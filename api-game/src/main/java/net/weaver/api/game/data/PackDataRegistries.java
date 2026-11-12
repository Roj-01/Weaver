package net.weaver.api.game.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.network.NetworkConnectedEvent;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadType;
import net.weaver.api.registry.DataPackSnapshot;
import net.weaver.api.registry.DataRegistryManager;

/**
 * Drives Weaver's data registries ({@link DataRegistryManager}) from the
 * server's real data packs. Every time the server loads data packs (startup,
 * {@code /reload}, a data pack enabled or disabled) the {@link #LISTENER}
 * reads {@code data/<ns>/weaver/registry} and {@code data/<ns>/weaver/tags}
 * from each active pack, lowest priority first, and applies them with
 * {@link DataRegistryManager#reloadAll}: pack values are replaced in their
 * existing handles, and frozen registries are refused and reported.
 *
 * <p>The first load opens the registration phase once
 * ({@code DataRegistryRegisterEvent} for every registry, so mods can add
 * programmatic entries) before applying the packs. Registries are not frozen
 * by this integration, so {@code /reload} keeps working; a mod that wants
 * fixed values freezes its own registry.</p>
 *
 * <p>After each load and when a player joins, the server sends the applied
 * pack layers to every Weaver client on {@code weaver:data_registries}; the
 * client applies them the same way. A client in the same process as the
 * server (single player) already shares the registries and skips them.</p>
 */
public final class PackDataRegistries {
    /** The reload listener the server appends to its data pack reload. */
    public static final PreparableReloadListener LISTENER = new Listener();

    public static final PayloadType<Sync> TYPE = PayloadType.of("weaver", "data_registries", Sync.CODEC);

    private static final int MAX_TEXT = 1 << 20;
    // Keeps explicit JSON nulls, which a codec may read as "unset".
    private static final Gson GSON = new com.google.gson.GsonBuilder().serializeNulls().create();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean REGISTRATION_DONE = new AtomicBoolean();
    private static volatile Sync current;
    private static volatile UUID appliedGeneration;
    private static volatile Consumer<String> log = System.out::println;

    private PackDataRegistries() {
    }

    /** The pack layers the server applied last, with their generation id. */
    public record Sync(UUID generation, List<DataPackSnapshot> snapshots) {
        public Sync {
            snapshots = List.copyOf(snapshots);
        }

        public static final PayloadCodec<Sync> CODEC = new PayloadCodec<>() {
            @Override
            public void encode(Sync value, PayloadBuffer out) {
                out.writeUuid(value.generation());
                out.writeVarInt(value.snapshots().size());
                for (DataPackSnapshot snapshot : value.snapshots()) {
                    out.writeString(snapshot.packId(), MAX_TEXT);
                    out.writeInt(snapshot.priority());
                    out.writeVarInt(snapshot.entries().size());
                    for (Map.Entry<String, Map<String, Map<String, Object>>> registry : snapshot.entries().entrySet()) {
                        out.writeString(registry.getKey(), MAX_TEXT);
                        out.writeVarInt(registry.getValue().size());
                        for (Map.Entry<String, Map<String, Object>> entry : registry.getValue().entrySet()) {
                            out.writeString(entry.getKey(), MAX_TEXT);
                            out.writeString(GSON.toJson(entry.getValue()), MAX_TEXT);
                        }
                    }
                    out.writeVarInt(snapshot.tags().size());
                    for (Map.Entry<String, Map<String, DataPackSnapshot.TagDeclaration>> registry : snapshot.tags().entrySet()) {
                        out.writeString(registry.getKey(), MAX_TEXT);
                        out.writeVarInt(registry.getValue().size());
                        for (Map.Entry<String, DataPackSnapshot.TagDeclaration> tag : registry.getValue().entrySet()) {
                            out.writeString(tag.getKey(), MAX_TEXT);
                            DataPackSnapshot.TagDeclaration declaration = tag.getValue();
                            out.writeInt(declaration.priority());
                            out.writeBoolean(declaration.replace());
                            out.writeVarInt(declaration.values().size());
                            for (DataPackSnapshot.TagValue tagValue : declaration.values()) {
                                out.writeString(tagValue.id(), MAX_TEXT);
                                out.writeBoolean(tagValue.tagReference());
                                out.writeBoolean(tagValue.required());
                            }
                        }
                    }
                }
            }

            @Override
            public Sync decode(PayloadBuffer in) {
                UUID generation = in.readUuid();
                int snapshotCount = count(in);
                List<DataPackSnapshot> snapshots = new ArrayList<>(snapshotCount);
                for (int i = 0; i < snapshotCount; i++) {
                    DataPackSnapshot.Builder builder = DataPackSnapshot.builder(in.readString(MAX_TEXT), in.readInt());
                    int registries = count(in);
                    for (int r = 0; r < registries; r++) {
                        String registry = in.readString(MAX_TEXT);
                        int entries = count(in);
                        for (int e = 0; e < entries; e++) {
                            String path = in.readString(MAX_TEXT);
                            JsonElement json = JsonParser.parseString(in.readString(MAX_TEXT));
                            builder.entry(registry, path, DataPackFiles.toMap(json.getAsJsonObject()));
                        }
                    }
                    int tagRegistries = count(in);
                    for (int r = 0; r < tagRegistries; r++) {
                        String registry = in.readString(MAX_TEXT);
                        int tags = count(in);
                        for (int t = 0; t < tags; t++) {
                            String tagId = in.readString(MAX_TEXT);
                            int priority = in.readInt();
                            boolean replace = in.readBoolean();
                            int valueCount = count(in);
                            List<DataPackSnapshot.TagValue> values = new ArrayList<>(valueCount);
                            for (int v = 0; v < valueCount; v++) {
                                values.add(new DataPackSnapshot.TagValue(in.readString(MAX_TEXT), in.readBoolean(),
                                        in.readBoolean()));
                            }
                            builder.tag(registry, tagId, new DataPackSnapshot.TagDeclaration(values, priority, replace));
                        }
                    }
                    snapshots.add(builder.build());
                }
                return new Sync(generation, snapshots);
            }
        };

        private static int count(PayloadBuffer in) {
            int count = in.readVarInt();
            if (count < 0 || count > 65_536) {
                throw new IllegalArgumentException("count out of range: " + count);
            }
            return count;
        }
    }

    /**
     * Registers the sync channel and the join-time sender. Called once when
     * the network bridge installs; loads no game state.
     */
    public static void install(Consumer<String> logger) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        log = logger;
        NetworkRegistry.global().registerClientbound("weaver", TYPE, NetworkPhase.PLAY, (sync, context) -> applyOnClient(sync));
        EventBus.GAME.addListener(NetworkConnectedEvent.class, event -> {
            Sync sync = current;
            if (event.side() == NetworkSide.SERVER && sync != null) {
                send(event.connection(), sync);
            }
        });
    }

    /** The layers the server applied last, or {@code null} before the first load. */
    public static Sync current() {
        return current;
    }

    /**
     * Reads the Weaver files of every pack in {@code manager}, lowest
     * priority first; problems are passed to {@code problems}.
     */
    public static List<DataPackSnapshot> read(ResourceManager manager, Consumer<String> problems) {
        List<DataPackSnapshot> snapshots = new ArrayList<>();
        List<PackResources> packs = manager.listPacks().toList();
        for (int priority = 0; priority < packs.size(); priority++) {
            PackResources pack = packs.get(priority);
            Map<String, String> files = new TreeMap<>();
            for (String namespace : pack.getNamespaces(PackType.SERVER_DATA)) {
                pack.listResources(PackType.SERVER_DATA, namespace, "weaver", (id, supplier) -> {
                    try (InputStream input = supplier.get()) {
                        byte[] bytes = input.readNBytes(DataPackFiles.MAX_FILE_BYTES + 1);
                        files.put(id.getNamespace() + "/" + id.getPath(), new String(bytes, StandardCharsets.UTF_8));
                    } catch (IOException failure) {
                        problems.accept(pack.packId() + "/data/" + id.getNamespace() + "/" + id.getPath() + ": " + failure);
                    }
                });
            }
            if (files.isEmpty()) {
                continue;
            }
            DataPackFiles.Result result = DataPackFiles.read(pack.packId(), priority, files);
            result.problems().forEach(problems);
            snapshots.add(result.snapshot());
        }
        return snapshots;
    }

    private static void applyOnServer(List<DataPackSnapshot> snapshots) {
        if (REGISTRATION_DONE.compareAndSet(false, true)) {
            DataRegistryManager.beginRegistration();
            DataRegistryManager.endRegistration();
        }
        DataRegistryManager.ReloadReport report = DataRegistryManager.reloadAll(snapshots);
        Sync sync = new Sync(UUID.randomUUID(), snapshots);
        appliedGeneration = sync.generation();
        current = sync;
        log.accept("[weaver] DATA REGISTRIES loaded packs=" + snapshots.size() + " registries="
                + report.openIds().size() + " frozen=" + report.frozenCount() + " problems=" + report.problems().size());
        report.problems().stream().limit(20).forEach(problem -> log.accept("[weaver] DATA REGISTRIES problem " + problem));
        for (NetworkConnection connection : net.weaver.api.game.network.GameNetworkBridge.connections()) {
            if (connection.side() == NetworkSide.SERVER && connection.isOpen()) {
                send(connection, sync);
            }
        }
    }

    private static void applyOnClient(Sync sync) {
        if (sync.generation().equals(appliedGeneration)) {
            log.accept("[weaver] DATA REGISTRIES sync skipped reason=same-process generation=" + sync.generation());
            return;
        }
        DataRegistryManager.ReloadReport report = DataRegistryManager.reloadAll(sync.snapshots());
        appliedGeneration = sync.generation();
        log.accept("[weaver] DATA REGISTRIES synced packs=" + sync.snapshots().size() + " registries="
                + report.openIds().size() + " problems=" + report.problems().size());
    }

    private static void send(NetworkConnection connection, Sync sync) {
        try {
            connection.send(TYPE, sync);
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            log.accept("[weaver] DATA REGISTRIES sync FAILED reason=" + failure);
        }
    }

    private static final class Listener extends SimplePreparableReloadListener<List<DataPackSnapshot>> {
        @Override
        protected List<DataPackSnapshot> prepare(ResourceManager manager, ProfilerFiller profiler) {
            return read(manager, problem -> log.accept("[weaver] DATA REGISTRIES problem " + problem));
        }

        @Override
        protected void apply(List<DataPackSnapshot> snapshots, ResourceManager manager, ProfilerFiller profiler) {
            applyOnServer(snapshots);
        }
    }
}
