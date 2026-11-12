package net.weaver.configproof;

import net.weaver.api.ModContext;
import net.weaver.api.config.ConfigBuilder;
import net.weaver.api.config.ConfigData;
import net.weaver.api.config.ConfigDocument;
import net.weaver.api.config.ConfigLoadResult;
import net.weaver.api.config.ConfigManager;
import net.weaver.api.config.ConfigMigration;
import net.weaver.api.config.ConfigSpec;
import net.weaver.api.config.ConfigSyncDocument;
import net.weaver.api.config.ConfigSyncPolicy;
import net.weaver.api.config.ConfigSyncResult;
import net.weaver.api.config.ConfigValidationException;
import net.weaver.api.config.ConfigValue;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.Side;
import net.weaver.api.event.events.config.ConfigLoadedEvent;
import net.weaver.api.event.events.config.ConfigReloadedEvent;
import net.weaver.api.event.events.config.ConfigSyncedEvent;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadType;
import net.weaver.core.config.ConfigFiles;
import net.weaver.core.config.ConfigSync;
import net.weaver.core.config.JsonConfigStore;
import net.weaver.core.config.WeaverConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Standalone proof for the Weaver 2.2 configuration API and core store.
 *
 * <p>This class uses only {@code net.weaver.api}, {@code net.weaver.core.config},
 * and the JDK. It never touches a Minecraft or Weaver game class.</p>
 */
public final class ConfigProof
{
    private static final AtomicInteger CHECKS = new AtomicInteger();

    enum Mode
    {
        NORMAL,
        FAST
    }

    private ConfigProof()
    {
    }

    public static void main(String[] args)
    {
        try
        {
            run();
            System.out.println("CONFIG PROOF RESULT ok=true checks=" + CHECKS.get());
        }
        catch (Throwable failure)
        {
            String reason = failure.getMessage();
            if (reason == null || reason.isBlank())
            {
                reason = failure.getClass().getName();
            }
            reason = reason.replace('\n', ' ').replace('\r', ' ');
            System.out.println("CONFIG PROOF RESULT ok=false reason=" + reason);
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static void run() throws IOException
    {
        Path proofDir = Path.of("").toAbsolutePath();
        Path work = proofDir.resolve("out").resolve("work");
        deleteRecursively(work);
        Files.createDirectories(work);

        String modId = "config-proof";
        Path configDir = work.resolve(modId);
        EventBus bus = new EventBus("config-proof-bus");
        List<String> logs = new ArrayList<>();
        ModContext context = new ProofContext(modId, "client", configDir,
                configDir.resolve("data"), bus, logs);

        Path clientFile = configDir.resolve("client.json");
        JsonConfigStore seedStore = new JsonConfigStore(clientFile, Side.CLIENT);
        LinkedHashMap<String, Object> seedValues = new LinkedHashMap<>();
        seedValues.put("general.message", "hello-from-v1");
        seedValues.put("general.count", 7);
        seedValues.put("general.mode", "FAST");
        seedValues.put("general.tags", List.of("red", "blue"));
        seedStore.write(new ConfigDocument(1, seedValues));
        check(Files.exists(clientFile), "v1 config file was seeded");

        ConfigSpec spec = proofSpec(modId);
        AtomicInteger eventCount = new AtomicInteger();
        List<ConfigLoadedEvent> events = new ArrayList<>();
        ListenerHandle handle = bus.addListener(ConfigLoadedEvent.class, event -> {
            events.add(event);
            eventCount.incrementAndGet();
        });
        ConfigManager manager;
        try
        {
            manager = WeaverConfig.open(context, spec);
        }
        finally
        {
            handle.unregister();
        }

        check(eventCount.get() == 1, "expected exactly one ConfigLoadedEvent but got "
                + eventCount.get());
        ConfigLoadedEvent event = events.get(0);
        check(modId.equals(event.modId()), "event mod id mismatch: " + event.modId());
        check(event.side() == Side.CLIENT, "event side mismatch: " + event.side());
        check(event.schemaVersion() == 2, "event schema mismatch: " + event.schemaVersion());
        check(event.logicalSide() == LogicalSide.CLIENT,
                "event logical side mismatch: " + event.logicalSide());
        check(event.problems().isEmpty(), "unexpected event problems: " + event.problems());
        System.out.println("CONFIG PROOF EVENT count=1 mod=" + event.modId());

        ConfigLoadResult loaded = manager.result();
        check(loaded.migrated(), "migration was not reported");
        check(loaded.fileVersion() == 1, "file version mismatch: " + loaded.fileVersion());
        check(loaded.appliedVersion() == 2,
                "applied version mismatch: " + loaded.appliedVersion());
        check(loaded.problems().isEmpty(), "unexpected load problems: " + loaded.problems());
        String greeting = (String) manager.value("general.greeting").get();
        check("hello-from-v1".equals(greeting), "renamed value mismatch: " + greeting);
        System.out.println("CONFIG PROOF MIGRATED from=1 to=2 value=" + greeting);
        System.out.println("CONFIG PROOF LOADED schema=" + loaded.appliedVersion()
                + " applied=" + loaded.appliedValues()
                + " defaults=" + loaded.defaultedValues());

        List<ConfigReloadedEvent> reloadEvents = new ArrayList<>();
        ListenerHandle reloadHandle = bus.addListener(ConfigReloadedEvent.class,
                reloadEvents::add);
        ConfigLoadResult reloaded;
        try
        {
            LinkedHashMap<String, Object> changed = new LinkedHashMap<>();
            changed.put("general.greeting", "hello-from-reload");
            changed.put("general.count", 9);
            changed.put("general.mode", "FAST");
            changed.put("general.tags", List.of("green"));
            new JsonConfigStore(clientFile, Side.CLIENT)
                    .write(new ConfigDocument(2, changed));
            reloaded = manager.reload();
        }
        finally
        {
            reloadHandle.unregister();
        }
        check(manager.generation() == 2, "reload generation mismatch: "
                + manager.generation());
        check(reloadEvents.size() == 1, "expected exactly one ConfigReloadedEvent but got "
                + reloadEvents.size());
        ConfigReloadedEvent reloadEvent = reloadEvents.get(0);
        check(modId.equals(reloadEvent.modId()), "reload event mod id mismatch: "
                + reloadEvent.modId());
        check(reloadEvent.generation() == 2, "reload event generation mismatch: "
                + reloadEvent.generation());
        check(reloadEvent.result() == reloaded, "reload event did not expose the new result");
        check(reloadEvent.logicalSide() == LogicalSide.CLIENT,
                "reload event logical side mismatch: " + reloadEvent.logicalSide());
        check("hello-from-reload".equals(manager.value("general.greeting").get()),
                "reloaded greeting mismatch: " + manager.value("general.greeting").get());
        check(((Number) manager.value("general.count").get()).intValue() == 9,
                "reloaded count mismatch: " + manager.value("general.count").get());
        System.out.println("CONFIG PROOF RELOADED generation=" + reloadEvent.generation()
                + " value=" + manager.value("general.greeting").get());

        ConfigValue<Integer> count = manager.spec().value("general.count", Integer.class);
        int previous = count.get();
        try
        {
            count.set(999);
            check(false, "out-of-range value 999 was accepted");
        }
        catch (ConfigValidationException expected)
        {
            check(count.get() == previous, "previous value changed after rejected set");
            check("general.count".equals(expected.key()),
                    "validation key mismatch: " + expected.key());
            check(expected.modId().equals(modId), "validation mod id mismatch: "
                    + expected.modId());
            System.out.println("CONFIG PROOF INVALID key=" + expected.key()
                    + " message=" + expected.getMessage());
        }
        count.set(4);
        check(count.get() == 4, "valid set did not publish: " + count.get());
        manager.spec().value("general.tags", List.class).set(List.of("red", "blue"));

        manager.spec().value("general.greeting", String.class).set("hello-from-proof");
        manager.save();
        String savedText = Files.readString(clientFile, StandardCharsets.UTF_8);
        check(savedText.contains("\"schemaVersion\": 2"),
                "saved file does not declare schemaVersion 2");
        check(savedText.contains("\"general.count\": 4"),
                "saved file does not contain the new count");
        check(savedText.contains("hello-from-proof"),
                "saved file does not contain the new greeting");
        check(savedText.contains("FAST"), "saved file does not contain the enum name");
        check(savedText.contains("red") && savedText.contains("blue"),
                "saved file does not contain the list values");
        System.out.println("CONFIG PROOF SAVED file=" + clientFile);

        ConfigManager.unregister(modId);
        ConfigSpec freshSpec = proofSpec(modId);
        ConfigManager reopened = WeaverConfig.open(context, freshSpec);
        check(((Number) reopened.value("general.count").get()).intValue() == 4,
                "round-trip count mismatch: " + reopened.value("general.count").get());
        check(reopened.value("general.mode").get() == Mode.FAST,
                "round-trip enum mismatch: " + reopened.value("general.mode").get());
        check(List.of("red", "blue").equals(reopened.value("general.tags").get()),
                "round-trip list mismatch: " + reopened.value("general.tags").get());
        check("hello-from-proof".equals(reopened.value("general.greeting").get()),
                "round-trip greeting mismatch: " + reopened.value("general.greeting").get());
        check(reopened.result().appliedValues() == 4,
                "round-trip applied count mismatch: " + reopened.result().appliedValues());
        System.out.println("CONFIG PROOF ROUNDTRIP value="
                + reopened.value("general.count").get());

        String corruptId = "config-proof-corrupt";
        Path corruptDir = work.resolve(corruptId);
        Files.createDirectories(corruptDir);
        Path corruptFile = corruptDir.resolve("client.json");
        String garbage = "{ this is not valid json";
        Files.writeString(corruptFile, garbage, StandardCharsets.UTF_8);
        List<String> corruptLogs = new ArrayList<>();
        ModContext corruptContext = new ProofContext(corruptId, "client", corruptDir,
                corruptDir.resolve("data"), new EventBus("config-proof-corrupt-bus"),
                corruptLogs);
        ConfigSpec corruptConfigSpec = corruptSpec(corruptId);
        ConfigManager corruptManager = WeaverConfig.open(corruptContext, corruptConfigSpec);
        ConfigLoadResult corruptResult = corruptManager.result();
        check(corruptResult.corrupt(), "corrupt file was not reported as corrupt");
        check(!corruptResult.problems().isEmpty(), "corrupt file produced no problems");
        check(corruptResult.backup() != null, "corrupt file produced no backup");
        check(Files.exists(corruptResult.backup()),
                "backup file does not exist: " + corruptResult.backup());
        check(garbage.equals(Files.readString(corruptResult.backup(),
                        StandardCharsets.UTF_8)),
                "backup does not preserve the original bytes");
        check("fallback".equals(corruptManager.value("general.value").get()),
                "corrupt file did not fall back to the default");
        System.out.println("CONFIG PROOF CORRUPT backup=" + corruptResult.backup()
                + " defaults=" + corruptResult.defaultedValues());

        Path sideDir = configDir;
        check(ConfigFiles.forMod(sideDir, Side.SERVER).location().toString()
                        .endsWith("server.json"),
                "server side path does not end with server.json");
        check(!Files.exists(sideDir.resolve("server.json")),
                "client manager created a server.json file");
        System.out.println("CONFIG PROOF SIDE file=client.json server-file-absent=true");

        check(ConfigManager.get(modId) == reopened,
                "ConfigManager.get did not return the reopened manager");
        System.out.println("CONFIG PROOF MANAGER identity=ok mod=" + modId);

        runSyncProof(work);

        ConfigManager.unregister(modId);
        ConfigManager.unregister(corruptId);
    }

    private static void runSyncProof(Path work) throws IOException
    {
        String modId = "config-proof-sync";
        Path syncDir = work.resolve(modId);
        Files.createDirectories(syncDir);
        List<String> serverLogs = new ArrayList<>();
        ModContext serverContext = new ProofContext(modId, "server", syncDir,
                syncDir.resolve("data"), new EventBus("config-proof-sync-server"), serverLogs);
        ConfigBuilder serverBuilder = ConfigBuilder.create(modId, Side.SERVER)
                .schemaVersion(1)
                .description("sync proof")
                .sync(ConfigSyncPolicy.SERVER_TO_CLIENT);
        serverBuilder.intValue("sync.count", 1, 0, 32, "count");
        serverBuilder.stringValue("sync.text", "default", 1, 64, "text");
        ConfigSpec serverSpec = serverBuilder.build();
        ConfigManager server = WeaverConfig.open(serverContext, serverSpec);
        server.spec().value("sync.count", Integer.class).set(17);
        server.spec().value("sync.text", String.class).set("from-server");

        SyncConnection connection = new SyncConnection();
        ConfigSyncDocument sent = ConfigSync.sendTo(connection, server);
        check(sent.modId().equals(modId), "sync document mod id mismatch: " + sent.modId());
        check(sent.side() == Side.SERVER, "sync document side mismatch: " + sent.side());
        check(sent.schemaVersion() == 1,
                "sync document schema mismatch: " + sent.schemaVersion());
        check(sent.values().size() == 2,
                "sync document key count mismatch: " + sent.values().size());
        check(connection.sends() == 1, "sync send count mismatch: " + connection.sends());
        ConfigManager.unregister(modId);

        EventBus clientBus = new EventBus("config-proof-sync-client");
        List<ConfigSyncedEvent> syncedEvents = new ArrayList<>();
        ListenerHandle syncHandle = clientBus.addListener(ConfigSyncedEvent.class,
                syncedEvents::add);
        List<String> clientLogs = new ArrayList<>();
        ModContext clientContext = new ProofContext(modId, "client", syncDir,
                syncDir.resolve("data"), clientBus, clientLogs);
        ConfigBuilder clientBuilder = ConfigBuilder.create(modId, Side.CLIENT)
                .schemaVersion(1)
                .description("sync proof")
                .sync(ConfigSyncPolicy.SERVER_TO_CLIENT);
        clientBuilder.intValue("sync.count", 1, 0, 32, "count");
        clientBuilder.stringValue("sync.text", "default", 1, 64, "text");
        ConfigSpec clientSpec = clientBuilder.build();
        try
        {
            ConfigManager client = WeaverConfig.open(clientContext, clientSpec);
            ConfigSyncDocument decoded = ConfigSync.TYPE.codec().decode(connection.bytes());
            check(decoded.modId().equals(modId),
                    "decoded sync mod id mismatch: " + decoded.modId());
            ConfigSyncResult result = ConfigSync.applyTo(client, decoded);
            check(Integer.valueOf(17).equals(client.value("sync.count").get()),
                    "synced count mismatch: " + client.value("sync.count").get());
            check("from-server".equals(client.value("sync.text").get()),
                    "synced text mismatch: " + client.value("sync.text").get());
            check(result.appliedValues() == 2,
                    "synced applied count mismatch: " + result.appliedValues());
            check(result.defaultedValues() == 0,
                    "synced defaulted count mismatch: " + result.defaultedValues());
            check(!Files.exists(syncDir.resolve("client.json")),
                    "sync wrote the client configuration file");
            check(syncedEvents.size() == 1,
                    "expected one ConfigSyncedEvent but got " + syncedEvents.size());
            ConfigSyncedEvent synced = syncedEvents.get(0);
            check(synced.logicalSide() == LogicalSide.CLIENT,
                    "synced logical side mismatch: " + synced.logicalSide());
            check(synced.schemaVersion() == 1,
                    "synced event schema mismatch: " + synced.schemaVersion());
            check(synced.appliedValues() == 2,
                    "synced event applied mismatch: " + synced.appliedValues());
            System.out.println("CONFIG PROOF SYNC ok=true mod=" + modId
                    + " applied=" + result.appliedValues()
                    + " logicalSide=" + synced.logicalSide()
                    + " fileWritten=false");
        }
        finally
        {
            syncHandle.unregister();
            ConfigManager.unregister(modId);
        }
    }

    private static final class SyncConnection implements NetworkConnection
    {
        private static final NetworkPlayer PLAYER =
                new NetworkPlayer(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000004"),
                        "ConfigProofSync");

        private final List<ConfigSyncDocument> documents = new ArrayList<>();
        private NetworkPhase phase = NetworkPhase.PLAY;
        private boolean open = true;

        @Override
        public NetworkSide side()
        {
            return NetworkSide.SERVER;
        }

        @Override
        public NetworkPhase phase()
        {
            return phase;
        }

        @Override
        public NetworkPlayer player()
        {
            return PLAYER;
        }

        @Override
        public boolean isOpen()
        {
            return open;
        }

        @Override
        public <T> void send(PayloadType<T> type, T payload)
        {
            documents.add((ConfigSyncDocument) payload);
        }

        @Override
        public void disconnect(String reason)
        {
            open = false;
        }

        @Override
        public void close()
        {
            open = false;
        }

        private int sends()
        {
            return documents.size();
        }

        private byte[] bytes()
        {
            return ConfigSync.TYPE.codec().encode(documents.get(0));
        }
    }

    private static ConfigSpec proofSpec(String modId)
    {
        ConfigBuilder builder = ConfigBuilder.create(modId, Side.CLIENT)
                .schemaVersion(2)
                .description("Config 2.2 standalone proof");
        builder.migration(migration(1,
                data -> data.rename("general.message", "general.greeting")));
        builder.intValue("general.count", 2, 0, 16, "bounded integer");
        builder.stringValue("general.greeting", "default", 1, 64, "greeting text");
        builder.enumValue("general.mode", Mode.class, Mode.NORMAL, "mode");
        builder.stringListValue("general.tags", List.of("default"), 0, 8, "tags");
        return builder.build();
    }

    private static ConfigSpec corruptSpec(String modId)
    {
        ConfigBuilder builder = ConfigBuilder.create(modId, Side.CLIENT)
                .schemaVersion(1)
                .description("corrupt proof");
        builder.stringValue("general.value", "fallback", "value");
        return builder.build();
    }

    private static ConfigMigration migration(int fromVersion,
                                             java.util.function.Consumer<ConfigData> action)
    {
        return new ConfigMigration()
        {
            @Override
            public int fromVersion()
            {
                return fromVersion;
            }

            @Override
            public void migrate(ConfigData data)
            {
                action.accept(data);
            }
        };
    }

    private static void check(boolean condition, String message)
    {
        CHECKS.incrementAndGet();
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    private static void deleteRecursively(Path directory) throws IOException
    {
        if (Files.notExists(directory))
        {
            return;
        }
        try (Stream<Path> entries = Files.walk(directory))
        {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList())
            {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static final class ProofContext implements ModContext
    {
        private final String id;
        private final String side;
        private final Path configDir;
        private final Path dataDir;
        private final EventBus bus;
        private final List<String> logs;

        private ProofContext(String id, String side, Path configDir, Path dataDir,
                             EventBus bus, List<String> logs)
        {
            this.id = id;
            this.side = side;
            this.configDir = configDir;
            this.dataDir = dataDir;
            this.bus = bus;
            this.logs = logs;
        }

        @Override
        public String id()
        {
            return id;
        }

        @Override
        public String side()
        {
            return side;
        }

        @Override
        public Path configDir()
        {
            return configDir;
        }

        @Override
        public Path dataDir()
        {
            return dataDir;
        }

        @Override
        public void log(String message)
        {
            logs.add(message);
            System.out.println("[config-proof] " + message);
        }

        @Override
        public EventBus modEvents()
        {
            return bus;
        }
    }
}
