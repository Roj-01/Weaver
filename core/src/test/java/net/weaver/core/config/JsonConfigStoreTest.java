package net.weaver.core.config;

import net.weaver.api.ModContext;
import net.weaver.api.config.ConfigBuilder;
import net.weaver.api.config.ConfigData;
import net.weaver.api.config.ConfigDocument;
import net.weaver.api.config.ConfigException;
import net.weaver.api.config.ConfigLoadResult;
import net.weaver.api.config.ConfigManager;
import net.weaver.api.config.ConfigMigration;
import net.weaver.api.config.ConfigSpec;
import net.weaver.api.config.ConfigStore;
import net.weaver.api.config.ConfigStoreException;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonConfigStoreTest
{
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @TempDir
    Path tempDir;

    private final List<String> modIds = new ArrayList<>();

    enum Mode
    {
        SLOW,
        NORMAL,
        FAST
    }

    @AfterEach
    void tearDown()
    {
        for (String modId : modIds)
        {
            ConfigManager.unregister(modId);
        }
        modIds.clear();
    }

    @Test
    void missingFileReadsAsNull()
    {
        JsonConfigStore store =
                new JsonConfigStore(tempDir.resolve("missing.json"), Side.CLIENT);

        assertNull(store.read());
    }

    @Test
    void writeAndReadRoundTripJsonTypes()
    {
        Path file = tempDir.resolve("nested").resolve("dir").resolve("config.json");
        JsonConfigStore store = new JsonConfigStore(file, Side.CLIENT);
        List<Object> list = new ArrayList<>(List.of("one", "two", 3));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("flag", true);
        nested.put("number", 7);
        nested.put("text", "héllo →");
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("bool", true);
        values.put("string", "value");
        values.put("integer", 42);
        values.put("long", 9_000_000_000L);
        values.put("double", 1.5);
        values.put("list", list);
        values.put("nested", nested);
        values.put("null", null);
        ConfigDocument document = new ConfigDocument(3, values);

        store.write(document);

        assertTrue(Files.exists(file));
        assertTrue(Files.isDirectory(file.getParent()));
        String text = assertDoesNotThrowReadString(file);
        assertTrue(text.endsWith("\n"), () -> "missing trailing newline: " + text);
        assertTrue(text.contains("\n  \"schemaVersion\""), () -> "not pretty: " + text);
        assertTrue(text.contains("\n  \"values\""), () -> "not pretty: " + text);
        assertTrue(text.contains("héllo →"), () -> "not UTF-8: " + text);
        assertTrue(text.lines().count() > 5);

        ConfigDocument read = store.read();

        assertEquals(3, read.schemaVersion());
        assertEquals(List.of("bool", "string", "integer", "long", "double", "list", "nested",
                        "null"),
                new ArrayList<>(read.values().keySet()));
        assertEquals(Boolean.TRUE, read.values().get("bool"));
        assertEquals("value", read.values().get("string"));
        assertEquals(42L, read.values().get("integer"));
        assertEquals(9_000_000_000L, read.values().get("long"));
        assertEquals(1.5, read.values().get("double"));
        assertTrue(read.values().containsKey("null"));
        assertNull(read.values().get("null"));

        Object readList = read.values().get("list");
        assertTrue(readList instanceof List<?>);
        assertEquals(List.of("one", "two", 3L), readList);
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) readList).add("x"));

        Object readNested = read.values().get("nested");
        assertTrue(readNested instanceof Map<?, ?>);
        Map<?, ?> nestedRead = (Map<?, ?>) readNested;
        assertEquals(List.of("flag", "number", "text"),
                new ArrayList<>(nestedRead.keySet()));
        assertEquals(Boolean.TRUE, nestedRead.get("flag"));
        assertEquals(7L, nestedRead.get("number"));
        assertEquals("héllo →", nestedRead.get("text"));
        assertThrows(UnsupportedOperationException.class,
                () -> ((Map<Object, Object>) nestedRead).put("k", "v"));
    }

    @Test
    void corruptContentIsReportedAsCorrupt()
    {
        assertCorrupt("invalid-json.json", "{ not json");
        assertCorrupt("root-array.json", "[1, 2, 3]");
        assertCorrupt("null-root.json", "null");
        assertCorrupt("missing-version.json", "{\"values\":{}}");
        assertCorrupt("string-version.json", "{\"schemaVersion\":\"x\",\"values\":{}}");
        assertCorrupt("zero-version.json", "{\"schemaVersion\":0,\"values\":{}}");
        assertCorrupt("missing-values.json", "{\"schemaVersion\":1}");
        assertCorrupt("array-values.json", "{\"schemaVersion\":1,\"values\":[]}");
    }

    @Test
    void backupMovesFileAndSanitizesReason() throws IOException
    {
        Path file = tempDir.resolve("backup.json");
        JsonConfigStore store = new JsonConfigStore(file, Side.CLIENT);
        store.write(new ConfigDocument(1, Map.of("k", "v")));
        byte[] original = Files.readAllBytes(file);

        Path backup = store.backup("corrupt");

        assertNotNull(backup);
        assertEquals(file.getParent(), backup.getParent());
        String backupName = backup.getFileName().toString();
        assertTrue(backupName.contains("corrupt"), () -> backupName);
        assertTrue(backupName.endsWith(".bak"), () -> backupName);
        assertFalse(Files.exists(file));
        assertArrayEquals(original, Files.readAllBytes(backup));
        assertNull(store.backup("corrupt"));

        store.write(new ConfigDocument(1, Map.of("k", "v2")));
        Path sanitized = store.backup("bad reason!");

        assertNotNull(sanitized);
        String sanitized_name = sanitized.getFileName().toString();
        assertTrue(sanitized_name.contains("bad-reason"), () -> sanitized_name);
        assertFalse(sanitized_name.contains("bad reason"), () -> sanitized_name);
        assertFalse(sanitized_name.contains("!"), () -> sanitized_name);
        assertTrue(sanitized_name.endsWith(".bak"), () -> sanitized_name);
    }

    @Test
    void invalidUtf8IsReportedAsCorruptAndCanBeBackedUp() throws IOException
    {
        Path file = tempDir.resolve("invalid-utf8.json");
        byte[] bytes = {'{', '"', 's', '"', ':', (byte) 0xC3, (byte) 0x28, '}'};
        Files.write(file, bytes);
        JsonConfigStore store = new JsonConfigStore(file, Side.CLIENT);

        ConfigStoreException failure = assertThrows(ConfigStoreException.class, store::read);
        assertTrue(failure.corrupt(), failure::getMessage);

        Path backup = store.backup("corrupt");
        assertNotNull(backup);
        assertArrayEquals(bytes, Files.readAllBytes(backup));
    }

    @Test
    void atomicWriteFailureLeavesPreviousFileIntact() throws IOException
    {
        Path file = tempDir.resolve("atomic.json");
        JsonConfigStore store = new JsonConfigStore(file, Side.CLIENT);
        store.write(new ConfigDocument(1, Map.of("k", "original")));
        byte[] original = Files.readAllBytes(file);

        JsonConfigStore failing = new JsonConfigStore(file, Side.CLIENT,
                (source, target) -> {
                    throw new IOException("move boom");
                });

        ConfigStoreException failure = assertThrows(ConfigStoreException.class,
                () -> failing.write(new ConfigDocument(2, Map.of("k", "changed"))));

        assertFalse(failure.corrupt());
        assertNotNull(failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("move boom"));
        assertArrayEquals(original, Files.readAllBytes(file));
        try (Stream<Path> entries = Files.list(tempDir))
        {
            assertTrue(entries.noneMatch(
                    entry -> entry.getFileName().toString().endsWith(".tmp")));
        }
        catch (IOException failureWhileListing)
        {
            throw new AssertionError(failureWhileListing);
        }
    }

    @Test
    void perSideFilesResolveFromModDirectoryAndContext()
    {
        Path root = tempDir.resolve("config");

        assertTrue(ConfigFiles.forMod(root, Side.CLIENT).location().endsWith("client.json"));
        assertEquals(root.resolve("client.json"),
                ConfigFiles.forMod(root, Side.CLIENT).location());
        assertEquals(root.resolve("server.json"),
                ConfigFiles.forMod(root, Side.SERVER).location());
        assertEquals(root.resolve("common.json"),
                ConfigFiles.forMod(root, Side.BOTH).location());
        assertEquals("client.json", ConfigFiles.fileName(Side.CLIENT));
        assertEquals("server.json", ConfigFiles.fileName(Side.SERVER));
        assertEquals("common.json", ConfigFiles.fileName(Side.BOTH));

        ModContext serverContext = context("per-side.server", "server", root);
        ConfigStore serverStore = ConfigFiles.forContext(serverContext);
        assertEquals(Side.SERVER, serverStore.side());
        assertEquals(root.resolve("server.json"), serverStore.location());

        ModContext clientContext = context("per-side.client", "Client", root);
        assertEquals(Side.CLIENT, ConfigFiles.forContext(clientContext).side());

        ConfigException failure = assertThrows(ConfigException.class,
                () -> ConfigFiles.forContext(context("per-side.bad", "proxy", root)));
        assertTrue(failure.getMessage().contains("proxy"));
        assertThrows(NullPointerException.class, () -> ConfigFiles.fileName(null));
        assertThrows(NullPointerException.class, () -> ConfigFiles.forMod(null, Side.CLIENT));
        assertThrows(NullPointerException.class, () -> ConfigFiles.forMod(root, null));
        assertThrows(NullPointerException.class, () -> ConfigFiles.forContext(null));
    }

    @Test
    void weaverConfigEndToEndLoadsDefaultsAndSaves()
    {
        String modId = id();
        Path configDir = tempDir.resolve(modId);
        List<String> logs = new ArrayList<>();
        ModContext context = context(modId, "client", configDir,
                new EventBus("weaver-config-test-" + modId), logs);
        ConfigSpec spec = freshSpec(modId);

        ConfigManager manager = WeaverConfig.open(context, spec);

        assertSame(manager, ConfigManager.get(modId));
        assertFalse(manager.result().filePresent());
        assertEquals(0, manager.result().appliedValues());
        assertEquals(spec.size(), manager.result().defaultedValues());
        assertEquals("default", manager.value("general.message").get());
        assertEquals(2, manager.value("general.count").get());
        assertFalse(Files.exists(configDir.resolve("client.json")));
        assertTrue(logs.stream().anyMatch(line -> line.contains("CONFIG LOADED")));

        manager.spec().value("general.message", String.class).set("saved");
        manager.spec().value("general.count", Integer.class).set(5);
        manager.save();

        assertTrue(Files.exists(configDir.resolve("client.json")));
        assertFalse(Files.exists(configDir.resolve("server.json")));

        ConfigManager.unregister(modId);
        ConfigSpec fresh = freshSpec(modId);
        ConfigManager reopened = WeaverConfig.open(context, fresh);

        assertEquals("saved", reopened.value("general.message").get());
        assertEquals(5, reopened.value("general.count").get());
        assertEquals(2, reopened.result().appliedValues());
        assertEquals(0, reopened.result().defaultedValues());
    }

    @Test
    void corruptFileEndToEndIsBackedUpAndDefaultsApplied() throws IOException
    {
        String modId = id();
        Path configDir = tempDir.resolve(modId);
        Files.createDirectories(configDir);
        Path clientFile = configDir.resolve("client.json");
        String garbage = "not json {{{";
        try
        {
            Files.writeString(clientFile, garbage, StandardCharsets.UTF_8);
        }
        catch (IOException failure)
        {
            throw new AssertionError(failure);
        }
        List<String> logs = new ArrayList<>();
        ModContext context = context(modId, "client", configDir,
                new EventBus("weaver-config-corrupt-" + modId), logs);
        ConfigSpec spec = freshSpec(modId);

        ConfigManager manager = WeaverConfig.open(context, spec);
        ConfigLoadResult result = manager.result();

        assertTrue(result.corrupt());
        assertEquals(1, result.problems().size());
        assertEquals("<file>", result.problems().get(0).key());
        assertNotNull(result.backup());
        assertTrue(Files.exists(result.backup()));
        assertEquals(garbage,
                assertDoesNotThrowReadString(result.backup()));
        assertFalse(Files.exists(clientFile));
        assertEquals("default", manager.value("general.message").get());
        assertEquals(2, manager.value("general.count").get());
    }

    @Test
    void migrationEndToEndRenamesKeyOnDisk()
    {
        String modId = id();
        Path configDir = tempDir.resolve(modId);
        JsonConfigStore seed = new JsonConfigStore(configDir.resolve("client.json"),
                Side.CLIENT);
        seed.write(new ConfigDocument(1, Map.of("general.message", "hello")));
        List<String> logs = new ArrayList<>();
        ModContext context = context(modId, "client", configDir,
                new EventBus("weaver-config-migration-" + modId), logs);
        ConfigBuilder builder = ConfigBuilder.create(modId, Side.CLIENT)
                .schemaVersion(2)
                .description("migration end to end");
        builder.migration(migration(1,
                data -> data.rename("general.message", "general.greeting")));
        builder.stringValue("general.greeting", "default", "greeting");

        ConfigManager manager = WeaverConfig.open(context, builder.build());

        assertTrue(manager.result().migrated());
        assertEquals(1, manager.result().fileVersion());
        assertEquals(2, manager.result().appliedVersion());
        assertEquals("hello", manager.value("general.greeting").get());
        manager.save();

        JsonConfigStore reader = new JsonConfigStore(configDir.resolve("client.json"),
                Side.CLIENT);
        ConfigDocument saved = reader.read();
        assertEquals(2, saved.schemaVersion());
        assertEquals("hello", saved.values().get("general.greeting"));
        assertFalse(saved.values().containsKey("general.message"));
    }

    @Test
    void sideMismatchThroughWeaverConfigCreatesNoFile()
    {
        String modId = id();
        Path configDir = tempDir.resolve(modId);
        List<String> logs = new ArrayList<>();
        ModContext context = context(modId, "client", configDir,
                new EventBus("weaver-config-side-" + modId), logs);
        ConfigBuilder serverBuilder = ConfigBuilder.create(modId, Side.SERVER);
        serverBuilder.stringValue("general.message", "default", "message");
        ConfigSpec serverSpec = serverBuilder.build();

        ConfigException failure = assertThrows(ConfigException.class,
                () -> WeaverConfig.open(context, serverSpec));

        assertTrue(failure.getMessage().contains("SERVER"));
        assertTrue(failure.getMessage().contains("CLIENT"));
        assertFalse(Files.exists(configDir.resolve("client.json")));
        assertFalse(Files.exists(configDir.resolve("server.json")));
        assertFalse(Files.exists(configDir));
        assertTrue(ConfigManager.find(modId).isEmpty());
    }

    @Test
    void lastModifiedTracksWritesAndMissingFiles() throws IOException
    {
        Path file = tempDir.resolve("tracked.json");
        JsonConfigStore store = new JsonConfigStore(file, Side.CLIENT);

        assertEquals(Long.MIN_VALUE, store.lastModified());

        store.write(new ConfigDocument(1, Map.of("k", "v")));

        long written = store.lastModified();
        assertTrue(written != Long.MIN_VALUE);
        FileTime changed = FileTime.fromMillis(System.currentTimeMillis() + 5_000L);
        Files.setLastModifiedTime(file, changed);
        long observed = store.lastModified();

        assertTrue(observed > written,
                () -> "expected a newer token: " + observed + " <= " + written);

        Files.delete(file);

        assertEquals(Long.MIN_VALUE, store.lastModified());
    }

    private void assertCorrupt(String fileName, String content)
    {
        Path file = tempDir.resolve("corrupt").resolve(fileName);
        try
        {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        }
        catch (IOException failure)
        {
            throw new AssertionError(failure);
        }
        JsonConfigStore store = new JsonConfigStore(file, Side.CLIENT);

        ConfigStoreException failure =
                assertThrows(ConfigStoreException.class, store::read,
                        () -> "expected a corrupt failure for " + fileName);

        assertTrue(failure.corrupt(), () -> fileName + ": " + failure.getMessage());
        assertTrue(failure.getMessage().contains(file.toString()),
                () -> fileName + ": " + failure.getMessage());
    }

    private String assertDoesNotThrowReadString(Path file)
    {
        try
        {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        catch (IOException failure)
        {
            throw new AssertionError(failure);
        }
    }

    private String id()
    {
        String modId = "core.config." + SEQUENCE.incrementAndGet();
        modIds.add(modId);
        return modId;
    }

    private static ConfigSpec freshSpec(String modId)
    {
        ConfigBuilder builder = ConfigBuilder.create(modId, Side.CLIENT)
                .schemaVersion(1)
                .description("end to end config");
        builder.stringValue("general.message", "default", "message");
        builder.intValue("general.count", 2, 0, 8, "count");
        return builder.build();
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

    private static ModContext context(String modId, String side, Path configDir)
    {
        return context(modId, side, configDir, new EventBus("context-" + modId),
                new ArrayList<>());
    }

    private static ModContext context(String modId, String side, Path configDir,
                                      EventBus bus, List<String> logs)
    {
        return new ModContext()
        {
            @Override
            public String id()
            {
                return modId;
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
                return configDir.resolve("data");
            }

            @Override
            public void log(String message)
            {
                logs.add(message);
            }

            @Override
            public EventBus modEvents()
            {
                return bus;
            }
        };
    }
}
