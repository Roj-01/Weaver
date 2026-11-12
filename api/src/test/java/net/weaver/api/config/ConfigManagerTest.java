package net.weaver.api.config;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.Side;
import net.weaver.api.event.events.config.ConfigLoadedEvent;
import net.weaver.api.event.events.config.ConfigReloadedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest
{
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

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
    void missingFileAppliesDefaultsWithoutWriting()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 3, builder -> {
            builder.description("missing file");
            builder.booleanValue("general.flag", true, "flag");
            builder.intValue("general.count", 4, 0, 10, "count");
            builder.stringValue("general.message", "default", "message");
        });
        FakeStore store = store(Side.CLIENT);
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        ConfigLoadResult result = manager.result();

        assertFalse(result.filePresent());
        assertEquals(0, result.fileVersion());
        assertEquals(3, result.appliedVersion());
        assertEquals(0, result.appliedValues());
        assertEquals(spec.size(), result.defaultedValues());
        assertFalse(result.migrated());
        assertFalse(result.corrupt());
        assertNull(result.backup());
        assertTrue(result.problems().isEmpty());
        assertFalse(result.hasProblems());
        assertTrue(manager.isLoaded());
        assertTrue(manager.problems().isEmpty());
        assertEquals(true, manager.value("general.flag").get());
        assertEquals(4, manager.value("general.count").get());
        assertEquals("default", manager.value("general.message").get());

        assertEquals(1, store.reads);
        assertEquals(0, store.writes);
        assertEquals(0, store.backups);
    }

    @Test
    void roundTripPersistsEveryScalarKind()
    {
        String modId = id();
        Consumer<ConfigBuilder> declarations = builder -> {
            builder.intValue("round.int", 1, 0, 100, "int");
            builder.longValue("round.long", 2L, "long");
            builder.doubleValue("round.double", 1.0, 0.0, 10.0, "double");
            builder.stringValue("round.string", "default", "string");
            builder.enumValue("round.enum", Mode.class, Mode.NORMAL, "enum");
            builder.stringListValue("round.list", List.of("default"), "list");
        };
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, declarations);
        FakeStore store = store(Side.CLIENT);
        List<String> logs = new ArrayList<>();

        ConfigManager first = ConfigManager.open(spec, store, logs::add);
        first.spec().value("round.int", Integer.class).set(42);
        first.spec().value("round.long", Long.class).set(9_000_000_000L);
        first.spec().value("round.double", Double.class).set(2.5);
        first.spec().value("round.string", String.class).set("saved");
        first.spec().value("round.enum", Mode.class).set(Mode.FAST);
        first.spec().value("round.list", List.class).set(List.of("a", "b"));
        first.save();

        assertNotNull(store.document);
        assertEquals(1, store.document.schemaVersion());
        assertEquals(42, store.document.values().get("round.int"));
        assertEquals(9_000_000_000L, store.document.values().get("round.long"));
        assertEquals(2.5, store.document.values().get("round.double"));
        assertEquals("saved", store.document.values().get("round.string"));
        assertEquals("FAST", store.document.values().get("round.enum"));
        assertEquals(List.of("a", "b"), store.document.values().get("round.list"));
        assertEquals(1, store.writes);

        ConfigManager.unregister(modId);
        ConfigSpec fresh = spec(modId, Side.CLIENT, 1, declarations);

        ConfigManager second = ConfigManager.open(fresh, store, logs::add);
        assertEquals(42, second.value("round.int").get());
        assertEquals(9_000_000_000L, second.value("round.long").get());
        assertEquals(2.5, second.value("round.double").get());
        assertEquals("saved", second.value("round.string").get());
        assertEquals(Mode.FAST, second.value("round.enum").get());
        assertEquals(List.of("a", "b"), second.value("round.list").get());
        assertEquals(6, second.result().appliedValues());
        assertEquals(0, second.result().defaultedValues());
        assertFalse(second.result().migrated());
    }

    @Test
    void invalidRawValuesFallBackToDefaultsAndAreReported()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 2, builder -> {
            builder.intValue("good.int", 1, 0, 10, "good int");
            builder.stringValue("good.string", "fallback", "good string");
            builder.intValue("bad.int", 5, 0, 10, "bad int");
            builder.longValue("bad.long", 5L, 0L, 10L, "bad long");
            builder.doubleValue("bad.double", 1.0, 0.0, 10.0, "bad double");
            builder.stringValue("bad.string", "default", "bad string");
            builder.enumValue("bad.enum", Mode.class, Mode.NORMAL, "bad enum");
            builder.stringListValue("bad.list", List.of("safe"), 1, 3, "bad list");
        });
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("good.int", 7);
        values.put("good.string", "from-file");
        values.put("bad.int", "not-a-number");
        values.put("bad.long", 99L);
        values.put("bad.double", "not-a-decimal");
        values.put("bad.string", 123);
        values.put("bad.enum", "ULTRA");
        values.put("bad.list", List.of("ok", 5));
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(2, values);
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        ConfigLoadResult result = manager.result();

        assertFalse(result.corrupt());
        assertFalse(result.migrated());
        assertEquals(2, result.fileVersion());
        assertEquals(2, result.appliedVersion());
        assertEquals(2, result.appliedValues());
        assertEquals(spec.size() - 2, result.defaultedValues());
        assertEquals(6, result.problems().size());
        assertEquals(7, manager.value("good.int").get());
        assertEquals("from-file", manager.value("good.string").get());
        assertEquals(5, manager.value("bad.int").get());
        assertEquals(5L, manager.value("bad.long").get());
        assertEquals(1.0, manager.value("bad.double").get());
        assertEquals("default", manager.value("bad.string").get());
        assertEquals(Mode.NORMAL, manager.value("bad.enum").get());
        assertEquals(List.of("safe"), manager.value("bad.list").get());

        List<String> badKeys = List.of("bad.int", "bad.long", "bad.double", "bad.string",
                "bad.enum", "bad.list");
        for (String key : badKeys)
        {
            assertTrue(result.problems().stream().anyMatch(problem -> problem.key().equals(key)),
                    () -> "missing problem for " + key + ": " + result.problems());
            assertTrue(logs.stream().anyMatch(line -> line.contains("[weaver] CONFIG INVALID")
                            && line.contains("key=" + key)),
                    () -> "missing CONFIG INVALID log for " + key + ": " + logs);
        }
        assertEquals(0, store.writes);
        assertEquals(0, store.backups);
    }

    @Test
    void migrationRenamesOldKeyAndSaveDropsIt()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 2, builder -> {
            builder.migration(migration(1,
                    data -> data.rename("general.old", "general.new")));
            builder.stringValue("general.new", "default", "renamed value");
        });
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(1, Map.of("general.old", "legacy"));
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        ConfigLoadResult result = manager.result();

        assertTrue(result.migrated());
        assertEquals(1, result.fileVersion());
        assertEquals(2, result.appliedVersion());
        assertEquals(1, result.appliedValues());
        assertEquals(0, result.defaultedValues());
        assertTrue(result.problems().isEmpty());
        assertEquals("legacy", manager.value("general.new").get());
        assertEquals(0, store.writes);

        manager.save();

        assertEquals(2, store.document.schemaVersion());
        assertEquals("legacy", store.document.values().get("general.new"));
        assertFalse(store.document.values().containsKey("general.old"));
        assertEquals(1, store.writes);
    }

    @Test
    void multiStepMigrationsRunInOrder()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 3, builder -> {
            builder.migration(migration(2, data -> data.rename("stage.two", "stage.three")));
            builder.migration(migration(1, data -> data.rename("stage.one", "stage.two")));
            builder.stringValue("stage.three", "default", "final stage");
        });
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(1, Map.of("stage.one", "value"));
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);

        assertTrue(manager.result().migrated());
        assertEquals(1, manager.result().fileVersion());
        assertEquals(3, manager.result().appliedVersion());
        assertEquals("value", manager.value("stage.three").get());
        assertTrue(manager.result().problems().isEmpty());

        manager.save();

        assertEquals(3, store.document.schemaVersion());
        assertEquals("value", store.document.values().get("stage.three"));
        assertFalse(store.document.values().containsKey("stage.one"));
        assertFalse(store.document.values().containsKey("stage.two"));
    }

    @Test
    void missingMigrationStepAppliesDefaultsAndDoesNotWrite()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 2, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(1, Map.of("general.value", "filed"));
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        ConfigLoadResult result = manager.result();

        assertFalse(result.migrated());
        assertFalse(result.corrupt());
        assertEquals(1, result.fileVersion());
        assertEquals(2, result.appliedVersion());
        assertEquals(0, result.appliedValues());
        assertEquals(1, result.defaultedValues());
        assertEquals("default", manager.value("general.value").get());
        assertEquals(1, result.problems().size());
        assertEquals("<file>", result.problems().get(0).key());
        assertTrue(result.problems().get(0).message().contains("migration"));
        assertEquals(0, store.writes);
        assertEquals(0, store.backups);
    }

    @Test
    void throwingMigrationAppliesDefaultsAndLeavesFileUntouched()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 2, builder -> {
            builder.migration(migration(1, data -> {
                throw new IllegalStateException("migration boom");
            }));
            builder.stringValue("general.value", "default", "value");
        });
        ConfigDocument original = new ConfigDocument(1, Map.of("general.value", "filed"));
        FakeStore store = store(Side.CLIENT);
        store.document = original;
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        ConfigLoadResult result = manager.result();

        assertFalse(result.migrated());
        assertFalse(result.corrupt());
        assertEquals(0, result.appliedValues());
        assertEquals(1, result.defaultedValues());
        assertEquals("default", manager.value("general.value").get());
        assertEquals(1, result.problems().size());
        assertEquals("<file>", result.problems().get(0).key());
        assertTrue(result.problems().get(0).message().contains("migration boom"));
        assertEquals(0, store.writes);
        assertSame(original, store.document);
        assertEquals("filed", store.document.values().get("general.value"));
        assertTrue(logs.stream().anyMatch(line -> line.contains("CONFIG MIGRATION FAILED")));
    }

    @Test
    void newerFileUsesDefaultsWithoutBackupOrWrite()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 2, builder ->
                builder.intValue("general.count", 3, 0, 8, "count"));
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(99, Map.of("general.count", 7));
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        ConfigLoadResult result = manager.result();

        assertFalse(result.corrupt());
        assertFalse(result.migrated());
        assertEquals(99, result.fileVersion());
        assertEquals(2, result.appliedVersion());
        assertEquals(0, result.appliedValues());
        assertEquals(1, result.defaultedValues());
        assertEquals(3, manager.value("general.count").get());
        assertEquals(1, result.problems().size());
        assertEquals("<file>", result.problems().get(0).key());
        assertNull(result.backup());
        assertEquals(0, store.writes);
        assertEquals(0, store.backups);
        assertTrue(logs.stream().anyMatch(line -> line.contains("CONFIG SCHEMA NEWER")));
    }

    @Test
    void corruptStoreIsBackedUpAndDefaultsApplied()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.CLIENT);
        Path backup = Path.of("virtual", modId, "client.json.corrupt-20260101.bak");
        store.readFailure = ConfigStoreException.corrupt("boom");
        store.backupPath = backup;
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        ConfigLoadResult result = manager.result();

        assertTrue(result.corrupt());
        assertEquals(1, result.problems().size());
        assertEquals("<file>", result.problems().get(0).key());
        assertTrue(result.problems().get(0).message().contains("boom"));
        assertEquals(backup, result.backup());
        assertEquals("default", manager.value("general.value").get());
        assertEquals(0, result.appliedValues());
        assertEquals(1, result.defaultedValues());
        assertEquals(1, store.reads);
        assertEquals(1, store.backups);
        assertEquals(0, store.writes);
        assertTrue(logs.stream().anyMatch(line -> line.contains("CONFIG CORRUPT")));
        assertTrue(logs.stream().anyMatch(line -> line.contains("CONFIG BACKUP")));
    }

    @Test
    void nonCorruptReadFailureAppliesDefaultsWithoutBackup()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.CLIENT);
        store.readFailure = new ConfigStoreException("disk boom");
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        ConfigLoadResult result = manager.result();

        assertFalse(result.corrupt());
        assertEquals(1, result.problems().size());
        assertEquals("<file>", result.problems().get(0).key());
        assertTrue(result.problems().get(0).message().contains("disk boom"));
        assertNull(result.backup());
        assertEquals("default", manager.value("general.value").get());
        assertEquals(0, store.writes);
        assertEquals(0, store.backups);
        assertTrue(logs.stream().anyMatch(line -> line.contains("CONFIG LOAD FAILED")));
    }

    @Test
    void saveAfterNewerSchemaFallbackBacksUpTheFileOnceBeforeWriting()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 2, builder ->
                builder.intValue("general.count", 3, 0, 8, "count"));
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(3, Map.of("general.count", 7));
        store.backupPath = Path.of("virtual", modId, "client.json.newer-schema-1.bak");
        List<String> logs = new ArrayList<>();
        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        assertEquals(0, store.backups);

        manager.save();

        assertEquals(List.of("newer-schema"), store.backupReasons);
        assertEquals(1, store.writes);
        assertTrue(logs.stream().anyMatch(line -> line.contains("CONFIG BACKUP")
                && line.contains("backup=" + store.backupPath)), logs::toString);

        manager.save();

        assertEquals(1, store.backups);
        assertEquals(2, store.writes);
    }

    @Test
    void saveAfterReadFailureOrMigrationFailureBacksUpFirst()
    {
        String readId = id();
        FakeStore unreadable = store(Side.CLIENT);
        unreadable.readFailure = new ConfigStoreException("disk boom");
        ConfigManager readManager = ConfigManager.open(spec(readId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value")), unreadable, null);
        readManager.save();
        assertEquals(List.of("unreadable"), unreadable.backupReasons);
        assertEquals(1, unreadable.writes);

        String migrationId = id();
        FakeStore old = store(Side.CLIENT);
        old.document = new ConfigDocument(1, Map.of("general.value", "filed"));
        ConfigManager migrationManager = ConfigManager.open(spec(migrationId, Side.CLIENT, 2,
                builder -> builder.stringValue("general.value", "default", "value")), old, null);
        migrationManager.save();
        assertEquals(List.of("migration-failed"), old.backupReasons);
        assertEquals(1, old.writes);
    }

    @Test
    void failedPreSaveBackupAbortsTheSaveAndKeepsTheFile()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.CLIENT);
        ConfigDocument original = new ConfigDocument(5, Map.of("general.value", "mine"));
        store.document = original;
        store.backupFailure = new ConfigStoreException("backup boom");
        List<String> logs = new ArrayList<>();
        ConfigManager manager = ConfigManager.open(spec, store, logs::add);

        assertThrows(ConfigStoreException.class, manager::save);

        assertEquals(0, store.writes);
        assertSame(original, store.document);
        assertTrue(logs.stream().anyMatch(line -> line.contains("CONFIG BACKUP FAILED")));

        store.backupFailure = null;
        manager.save();
        assertEquals(1, store.writes);
        assertEquals(2, store.backups);
    }

    @Test
    void normalLoadOrSuccessfulReloadSavesWithoutBackup()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.CLIENT);
        store.readFailure = new ConfigStoreException("disk boom");
        ConfigManager manager = ConfigManager.open(spec, store, null);

        store.readFailure = null;
        store.document = new ConfigDocument(1, Map.of("general.value", "filed"));
        manager.reload();
        manager.save();

        assertEquals(0, store.backups);
        assertEquals(1, store.writes);
    }

    @Test
    void saveFailurePropagatesAndLeavesPreviousDocumentUnchanged()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        ConfigDocument original = new ConfigDocument(1, Map.of("general.value", "old"));
        FakeStore store = store(Side.CLIENT);
        store.document = original;
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        assertEquals("old", manager.value("general.value").get());
        manager.spec().value("general.value", String.class).set("new");
        store.writeFailure = ConfigStoreException.io("disk full", new IOException("full"));

        ConfigStoreException failure = assertThrows(ConfigStoreException.class, manager::save);

        assertEquals("disk full", failure.getMessage());
        assertEquals(1, store.writes);
        assertSame(original, store.document);
        assertEquals("old", store.document.values().get("general.value"));
        assertTrue(logs.stream().anyMatch(line -> line.contains("CONFIG SAVE FAILED")));
    }

    @Test
    void sideMismatchIsRejectedBeforeAnyRead()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.SERVER);
        List<String> logs = new ArrayList<>();

        ConfigException failure = assertThrows(ConfigException.class,
                () -> ConfigManager.open(spec, store, logs::add));

        assertTrue(failure.getMessage().contains("CLIENT"));
        assertTrue(failure.getMessage().contains("SERVER"));
        assertEquals(0, store.reads);
        assertEquals(0, store.writes);
        assertEquals(0, store.backups);
        assertTrue(ConfigManager.find(modId).isEmpty());
    }

    @Test
    void bothSideMatchesBothStore()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.BOTH, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.BOTH);
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);

        assertSame(spec, manager.spec());
        assertEquals(1, store.reads);
        assertFalse(manager.result().filePresent());
        assertEquals("default", manager.value("general.value").get());
    }

    @Test
    void registryTracksOpenManagers()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.CLIENT);
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);

        assertSame(manager, ConfigManager.get(modId));
        assertSame(manager, ConfigManager.find(modId).orElseThrow());
        assertEquals(Optional.of(manager), ConfigManager.find(modId));
        assertEquals(1, store.reads);

        ConfigException duplicate = assertThrows(ConfigException.class,
                () -> ConfigManager.open(spec, store, logs::add));
        assertTrue(duplicate.getMessage().contains("already open"));
        assertEquals(1, store.reads);

        ConfigManager.unregister(modId);

        assertTrue(ConfigManager.find(modId).isEmpty());
        ConfigException missing = assertThrows(ConfigException.class,
                () -> ConfigManager.get(modId));
        assertTrue(missing.getMessage().contains(modId));
        assertTrue(missing.getMessage().contains("open"));
        assertTrue(missing.getMessage().contains("during mod initialization")
                || missing.getMessage().contains("ConfigManager.open"));
    }

    @Test
    void loadedEventIsPostedExactlyOnceAndCanSeeTheManager()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 4, builder -> {
            builder.intValue("general.count", 2, 0, 8, "count");
            builder.stringValue("general.value", "default", "value");
        });
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(4, Map.of("general.count", "bad"));
        EventBus bus = new EventBus("config-manager-test-" + modId);
        List<ConfigLoadedEvent> events = new ArrayList<>();
        AtomicReference<ConfigManager> observed = new AtomicReference<>();
        ListenerHandle handle = bus.addListener(ConfigLoadedEvent.class, event -> {
            events.add(event);
            observed.set(ConfigManager.get(event.modId()));
        });
        List<String> logs = new ArrayList<>();

        try
        {
            ConfigManager manager = ConfigManager.open(spec, store, bus, logs::add);

            assertEquals(1, events.size());
            ConfigLoadedEvent event = events.get(0);
            assertEquals(modId, event.modId());
            assertEquals(Side.CLIENT, event.side());
            assertEquals(4, event.schemaVersion());
            assertEquals(1, event.problems().size());
            assertEquals("general.count", event.problems().get(0).key());
            assertEquals(LogicalSide.CLIENT, event.logicalSide());
            assertSame(manager.result(), event.result());
            assertSame(manager, observed.get());
            assertSame(manager, ConfigManager.get(modId));
            assertSame(manager.result(), manager.load());
            manager.load();
            assertEquals(1, events.size());
            assertEquals(1, event.problems().size());
        }
        finally
        {
            handle.unregister();
            ConfigManager.unregister(modId);
        }
    }

    @Test
    void roundTripThroughAStoreIsStableAcrossManagers()
    {
        String modId = id();
        Consumer<ConfigBuilder> declarations = builder ->
                builder.intValue("general.count", 1, 0, 10, "count");
        ConfigSpec spec = spec(modId, Side.CLIENT, 2, declarations);
        FakeStore store = store(Side.CLIENT);
        List<String> logs = new ArrayList<>();

        ConfigManager first = ConfigManager.open(spec, store, logs::add);
        first.spec().value("general.count", Integer.class).set(6);
        first.save();

        ConfigManager.unregister(modId);
        ConfigSpec fresh = spec(modId, Side.CLIENT, 2, declarations);

        ConfigManager second = ConfigManager.open(fresh, store, logs::add);
        assertEquals(6, second.value("general.count").get());
        assertEquals(1, second.result().appliedValues());
        assertEquals(0, second.result().defaultedValues());
        assertFalse(second.result().migrated());
    }

    @Test
    void concurrentReadersObserveStableValues() throws Exception
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder -> {
            builder.intValue("concurrent.int", 1, 0, 100, "int");
            builder.longValue("concurrent.long", 2L, "long");
            builder.doubleValue("concurrent.double", 3.0, 0.0, 10.0, "double");
            builder.stringValue("concurrent.string", "default", "string");
            builder.enumValue("concurrent.enum", Mode.class, Mode.SLOW, "enum");
            builder.stringListValue("concurrent.list", List.of("default"), "list");
        });
        FakeStore store = store(Side.CLIENT);
        List<String> logs = new ArrayList<>();
        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        manager.spec().value("concurrent.int", Integer.class).set(9);
        manager.spec().value("concurrent.long", Long.class).set(10L);
        manager.spec().value("concurrent.double", Double.class).set(4.5);
        manager.spec().value("concurrent.string", String.class).set("stable");
        manager.spec().value("concurrent.enum", Mode.class).set(Mode.FAST);
        manager.spec().value("concurrent.list", List.class).set(List.of("x", "y"));

        ConfigValue<?> intHandle = manager.spec().value("concurrent.int");
        ConfigValue<?> enumHandle = manager.spec().value("concurrent.enum");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try
        {
            for (int reader = 0; reader < 8; reader++)
            {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int read = 0; read < 100; read++)
                    {
                        assertEquals(9, manager.value("concurrent.int").get());
                        assertEquals(10L, manager.value("concurrent.long").get());
                        assertEquals(4.5, manager.value("concurrent.double").get());
                        assertEquals("stable", manager.value("concurrent.string").get());
                        assertEquals(Mode.FAST, manager.value("concurrent.enum").get());
                        assertEquals(List.of("x", "y"), manager.value("concurrent.list").get());
                        assertSame(intHandle, manager.spec().value("concurrent.int"));
                        assertSame(enumHandle, manager.spec().value("concurrent.enum"));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures)
            {
                future.get(30, TimeUnit.SECONDS);
            }
        }
        finally
        {
            pool.shutdownNow();
        }
    }

    @Test
    void unknownKeysArePreservedAcrossSave()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("known.value", "default", "known"));
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("future.flag", "keep-me");
        values.put("known.value", "old");
        values.put("future.nested", Map.of("x", 1, "y", List.of("a")));
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(1, values);
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        assertEquals("old", manager.value("known.value").get());
        manager.spec().value("known.value", String.class).set("new");
        manager.save();

        assertEquals("keep-me", store.document.values().get("future.flag"));
        assertEquals("new", store.document.values().get("known.value"));
        assertEquals(Map.of("x", 1, "y", List.of("a")),
                store.document.values().get("future.nested"));
    }

    @Test
    void problemsMirrorLoadResultAfterOpen()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.CLIENT);
        store.readFailure = ConfigStoreException.corrupt("broken");
        store.backupPath = Path.of("virtual", "backup.bak");
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);

        assertTrue(manager.isLoaded());
        assertNotNull(manager.result());
        assertSame(manager.result(), manager.load());
        assertEquals(manager.result().problems(), manager.problems());
        assertFalse(manager.problems().isEmpty());
    }

    @Test
    void defaultStoreLastModifiedIsUnknown()
    {
        FakeStore store = store(Side.CLIENT);

        assertEquals(Long.MIN_VALUE, store.lastModified());
    }

    @Test
    void reloadReReadsStoreAndIncrementsGeneration()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(1, Map.of("general.value", "first"));
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);

        assertEquals(1, manager.generation());
        assertEquals("first", manager.value("general.value").get());
        assertEquals(1, store.reads);

        store.document = new ConfigDocument(1, Map.of("general.value", "second"));
        ConfigLoadResult reloaded = manager.reload();

        assertSame(reloaded, manager.result());
        assertEquals("second", manager.value("general.value").get());
        assertEquals(2, manager.generation());
        assertEquals(2, store.reads);
        assertSame(reloaded, manager.load());
        assertEquals(2, store.reads);
        assertTrue(logs.stream().anyMatch(line -> line.contains("[weaver] CONFIG RELOADED")
                        && line.contains("mod=" + modId)),
                () -> "missing CONFIG RELOADED log: " + logs);

        store.document = new ConfigDocument(1, Map.of("general.value", "third"));
        manager.reload();

        assertEquals("third", manager.value("general.value").get());
        assertEquals(3, manager.generation());
        assertEquals(3, store.reads);
    }

    @Test
    void reloadPostsANewConfigReloadedEventForEveryPass()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(1, Map.of("general.value", "first"));
        EventBus bus = new EventBus("config-reload-event-test-" + modId);
        List<ConfigLoadedEvent> loads = new ArrayList<>();
        List<ConfigReloadedEvent> reloads = new ArrayList<>();
        ListenerHandle loadHandle = bus.addListener(ConfigLoadedEvent.class, loads::add);
        ListenerHandle reloadHandle = bus.addListener(ConfigReloadedEvent.class, reloads::add);
        List<String> logs = new ArrayList<>();

        try
        {
            ConfigManager manager = ConfigManager.open(spec, store, bus, logs::add);

            assertEquals(1, loads.size());
            assertTrue(reloads.isEmpty());
            assertEquals(1, manager.generation());

            store.document = new ConfigDocument(1, Map.of("general.value", "reloaded"));
            ConfigLoadResult result = manager.reload();

            assertEquals(1, reloads.size());
            ConfigReloadedEvent event = reloads.get(0);
            assertEquals(modId, event.modId());
            assertEquals(Side.CLIENT, event.side());
            assertEquals(LogicalSide.CLIENT, event.logicalSide());
            assertEquals(1, event.schemaVersion());
            assertEquals(2, event.generation());
            assertSame(spec, event.spec());
            assertSame(result, event.result());
            assertTrue(event.problems().isEmpty());
            assertSame(manager, ConfigManager.get(modId));

            store.document = new ConfigDocument(1, Map.of("general.value", "again"));
            ConfigLoadResult second = manager.reload();

            assertEquals(2, reloads.size());
            assertEquals(3, reloads.get(1).generation());
            assertSame(second, reloads.get(1).result());
            assertEquals(1, loads.size());
        }
        finally
        {
            loadHandle.unregister();
            reloadHandle.unregister();
        }
    }

    @Test
    void reloadAppliesDefaultsWhenTheFileDisappears()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(1, Map.of("general.value", "filed"));
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        assertEquals("filed", manager.value("general.value").get());
        assertEquals(1, manager.generation());

        store.document = null;
        ConfigLoadResult reloaded = manager.reload();

        assertFalse(reloaded.filePresent());
        assertEquals(0, reloaded.appliedValues());
        assertEquals(1, reloaded.defaultedValues());
        assertTrue(reloaded.problems().isEmpty());
        assertEquals("default", manager.value("general.value").get());
        assertEquals(2, manager.generation());
    }

    @Test
    void reloadHandlesCorruptContentLikeTheInitialLoad()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.stringValue("general.value", "default", "value"));
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(1, Map.of("general.value", "filed"));
        Path backup = Path.of("virtual", modId, "client.json.corrupt-reload.bak");
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        assertEquals("filed", manager.value("general.value").get());

        store.readFailure = ConfigStoreException.corrupt("reload boom");
        store.backupPath = backup;
        ConfigLoadResult reloaded = manager.reload();

        assertTrue(reloaded.corrupt());
        assertEquals(backup, reloaded.backup());
        assertEquals(1, reloaded.problems().size());
        assertEquals("<file>", reloaded.problems().get(0).key());
        assertTrue(reloaded.problems().get(0).message().contains("reload boom"));
        assertEquals("default", manager.value("general.value").get());
        assertEquals(1, store.backups);
        assertEquals(2, manager.generation());
        assertTrue(logs.stream().anyMatch(line -> line.contains("CONFIG CORRUPT")));
        assertTrue(logs.stream().anyMatch(line -> line.contains("[weaver] CONFIG RELOADED")));
    }

    @Test
    void reloadReappliesMigrationsValidationAndPreservedKeys()
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 2, builder -> {
            builder.migration(migration(1,
                    data -> data.rename("general.old", "general.new")));
            builder.intValue("general.new", 1, 0, 10, "migrated integer");
        });
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(1, Map.of("general.old", 4));
        List<String> logs = new ArrayList<>();

        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        assertTrue(manager.result().migrated());
        assertEquals(4, manager.value("general.new").get());

        LinkedHashMap<String, Object> raw = new LinkedHashMap<>();
        raw.put("general.old", "not-a-number");
        raw.put("future.key", "kept");
        store.document = new ConfigDocument(1, raw);
        ConfigLoadResult reloaded = manager.reload();

        assertTrue(reloaded.migrated());
        assertEquals(1, reloaded.fileVersion());
        assertEquals(2, reloaded.appliedVersion());
        assertEquals(0, reloaded.appliedValues());
        assertEquals(1, reloaded.defaultedValues());
        assertEquals(1, reloaded.problems().size());
        assertEquals("general.new", reloaded.problems().get(0).key());
        assertEquals(1, manager.value("general.new").get());
        assertEquals(2, manager.generation());

        manager.save();
        assertEquals("kept", store.document.values().get("future.key"));
        assertFalse(store.document.values().containsKey("general.old"));
    }

    @Test
    void concurrentLoadReloadSaveAndSyncDoNotCorruptState() throws Exception
    {
        String modId = id();
        ConfigSpec spec = spec(modId, Side.CLIENT, 1, builder ->
                builder.intValue("general.count", 0, 0, 100, "count"));
        FakeStore store = store(Side.CLIENT);
        store.document = new ConfigDocument(1, Map.of("general.count", 1));
        List<String> logs = new ArrayList<>();
        ConfigManager manager = ConfigManager.open(spec, store, logs::add);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try
        {
            futures.add(pool.submit(() -> {
                start.await();
                for (int pass = 0; pass < 20; pass++)
                {
                    manager.reload();
                }
                return null;
            }));
            futures.add(pool.submit(() -> {
                start.await();
                for (int pass = 0; pass < 20; pass++)
                {
                    manager.save();
                }
                return null;
            }));
            futures.add(pool.submit(() -> {
                start.await();
                for (int pass = 0; pass < 100; pass++)
                {
                    assertNotNull(manager.result());
                    manager.load();
                }
                return null;
            }));
            futures.add(pool.submit(() -> {
                start.await();
                for (int pass = 0; pass < 40; pass++)
                {
                    net.weaver.core.config.ConfigSync.apply(manager,
                            new ConfigSyncDocument(modId, Side.SERVER, 1,
                                    Map.of("general.count", 50)));
                }
                return null;
            }));
            start.countDown();
            for (Future<?> future : futures)
            {
                future.get(30, TimeUnit.SECONDS);
            }
        }
        finally
        {
            pool.shutdownNow();
        }

        assertTrue(manager.generation() >= 21);
        assertNotNull(manager.result());
        assertEquals(manager.result().problems(), manager.problems());
        assertTrue(manager.syncResult().isPresent());
        assertTrue(manager.syncResult().orElseThrow().appliedValues() >= 0);
    }

    private String id()
    {
        String modId = "api.config." + SEQUENCE.incrementAndGet();
        openedIds.add(modId);
        return modId;
    }

    private static ConfigSpec spec(String modId, Side side, int schemaVersion,
                                   Consumer<ConfigBuilder> declarations)
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

    private static ConfigMigration migration(int fromVersion,
                                             Consumer<ConfigData> action)
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

    private static final class FakeStore implements ConfigStore
    {
        private final Side side;
        private final Path location;
        private volatile ConfigDocument document;
        private int reads;
        private int writes;
        private int backups;
        private volatile ConfigStoreException readFailure;
        private volatile ConfigStoreException writeFailure;
        private volatile Path backupPath;
        private volatile ConfigStoreException backupFailure;
        private final List<String> backupReasons = new ArrayList<>();

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
        public synchronized ConfigDocument read() throws ConfigStoreException
        {
            reads++;
            if (readFailure != null)
            {
                throw readFailure;
            }
            return document;
        }

        @Override
        public synchronized void write(ConfigDocument document) throws ConfigStoreException
        {
            writes++;
            if (writeFailure != null)
            {
                throw writeFailure;
            }
            this.document = document;
        }

        @Override
        public synchronized Path backup(String reason) throws ConfigStoreException
        {
            backups++;
            backupReasons.add(reason);
            if (backupFailure != null)
            {
                throw backupFailure;
            }
            return backupPath;
        }
    }
}
