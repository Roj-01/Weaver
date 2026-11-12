package net.weaver.api.game.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.network.NetworkConnectedEvent;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadType;

/**
 * Data maps: register a {@link DataMapType}, and packs give its values.
 *
 * <p>A file at {@link DataMapType#file()}:</p>
 *
 * <pre>{@code
 * {
 *   "replace": false,
 *   "values": {
 *     "#minecraft:logs": 4,
 *     "minecraft:diamond": 200
 *   },
 *   "remove": ["minecraft:pale_oak_log"]
 * }
 * }</pre>
 *
 * <p>Every pack's copy of the file is read, lowest pack first. A file with
 * {@code "replace": true} drops what lower packs gave. {@code values} keys are
 * entry ids or {@code #tags}; within a file and across files, later keys win.
 * {@code remove} takes ids or tags and drops their values. An entry id the
 * registry does not have, or a value the codec rejects, is skipped with a
 * {@code [weaver] DATA MAP} line; the rest of the file still loads.</p>
 *
 * <p>Values are read on every data pack load, after tags are bound, so they
 * follow {@code /reload}. Synced maps are sent to clients when they join and
 * after each reload; on a client the values are those of the server it is on.
 * Register types during mod setup.</p>
 */
public final class DataMaps {
    /** The clientbound channel for synced maps. */
    public static final PayloadType<Sync> TYPE = PayloadType.of("weaver", "data_maps", Sync.CODEC);

    private static final int MAX_TEXT = 1 << 20;
    private static final Map<Identifier, DataMapType<?, ?>> TYPES = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile Map<Identifier, Map<ResourceKey<?>, Object>> current = Map.of();
    private static volatile Sync lastSync;
    private static volatile UUID appliedGeneration;
    private static volatile Consumer<String> log = System.out::println;

    private DataMaps() {
    }

    /**
     * Registers {@code type} and returns it.
     *
     * @throws IllegalArgumentException when another type has the same id
     */
    public static <R, T> DataMapType<R, T> register(DataMapType<R, T> type) {
        synchronized (TYPES) {
            DataMapType<?, ?> existing = TYPES.putIfAbsent(type.id(), type);
            if (existing != null && existing != type) {
                throw new IllegalArgumentException("data map already registered: " + type.id());
            }
        }
        return type;
    }

    /** The registered types, in registration order. */
    public static List<DataMapType<?, ?>> types() {
        synchronized (TYPES) {
            return List.copyOf(TYPES.values());
        }
    }

    /** The value of {@code type} for {@code holder}, or {@code null}. */
    public static <R, T> T get(DataMapType<R, T> type, Holder<R> holder) {
        Optional<ResourceKey<R>> key = holder.unwrapKey();
        return key.isPresent() ? get(type, key.get()) : null;
    }

    /** The value of {@code type} for {@code key}, or {@code null}. */
    @SuppressWarnings("unchecked")
    public static <R, T> T get(DataMapType<R, T> type, ResourceKey<R> key) {
        Map<ResourceKey<?>, Object> values = current.get(type.id());
        return values == null ? null : (T) values.get(key);
    }

    /** Every value of {@code type}. */
    @SuppressWarnings("unchecked")
    public static <R, T> Map<ResourceKey<R>, T> values(DataMapType<R, T> type) {
        Map<ResourceKey<?>, Object> values = current.get(type.id());
        return values == null ? Map.of() : (Map<ResourceKey<R>, T>) (Map<?, ?>) values;
    }

    // ---- loading -------------------------------------------------------------------------

    /** Every pack's copy of every registered map's file, lowest pack first. */
    static Map<Identifier, List<JsonElement>> read(ResourceManager manager) {
        Map<Identifier, List<JsonElement>> files = new HashMap<>();
        for (DataMapType<?, ?> type : types()) {
            List<JsonElement> stack = new ArrayList<>();
            for (Resource resource : manager.getResourceStack(type.file())) {
                try {
                    stack.add(ModifierFiles.readJson(resource));
                } catch (IOException | RuntimeException failure) {
                    log.accept("[weaver] DATA MAP UNREADABLE map=" + type.id() + " pack=" + resource.sourcePackId()
                            + " reason=" + failure.getMessage());
                }
            }
            if (!stack.isEmpty()) {
                files.put(type.id(), stack);
            }
        }
        return files;
    }

    /** Resolves {@code files} against {@code lookup}, installs the result, and syncs clients. */
    static void load(Map<Identifier, List<JsonElement>> files, HolderLookup.Provider lookup) {
        DynamicOps<JsonElement> ops = lookup.createSerializationContext(JsonOps.INSTANCE);
        Map<Identifier, Map<ResourceKey<?>, Object>> loaded = new HashMap<>();
        int total = 0;
        for (DataMapType<?, ?> type : types()) {
            Map<ResourceKey<?>, Object> values = resolve(type, files.getOrDefault(type.id(), List.of()), lookup, ops);
            if (!values.isEmpty()) {
                loaded.put(type.id(), values);
                total += values.size();
            }
        }
        current = Map.copyOf(loaded);
        log.accept("[weaver] DATA MAPS loaded maps=" + loaded.size() + " values=" + total);
        Sync sync = new Sync(UUID.randomUUID(), encodeSynced(ops));
        appliedGeneration = sync.generation();
        lastSync = sync;
        for (NetworkConnection connection : net.weaver.api.game.network.GameNetworkBridge.connections()) {
            if (connection.side() == NetworkSide.SERVER && connection.isOpen()) {
                send(connection, sync);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <R, T> Map<ResourceKey<?>, Object> resolve(DataMapType<R, T> type, List<JsonElement> stack,
            HolderLookup.Provider lookup, DynamicOps<JsonElement> ops) {
        Map<ResourceKey<?>, Object> values = new LinkedHashMap<>();
        if (stack.isEmpty()) {
            return values;
        }
        Optional<? extends HolderLookup.RegistryLookup<R>> registry = lookup.lookup(type.registry());
        if (registry.isEmpty()) {
            log.accept("[weaver] DATA MAP SKIPPED map=" + type.id() + " reason=no registry " + type.registry().identifier());
            return values;
        }
        for (JsonElement file : stack) {
            if (!file.isJsonObject()) {
                problem(type, "file is not a JSON object");
                continue;
            }
            JsonObject object = file.getAsJsonObject();
            if (object.has("replace") && object.get("replace").getAsBoolean()) {
                values.clear();
            }
            if (object.has("values")) {
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("values").entrySet()) {
                    List<ResourceKey<R>> keys = keys(type, registry.get(), entry.getKey());
                    if (keys.isEmpty()) {
                        continue;
                    }
                    DataResult<T> parsed = type.codec().parse(ops, entry.getValue());
                    Optional<T> value = parsed.result();
                    if (value.isEmpty()) {
                        problem(type, entry.getKey() + ": " + parsed.error().map(error -> error.message()).orElse("invalid"));
                        continue;
                    }
                    for (ResourceKey<R> key : keys) {
                        values.remove(key);
                        values.put(key, value.get());
                    }
                }
            }
            if (object.has("remove")) {
                JsonArray removals = object.getAsJsonArray("remove");
                for (JsonElement removal : removals) {
                    for (ResourceKey<R> key : keys(type, registry.get(), removal.getAsString())) {
                        values.remove(key);
                    }
                }
            }
        }
        return values;
    }

    private static <R> List<ResourceKey<R>> keys(DataMapType<R, ?> type, HolderLookup.RegistryLookup<R> registry,
            String reference) {
        List<ResourceKey<R>> keys = new ArrayList<>();
        try {
            if (reference.startsWith("#")) {
                Optional<HolderSet.Named<R>> tag = registry.get(TagKey.create(type.registry(),
                        Identifier.parse(reference.substring(1))));
                if (tag.isEmpty()) {
                    problem(type, "unknown tag " + reference);
                }
                tag.ifPresent(set -> set.forEach(holder -> holder.unwrapKey().ifPresent(keys::add)));
            } else {
                ResourceKey<R> key = ResourceKey.create(type.registry(), Identifier.parse(reference));
                if (registry.get(key).isPresent()) {
                    keys.add(key);
                } else {
                    problem(type, "unknown entry " + reference);
                }
            }
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            problem(type, "bad reference " + reference + ": " + failure.getMessage());
        }
        return keys;
    }

    private static void problem(DataMapType<?, ?> type, String message) {
        log.accept("[weaver] DATA MAP problem map=" + type.id() + " " + message);
    }

    // ---- sync ----------------------------------------------------------------------------

    /**
     * Synced map values: map id to a JSON object of entry id to value, with a
     * generation so a client in the same process as the server skips it.
     */
    public record Sync(UUID generation, Map<String, String> maps) {
        public Sync {
            maps = Map.copyOf(maps);
        }

        public static final PayloadCodec<Sync> CODEC = new PayloadCodec<>() {
            @Override
            public void encode(Sync value, PayloadBuffer out) {
                out.writeUuid(value.generation());
                out.writeVarInt(value.maps().size());
                for (Map.Entry<String, String> entry : new java.util.TreeMap<>(value.maps()).entrySet()) {
                    out.writeString(entry.getKey(), MAX_TEXT);
                    out.writeString(entry.getValue(), MAX_TEXT);
                }
            }

            @Override
            public Sync decode(PayloadBuffer in) {
                UUID generation = in.readUuid();
                int count = in.readVarInt();
                if (count < 0 || count > 4096) {
                    throw new IllegalArgumentException("map count out of range: " + count);
                }
                Map<String, String> maps = new HashMap<>();
                for (int i = 0; i < count; i++) {
                    maps.put(in.readString(MAX_TEXT), in.readString(MAX_TEXT));
                }
                return new Sync(generation, maps);
            }
        };
    }

    /** The sync the server built at the last load (sent to joining clients), or {@code null}. */
    public static Sync lastSync() {
        return lastSync;
    }

    /** Registers the sync channel and the join-time sender. Called once when the network bridge installs. */
    public static void install(Consumer<String> logger) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        log = logger;
        NetworkRegistry.global().registerClientbound("weaver", TYPE, NetworkPhase.PLAY,
                (sync, context) -> DataMapsClient.apply(sync));
        EventBus.GAME.addListener(NetworkConnectedEvent.class, event -> {
            Sync sync = lastSync;
            if (event.side() == NetworkSide.SERVER && sync != null && !sync.maps().isEmpty()) {
                send(event.connection(), sync);
            }
        });
    }

    private static Map<String, String> encodeSynced(DynamicOps<JsonElement> ops) {
        Map<String, String> maps = new HashMap<>();
        for (DataMapType<?, ?> type : types()) {
            if (type.isSynced()) {
                maps.put(type.id().toString(), encode(type, ops).toString());
            }
        }
        return maps;
    }

    private static <R, T> JsonObject encode(DataMapType<R, T> type, DynamicOps<JsonElement> ops) {
        JsonObject object = new JsonObject();
        for (Map.Entry<ResourceKey<R>, T> entry : values(type).entrySet()) {
            type.codec().encodeStart(ops, entry.getValue()).result()
                    .ifPresent(json -> object.add(entry.getKey().identifier().toString(), json));
        }
        return object;
    }

    /** Applies a sync on the client; {@code ops} reads values with the client's registries. */
    static void applyOnClient(Sync sync, DynamicOps<JsonElement> ops) {
        if (sync.generation().equals(appliedGeneration)) {
            log.accept("[weaver] DATA MAPS sync skipped reason=same-process generation=" + sync.generation());
            return;
        }
        Map<Identifier, Map<ResourceKey<?>, Object>> next = new HashMap<>();
        int total = 0;
        for (DataMapType<?, ?> type : types()) {
            String json = sync.maps().get(type.id().toString());
            if (json != null) {
                Map<ResourceKey<?>, Object> values = decode(type, JsonParser.parseString(json).getAsJsonObject(), ops);
                next.put(type.id(), values);
                total += values.size();
            }
        }
        current = Map.copyOf(next);
        appliedGeneration = sync.generation();
        log.accept("[weaver] DATA MAPS synced maps=" + next.size() + " values=" + total);
    }

    private static <R, T> Map<ResourceKey<?>, Object> decode(DataMapType<R, T> type, JsonObject object,
            DynamicOps<JsonElement> ops) {
        Map<ResourceKey<?>, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            type.codec().parse(ops, entry.getValue()).result().ifPresent(value ->
                    values.put(ResourceKey.create(type.registry(), Identifier.parse(entry.getKey())), value));
        }
        return values;
    }

    private static void send(NetworkConnection connection, Sync sync) {
        try {
            connection.send(TYPE, sync);
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            log.accept("[weaver] DATA MAPS sync FAILED reason=" + failure);
        }
    }
}
