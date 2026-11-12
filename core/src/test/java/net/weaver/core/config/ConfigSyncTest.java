package net.weaver.core.config;

import net.weaver.api.config.ConfigBuilder;
import net.weaver.api.config.ConfigDocument;
import net.weaver.api.config.ConfigException;
import net.weaver.api.config.ConfigManager;
import net.weaver.api.config.ConfigSpec;
import net.weaver.api.config.ConfigStore;
import net.weaver.api.config.ConfigSyncDocument;
import net.weaver.api.config.ConfigSyncPolicy;
import net.weaver.api.config.ConfigSyncResult;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.Side;
import net.weaver.api.event.events.config.ConfigSyncedEvent;
import net.weaver.api.event.events.network.NetworkConnectedEvent;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadContext;
import net.weaver.api.network.PayloadDirection;
import net.weaver.api.network.PayloadHandler;
import net.weaver.api.network.PayloadRegistration;
import net.weaver.api.network.NetworkException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSyncTest
{
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final NetworkPlayer PLAYER =
            new NetworkPlayer(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000003"),
                    "SyncCoreTest");

    private final List<String> openedIds = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();

    @BeforeEach
    void resetSync()
    {
        ConfigSync.resetForTests();
        logs.clear();
    }

    @AfterEach
    void tearDown()
    {
        ConfigSync.resetForTests();
        for (String modId : openedIds)
        {
            ConfigManager.unregister(modId);
        }
        openedIds.clear();
    }

    @Test
    void installIsIdempotentAndKeepsOneRegistration()
    {
        NetworkRegistry registry = new NetworkRegistry();
        EventBus bus = new EventBus("config-sync-install");

        assertTrue(ConfigSync.install(registry, bus, logs::add));
        assertEquals(1, registry.size());
        assertTrue(registry.find(PayloadDirection.CLIENTBOUND, NetworkPhase.PLAY,
                ConfigSync.TYPE.channel()).isPresent());

        assertFalse(ConfigSync.install(registry, bus, logs::add));
        assertEquals(1, registry.size());

        registry.clear();
        assertTrue(ConfigSync.install(registry, bus, logs::add));
        assertEquals(1, registry.size());

        ConfigSync.resetForTests();
        assertFalse(ConfigSync.install(registry, bus, logs::add));
        assertTrue(logs.stream().anyMatch(line ->
                line.contains("[weaver] CONFIG SYNC INSTALL SKIPPED")
                        && line.contains("channel-already-registered")));
    }

    @Test
    void serverSendsOnlyServerToClientManagersAndIsolatesFailures()
    {
        String syncedId = id();
        String noneId = id();
        String clientSideId = id();
        ConfigManager synced = open(syncedId, Side.SERVER, ConfigSyncPolicy.SERVER_TO_CLIENT,
                builder -> builder.intValue("sync.count", 3, 0, 10, "count"));
        synced.spec().value("sync.count", Integer.class).set(7);
        open(noneId, Side.SERVER, ConfigSyncPolicy.NONE,
                builder -> builder.intValue("sync.count", 1, 0, 10, "count"));
        open(clientSideId, Side.CLIENT, ConfigSyncPolicy.SERVER_TO_CLIENT,
                builder -> builder.intValue("sync.count", 1, 0, 10, "count"));

        NetworkRegistry registry = new NetworkRegistry();
        EventBus bus = new EventBus("config-sync-server");
        assertTrue(ConfigSync.install(registry, bus, logs::add));

        FakeConnection failing = new FakeConnection(NetworkSide.SERVER,
                document -> document.modId().equals(syncedId)
                        ? new NetworkException("synthetic send failure") : null);
        bus.post(new NetworkConnectedEvent(failing, List.of(NetworkModInfo.of("test", "1.0"))));

        assertTrue(logs.stream().anyMatch(line ->
                line.contains("[weaver] CONFIG SYNC SEND FAILED mod=" + syncedId)));
        assertTrue(logs.stream().anyMatch(line ->
                line.contains("[weaver] CONFIG SYNC SKIPPED mod=" + clientSideId)
                        && line.contains("manager-side-is-not-server")));
        assertFalse(logs.stream().anyMatch(line ->
                        line.contains("CONFIG SYNC") && line.contains("mod=" + noneId)),
                () -> "NONE manager should not be logged as a candidate: " + logs);

        FakeConnection connection = new FakeConnection(NetworkSide.SERVER, document -> null);
        bus.post(new NetworkConnectedEvent(connection, List.of()));
        assertEquals(1, connection.documents().size());
        ConfigSyncDocument document = connection.documents().get(0);
        assertEquals(syncedId, document.modId());
        assertEquals(Side.SERVER, document.side());
        assertEquals(1, document.schemaVersion());
        assertEquals(7, document.values().get("sync.count"));
        assertTrue(logs.stream().anyMatch(line ->
                line.contains("[weaver] CONFIG SYNC SENT mod=" + syncedId)));
    }

    @Test
    void noConnectedManagersSkipsWithoutFailing()
    {
        NetworkRegistry registry = new NetworkRegistry();
        EventBus bus = new EventBus("config-sync-empty");
        assertTrue(ConfigSync.install(registry, bus, logs::add));

        FakeConnection connection = new FakeConnection(NetworkSide.SERVER, document -> null);
        bus.post(new NetworkConnectedEvent(connection, List.of()));

        assertTrue(connection.documents().isEmpty());
        assertTrue(logs.stream().anyMatch(line ->
                line.contains("[weaver] CONFIG SYNC SKIPPED side=SERVER")
                        && line.contains("no-sync-managers")));

        FakeConnection closed = new FakeConnection(NetworkSide.SERVER, document -> null);
        closed.close();
        bus.post(new NetworkConnectedEvent(closed, List.of()));
        assertTrue(logs.stream().anyMatch(line ->
                line.contains("reason=connection-not-ready")));
    }

    @Test
    void clientHandlerAppliesOnceAndLogsRejectionsOnce()
    {
        String modId = id();
        EventBus bus = new EventBus("config-sync-client");
        List<ConfigSyncedEvent> events = new ArrayList<>();
        ListenerHandle eventHandle = bus.addListener(ConfigSyncedEvent.class, events::add);
        try
        {
            ConfigBuilder clientBuilder = ConfigBuilder.create(modId, Side.CLIENT)
                    .sync(ConfigSyncPolicy.SERVER_TO_CLIENT);
            clientBuilder.intValue("sync.count", 1, 0, 10, "count");
            clientBuilder.stringValue("sync.text", "default", "text");
            ConfigSpec spec = clientBuilder.build();
            ConfigManager manager = ConfigManager.open(spec, new FakeStore(Side.CLIENT), bus,
                    logs::add);
            NetworkRegistry registry = new NetworkRegistry();
            assertTrue(ConfigSync.install(registry, bus, logs::add));
            PayloadHandler<ConfigSyncDocument> handler = handler(registry);

            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("sync.count", 9);
            values.put("sync.text", "from-server");
            values.put("future.key", "ignored");
            FakeConnection connection = new FakeConnection(NetworkSide.CLIENT, document -> null);
            handler.handle(new ConfigSyncDocument(modId, Side.SERVER, 1, values),
                    new FakeContext(connection, NetworkSide.CLIENT));

            assertEquals(9, manager.value("sync.count").get());
            assertEquals("from-server", manager.value("sync.text").get());
            assertEquals(2, manager.syncResult().orElseThrow().appliedValues());
            assertEquals(1, manager.syncResult().orElseThrow().ignoredKeys());
            assertEquals(1, events.size());
            assertEquals(2, events.get(0).appliedValues());
            assertEquals(1, events.get(0).ignoredKeys());

            ConfigSyncDocument wrongSide = new ConfigSyncDocument(modId, Side.CLIENT, 1,
                    Map.of("sync.count", 5));
            assertDoesNotThrow(() -> handler.handle(wrongSide,
                    new FakeContext(connection, NetworkSide.CLIENT)));
            assertDoesNotThrow(() -> handler.handle(wrongSide,
                    new FakeContext(connection, NetworkSide.CLIENT)));
            assertEquals(1, countLogs("reason=document-side-is-not-server"));

            ConfigSyncDocument wrongSchema = new ConfigSyncDocument(modId, Side.SERVER, 2,
                    Map.of("sync.count", 5));
            assertDoesNotThrow(() -> handler.handle(wrongSchema,
                    new FakeContext(connection, NetworkSide.CLIENT)));
            assertDoesNotThrow(() -> handler.handle(wrongSchema,
                    new FakeContext(connection, NetworkSide.CLIENT)));
            assertEquals(1, countLogs("reason=schema-mismatch"));
            assertEquals(9, manager.value("sync.count").get());

            ConfigSyncDocument unknown = new ConfigSyncDocument("missing.sync.mod",
                    Side.SERVER, 1, Map.of());
            assertDoesNotThrow(() -> handler.handle(unknown,
                    new FakeContext(connection, NetworkSide.CLIENT)));
            assertEquals(1, countLogs("reason=no-config-manager"));

            assertDoesNotThrow(() -> handler.handle(null,
                    new FakeContext(connection, NetworkSide.CLIENT)));
            assertEquals(1, countLogs("reason=null-document"));
            assertEquals(1, events.size());
        }
        finally
        {
            eventHandle.unregister();
        }
    }

    @Test
    void sendToBuildsTheLoadedDocumentAndRejectsIneligibleManagers()
    {
        String modId = id();
        ConfigManager manager = open(modId, Side.SERVER, ConfigSyncPolicy.SERVER_TO_CLIENT,
                builder -> {
                    builder.intValue("sync.count", 2, 0, 10, "count");
                    builder.stringValue("sync.text", "value", "text");
                });
        manager.spec().value("sync.count", Integer.class).set(6);
        manager.spec().value("sync.text", String.class).set("sent");
        FakeConnection connection = new FakeConnection(NetworkSide.SERVER, document -> null);

        ConfigSyncDocument document = ConfigSync.sendTo(connection, manager);

        assertEquals(modId, document.modId());
        assertEquals(Side.SERVER, document.side());
        assertEquals(1, document.schemaVersion());
        assertEquals(6, document.values().get("sync.count"));
        assertEquals("sent", document.values().get("sync.text"));
        assertEquals(1, connection.documents().size());
        assertSame(document, connection.documents().get(0));

        String noneId = id();
        ConfigManager none = open(noneId, Side.SERVER, ConfigSyncPolicy.NONE,
                builder -> builder.intValue("sync.count", 1, 0, 10, "count"));
        assertThrows(ConfigException.class, () -> ConfigSync.sendTo(connection, none));

        FakeConnection clientConnection = new FakeConnection(NetworkSide.CLIENT,
                documentValue -> null);
        assertThrows(ConfigException.class,
                () -> ConfigSync.sendTo(clientConnection, manager));

        FakeConnection closed = new FakeConnection(NetworkSide.SERVER, documentValue -> null);
        closed.close();
        assertThrows(ConfigException.class, () -> ConfigSync.sendTo(closed, manager));
    }

    @Test
    void applyToRejectsNonServerDocuments()
    {
        String modId = id();
        ConfigManager manager = open(modId, Side.CLIENT, ConfigSyncPolicy.SERVER_TO_CLIENT,
                builder -> builder.stringValue("sync.text", "default", "text"));

        ConfigException failure = assertThrows(ConfigException.class,
                () -> ConfigSync.applyTo(manager,
                        new ConfigSyncDocument(modId, Side.CLIENT, 1,
                                Map.of("sync.text", "nope"))));
        assertTrue(failure.getMessage().contains("SERVER"));
        assertEquals("default", manager.value("sync.text").get());
    }

    private int countLogs(String fragment)
    {
        int count = 0;
        for (String line : logs)
        {
            if (line.contains(fragment))
            {
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static PayloadHandler<ConfigSyncDocument> handler(NetworkRegistry registry)
    {
        PayloadRegistration<?> registration = registry.find(PayloadDirection.CLIENTBOUND,
                NetworkPhase.PLAY, ConfigSync.TYPE.channel()).orElseThrow();
        return (PayloadHandler<ConfigSyncDocument>) registration.handler();
    }

    private String id()
    {
        String modId = "core.config.sync." + SEQUENCE.incrementAndGet();
        openedIds.add(modId);
        return modId;
    }

    private ConfigManager open(String modId, Side side, ConfigSyncPolicy policy,
                               java.util.function.Consumer<ConfigBuilder> declarations)
    {
        ConfigBuilder builder = ConfigBuilder.create(modId, side).sync(policy);
        declarations.accept(builder);
        ConfigSpec spec = builder.build();
        return ConfigManager.open(spec, new FakeStore(side), logs::add);
    }

    private static void assertDoesNotThrow(Runnable action)
    {
        action.run();
    }

    private static final class FakeConnection implements NetworkConnection
    {
        private final NetworkSide side;
        private final Function<ConfigSyncDocument, RuntimeException> failure;
        private final List<ConfigSyncDocument> documents = new ArrayList<>();
        private NetworkPhase phase = NetworkPhase.PLAY;
        private boolean open = true;

        private FakeConnection(NetworkSide side,
                               Function<ConfigSyncDocument, RuntimeException> failure)
        {
            this.side = side;
            this.failure = failure;
        }

        @Override
        public NetworkSide side()
        {
            return side;
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
        public <T> void send(net.weaver.api.network.PayloadType<T> type, T payload)
        {
            assertTrue(payload instanceof ConfigSyncDocument);
            ConfigSyncDocument document = (ConfigSyncDocument) payload;
            RuntimeException thrown = failure.apply(document);
            if (thrown != null)
            {
                throw thrown;
            }
            documents.add(document);
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

        private List<ConfigSyncDocument> documents()
        {
            return documents;
        }
    }

    private record FakeContext(NetworkConnection connection, NetworkSide side)
            implements PayloadContext
    {
        @Override
        public NetworkPhase phase()
        {
            return NetworkPhase.PLAY;
        }

        @Override
        public NetworkPlayer player()
        {
            return PLAYER;
        }

        @Override
        public <T> void reply(net.weaver.api.network.PayloadType<T> type, T payload)
        {
            throw new UnsupportedOperationException("reply not used");
        }
    }

    private static final class FakeStore implements ConfigStore
    {
        private final Side side;
        private ConfigDocument document;

        private FakeStore(Side side)
        {
            this.side = side;
        }

        @Override
        public Side side()
        {
            return side;
        }

        @Override
        public Path location()
        {
            return Path.of("virtual", "config", side.name().toLowerCase() + ".json");
        }

        @Override
        public synchronized ConfigDocument read()
        {
            return document;
        }

        @Override
        public synchronized void write(ConfigDocument document)
        {
            this.document = document;
        }

        @Override
        public synchronized Path backup(String reason)
        {
            return null;
        }
    }
}
