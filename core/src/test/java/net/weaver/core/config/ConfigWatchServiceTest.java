package net.weaver.core.config;

import net.weaver.api.config.ConfigBuilder;
import net.weaver.api.config.ConfigDocument;
import net.weaver.api.config.ConfigManager;
import net.weaver.api.config.ConfigSpec;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.Side;
import net.weaver.api.event.events.config.ConfigReloadedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigWatchServiceTest
{
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final String WATCH_PROPERTY = "weaver.config.watch";

    @TempDir
    Path tempDir;

    private final List<String> modIds = new ArrayList<>();
    private String originalWatchProperty;

    @BeforeEach
    void disableAutomaticRegistration()
    {
        originalWatchProperty = System.getProperty(WATCH_PROPERTY);
        System.clearProperty(WATCH_PROPERTY);
    }

    @AfterEach
    void tearDown()
    {
        for (String modId : modIds)
        {
            ConfigManager.unregister(modId);
        }
        modIds.clear();
        if (originalWatchProperty == null)
        {
            System.clearProperty(WATCH_PROPERTY);
        }
        else
        {
            System.setProperty(WATCH_PROPERTY, originalWatchProperty);
        }
    }

    @Test
    void scanOnceReloadsWhenTheFileChanges() throws IOException
    {
        String modId = id();
        Watched watched = watched(modId, "first");
        List<String> logs = new ArrayList<>();
        List<ConfigReloadedEvent> events = new ArrayList<>();
        ListenerHandle handle = watched.bus().addListener(ConfigReloadedEvent.class, events::add);

        try (ConfigWatchService watcher = new ConfigWatchService(logs::add, false))
        {
            assertTrue(watcher.watch(watched.manager()));
            assertTrue(watcher.isWatching(watched.manager()));
            assertEquals(1, watcher.watchedCount());
            assertEquals(1, watched.manager().generation());

            write(watched.file(), "second");
            forceFutureTimestamp(watched.file());

            List<Path> reloaded = watcher.scanOnce();

            assertEquals(1, reloaded.size());
            assertEquals(watched.file().toAbsolutePath().normalize(), reloaded.get(0));
            assertEquals("second", watched.manager().value("general.value").get());
            assertEquals(2, watched.manager().generation());
            assertEquals(1, events.size());
            assertEquals(2, events.get(0).generation());
            assertSame(watched.manager().result(), events.get(0).result());
            assertTrue(watcher.scanOnce().isEmpty(),
                    () -> "a stable file must not reload twice: " + logs);
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void unchangedLastModifiedDoesNotReload() throws IOException
    {
        String modId = id();
        Watched watched = watched(modId, "first");
        List<String> logs = new ArrayList<>();
        FileTime baseline = Files.getLastModifiedTime(watched.file());

        try (ConfigWatchService watcher = new ConfigWatchService(logs::add, false))
        {
            assertTrue(watcher.watch(watched.manager()));

            Files.writeString(watched.file(),
                    "{\"schemaVersion\":1,\"values\":{\"general.value\":\"second\"}}\n",
                    StandardCharsets.UTF_8);
            Files.setLastModifiedTime(watched.file(), baseline);

            assertTrue(watcher.scanOnce().isEmpty());
            assertEquals("first", watched.manager().value("general.value").get());
            assertEquals(1, watched.manager().generation());
        }
    }

    @Test
    void missingFileReloadsWhenItIsCreated() throws IOException
    {
        String modId = id();
        Path directory = tempDir.resolve(modId);
        Files.createDirectories(directory);
        Path file = directory.resolve("client.json");
        JsonConfigStore store = new JsonConfigStore(file, Side.CLIENT);
        ConfigManager manager = ConfigManager.open(spec(modId), store, message -> { });

        try (ConfigWatchService watcher = new ConfigWatchService(message -> { }, false))
        {
            assertTrue(watcher.watch(manager));
            assertTrue(watcher.scanOnce().isEmpty());
            assertEquals("default", manager.value("general.value").get());
            assertEquals(1, manager.generation());

            store.write(new ConfigDocument(1, Map.of("general.value", "created")));

            List<Path> reloaded = watcher.scanOnce();

            assertEquals(1, reloaded.size());
            assertEquals("created", manager.value("general.value").get());
            assertEquals(2, manager.generation());
        }
    }

    @Test
    void unwatchStopsReloads() throws IOException
    {
        String modId = id();
        Watched watched = watched(modId, "first");

        try (ConfigWatchService watcher = new ConfigWatchService(message -> { }, false))
        {
            assertTrue(watcher.watch(watched.manager()));
            assertTrue(watcher.unwatch(watched.manager()));
            assertFalse(watcher.unwatch(watched.manager()));
            assertEquals(0, watcher.watchedCount());

            write(watched.file(), "second");
            forceFutureTimestamp(watched.file());

            assertTrue(watcher.scanOnce().isEmpty());
            assertEquals("first", watched.manager().value("general.value").get());
            assertEquals(1, watched.manager().generation());
        }
    }

    @Test
    void closeIsIdempotentAndStopsTracking() throws IOException
    {
        String modId = id();
        Watched watched = watched(modId, "first");
        ConfigWatchService watcher = new ConfigWatchService(message -> { }, false);

        assertTrue(watcher.watch(watched.manager()));
        watcher.close();
        watcher.close();

        assertEquals(0, watcher.watchedCount());
        assertFalse(watcher.isWatching(watched.manager()));
        assertFalse(watcher.watch(watched.manager()),
                "a closed watcher must not accept new registrations");

        write(watched.file(), "second");
        forceFutureTimestamp(watched.file());

        assertTrue(watcher.scanOnce().isEmpty());
        assertEquals(1, watched.manager().generation());
    }

    @Test
    void configManagerOpenRegistersWithSharedWatcherWhenEnabled() throws IOException
    {
        String modId = id();
        System.setProperty(WATCH_PROPERTY, "true");
        Path directory = tempDir.resolve(modId);
        Files.createDirectories(directory);
        Path file = directory.resolve("client.json");
        JsonConfigStore store = new JsonConfigStore(file, Side.CLIENT);
        store.write(new ConfigDocument(1, Map.of("general.value", "first")));
        List<String> logs = new ArrayList<>();
        ConfigManager manager = ConfigManager.open(spec(modId), store, logs::add);

        ConfigWatchService shared = ConfigWatchService.shared();
        try
        {
            assertTrue(shared.isWatching(manager),
                    () -> "ConfigManager.open did not register with the shared watcher: "
                            + logs);
            assertSame(shared, ConfigWatchService.shared());
        }
        finally
        {
            assertTrue(shared.unwatch(manager));
        }
    }

    private Watched watched(String modId, String value) throws IOException
    {
        Path directory = tempDir.resolve(modId);
        Files.createDirectories(directory);
        Path file = directory.resolve("client.json");
        JsonConfigStore store = new JsonConfigStore(file, Side.CLIENT);
        store.write(new ConfigDocument(1, Map.of("general.value", value)));
        EventBus bus = new EventBus("config-watch-test-" + modId);
        ConfigManager manager = ConfigManager.open(spec(modId), store, bus, message -> { });
        return new Watched(file, bus, manager);
    }

    private static void write(Path file, String value) throws IOException
    {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("general.value", value);
        new JsonConfigStore(file, Side.CLIENT).write(new ConfigDocument(1, values));
    }

    private static void forceFutureTimestamp(Path file) throws IOException
    {
        Files.setLastModifiedTime(file,
                FileTime.fromMillis(System.currentTimeMillis() + 5_000L));
    }

    private String id()
    {
        String modId = "core.watch." + SEQUENCE.incrementAndGet();
        modIds.add(modId);
        return modId;
    }

    private static ConfigSpec spec(String modId)
    {
        ConfigBuilder builder = ConfigBuilder.create(modId, Side.CLIENT).schemaVersion(1);
        builder.stringValue("general.value", "default", "value");
        return builder.build();
    }

    private record Watched(Path file, EventBus bus, ConfigManager manager)
    {
    }
}
