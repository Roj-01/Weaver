package net.weaver.api.config;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.Side;
import net.weaver.api.event.events.config.ConfigSyncedEvent;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerSyncTest
{
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final PayloadType<ConfigSyncDocument> TYPE =
            PayloadType.of("weaver", "config/sync", ConfigSyncDocument.CODEC);

    private final List<String> openedIds = new ArrayList<>();

    enum Mode
    {
        SLOW,
        NORMAL,
        FAST
    }

    @AfterEach
    void tearDown()
    {
        for (String modId : openedIds)
        {
            ConfigManager.unregister(modId);
        }
        openedIds.clear();
    }

    @Test
    void serverDocumentTravelsOverAFakeConnectionAndAppliesReadOnly()
    {
        String modId = id();
        ConfigSpec serverSpec = spec(modId, Side.SERVER, 2, builder -> {
            builder.sync(ConfigSyncPolicy.SERVER_TO_CLIENT);
            builder.intValue("sync.count", 1, 0, 20, "count");
            builder.stringValue("sync.text", "default", "text");
            builder.enumValue("sync.mode", Mode.class, Mode.NORMAL, "mode");
            builder.stringListValue("sync.tags", List.of("default"), "tags");
        });
        FakeStore serverStore = store(Side.SERVER);
        ConfigManager server = ConfigManager.open(serverSpec, serverStore, message ->
        {
        });
        server.spec().value("sync.count", Integer.class).set(11);
        server.spec().value("sync.text", String.class).set("from-server");
        server.spec().value("sync.mode", Mode.class).set(Mode.FAST);
        server.spec().value("sync.tags", List.class).set(List.of("red", "blue"));

        FakeConnection connection = new FakeConnection(NetworkSide.SERVER);
        ConfigSyncDocument sent = ConfigSyncDocument.from(modId, Side.SERVER,
                server.document());
        connection.send(TYPE, sent);
        ConfigManager.unregister(modId);

        ConfigSpec clientSpec = spec(modId, Side.CLIENT, 2, builder -> {
            builder.sync(ConfigSyncPolicy.SERVER_TO_CLIENT);
            builder.intValue("sync.count", 1, 0, 20, "count");
            builder.stringValue("sync.text", "default", "text");
            builder.enumValue("sync.mode", Mode.class, Mode.NORMAL, "mode");
            builder.stringListValue("sync.tags", List.of("default"), "tags");
        });
        FakeStore clientStore = store(Side.CLIENT);
        EventBus bus = new EventBus("config-sync-client-" + modId);
        List<ConfigSyncedEvent> events = new ArrayList<>();
        ListenerHandle handle = bus.addListener(ConfigSyncedEvent.class, events::add);
        List<String> logs = new ArrayList<>();
        try
        {
            ConfigManager client = ConfigManager.open(clientSpec, clientStore, bus, logs::add);
            client.spec().value("sync.text", String.class).set("local-unsaved");

            ConfigSyncDocument received = TYPE.codec().decode(connection.sentBytes());
            assertEquals(modId, received.modId());
            assertEquals(Side.SERVER, received.side());
            assertEquals(2, received.schemaVersion());
            assertEquals(4, received.values().size());

            ConfigSyncResult result = net.weaver.core.config.ConfigSync.apply(client,
                    received);

            assertEquals(2, result.schemaVersion());
            assertEquals(4, result.appliedValues());
            assertEquals(0, result.defaultedValues());
            assertEquals(0, result.ignoredKeys());
            assertTrue(result.problems().isEmpty());
            assertEquals(11, client.value("sync.count").get());
            assertEquals("from-server", client.value("sync.text").get());
            assertEquals(Mode.FAST, client.value("sync.mode").get());
            assertEquals(List.of("red", "blue"), client.value("sync.tags").get());
            assertEquals(0, clientStore.writes);
            assertEquals(1, client.generation());
            assertSame(result, client.syncResult().orElseThrow());

            assertEquals(1, events.size());
            ConfigSyncedEvent event = events.get(0);
            assertEquals(modId, event.modId());
            assertEquals(Side.CLIENT, event.side());
            assertEquals(LogicalSide.CLIENT, event.logicalSide());
            assertEquals(2, event.schemaVersion());
            assertEquals(4, event.appliedValues());
            assertEquals(0, event.defaultedValues());
            assertEquals(0, event.ignoredKeys());
            assertSame(clientSpec, event.spec());
            assertSame(result, event.result());
            assertTrue(logs.stream().anyMatch(line ->
                    line.contains("[weaver] CONFIG SYNC APPLIED")
                            && line.contains("mod=" + modId)));
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void invalidAndMissingValuesFallBackToDefaultsAndUnknownKeysAreIgnored()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 3, builder -> {
            builder.sync(ConfigSyncPolicy.SERVER_TO_CLIENT);
            builder.intValue("sync.count", 5, 0, 10, "count");
            builder.stringValue("sync.text", "default", "text");
            builder.stringValue("sync.absent", "absent-default", "absent");
        });
        FakeStore store = store(Side.CLIENT);
        List<String> logs = new ArrayList<>();
        EventBus bus = new EventBus("config-sync-invalid-" + modId);
        List<ConfigSyncedEvent> events = new ArrayList<>();
        ListenerHandle handle = bus.addListener(ConfigSyncedEvent.class, events::add);
        try
        {
            ConfigManager client = ConfigManager.open(spec, store, bus, logs::add);
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("sync.count", "not-a-number");
            values.put("sync.text", "from-server");
            values.put("future.key", Map.of("ignored", true));

            ConfigSyncResult result = net.weaver.core.config.ConfigSync.apply(client,
                    new ConfigSyncDocument(modId, Side.SERVER, 3, values));

            assertEquals(1, result.appliedValues());
            assertEquals(2, result.defaultedValues());
            assertEquals(1, result.ignoredKeys());
            assertEquals(1, result.problems().size());
            assertEquals("sync.count", result.problems().get(0).key());
            assertEquals(5, client.value("sync.count").get());
            assertEquals("from-server", client.value("sync.text").get());
            assertEquals("absent-default", client.value("sync.absent").get());
            assertEquals(0, store.writes);
            assertTrue(logs.stream().anyMatch(line ->
                    line.contains("[weaver] CONFIG SYNC INVALID")
                            && line.contains("key=sync.count")),
                    () -> "missing CONFIG SYNC INVALID log: " + logs);
            assertEquals(1, events.size());
            assertEquals(1, events.get(0).problems().size());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void directCallsServerManagersAndSchemaMismatchesAreRejected()
    {
        String serverId = id();
        ConfigManager server = ConfigManager.open(spec(serverId, Side.SERVER, 1, builder -> {
            builder.sync(ConfigSyncPolicy.SERVER_TO_CLIENT);
            builder.stringValue("sync.text", "default", "text");
        }), store(Side.SERVER), message ->
        {
        });
        assertThrows(ConfigException.class,
                () -> net.weaver.core.config.ConfigSync.apply(server,
                        new ConfigSyncDocument(serverId, Side.SERVER, 1,
                                Map.of("sync.text", "server"))));

        String clientId = id();
        ConfigManager client = ConfigManager.open(spec(clientId, Side.CLIENT, 2, builder -> {
            builder.sync(ConfigSyncPolicy.SERVER_TO_CLIENT);
            builder.stringValue("sync.text", "default", "text");
        }), store(Side.CLIENT), message ->
        {
        });
        assertThrows(ConfigException.class,
                () -> net.weaver.core.config.ConfigSync.apply(client,
                        new ConfigSyncDocument(clientId, Side.SERVER, 1,
                                Map.of("sync.text", "wrong-version"))));
        assertThrows(ConfigException.class,
                () -> client.applySyncedValues(Map.of("sync.text", "direct"), 2));
        assertEquals("default", client.value("sync.text").get());
        assertEquals(Optional.empty(), client.syncResult());
    }

    private String id()
    {
        String modId = "api.config.sync." + SEQUENCE.incrementAndGet();
        openedIds.add(modId);
        return modId;
    }

    private static ConfigSpec spec(String modId, Side side, int schemaVersion,
                                   java.util.function.Consumer<ConfigBuilder> declarations)
    {
        ConfigBuilder builder = ConfigBuilder.create(modId, side).schemaVersion(schemaVersion);
        declarations.accept(builder);
        return builder.build();
    }

    private static FakeStore store(Side side)
    {
        String file = switch (side)
        {
            case CLIENT -> "client.json";
            case SERVER -> "server.json";
            case BOTH -> "common.json";
        };
        return new FakeStore(side, Path.of("virtual", "config", file));
    }

    private static final class FakeConnection implements NetworkConnection
    {
        private static final NetworkPlayer PLAYER =
                new NetworkPlayer(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000002"),
                        "SyncTest");

        private final NetworkSide side;
        private NetworkPhase phase = NetworkPhase.PLAY;
        private boolean open = true;
        private int sends;
        private byte[] sentBytes;

        private FakeConnection(NetworkSide side)
        {
            this.side = side;
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
        public <T> void send(PayloadType<T> type, T payload)
        {
            sends++;
            sentBytes = type.codec().encode(payload);
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

        private byte[] sentBytes()
        {
            return sentBytes;
        }
    }

    private static final class FakeStore implements ConfigStore
    {
        private final Side side;
        private final Path location;
        private volatile ConfigDocument document;
        private int reads;
        private int writes;

        private FakeStore(Side side, Path location)
        {
            this.side = side;
            this.location = location;
        }

        @Override
        public Side side()
        {
            return side;
        }

        @Override
        public Path location()
        {
            return location;
        }

        @Override
        public synchronized ConfigDocument read()
        {
            reads++;
            return document;
        }

        @Override
        public synchronized void write(ConfigDocument document)
        {
            writes++;
            this.document = document;
        }

        @Override
        public synchronized Path backup(String reason)
        {
            return null;
        }
    }
}
