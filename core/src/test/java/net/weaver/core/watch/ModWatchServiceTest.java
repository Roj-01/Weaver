package net.weaver.core.watch;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.events.ModsChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModWatchServiceTest
{
    @TempDir
    Path tempDir;

    @Test
    void scanOnceDetectsAddedModifiedAndRemovedJars() throws IOException
    {
        List<String> logs = new CopyOnWriteArrayList<>();
        Path jar = tempDir.resolve("example.jar");
        try (ModWatchService watcher = ModWatchService.manual(logs::add))
        {
            assertTrue(watcher.start(tempDir, logs::add));
            assertTrue(watcher.isWatching());

            Files.writeString(jar, "first");
            List<ModChangeReport> added = watcher.scanOnce();

            assertEquals(1, added.size());
            assertEquals(ModChangeReport.Kind.ADDED, added.get(0).kind());
            assertEquals(jar.toAbsolutePath().normalize(), added.get(0).path());
            assertEquals(5L, added.get(0).size());

            Files.writeString(jar, "second-value");
            Files.setLastModifiedTime(jar,
                    FileTime.fromMillis(System.currentTimeMillis() + 5_000L));
            List<ModChangeReport> modified = watcher.scanOnce();

            assertEquals(1, modified.size());
            assertEquals(ModChangeReport.Kind.MODIFIED, modified.get(0).kind());
            assertEquals(12L, modified.get(0).size());

            Files.delete(jar);
            List<ModChangeReport> removed = watcher.scanOnce();

            assertEquals(1, removed.size());
            assertEquals(ModChangeReport.Kind.REMOVED, removed.get(0).kind());
            assertEquals(12L, removed.get(0).size());

            assertTrue(watcher.scanOnce().isEmpty(),
                    () -> "a stable directory must not report twice: " + logs);
        }
    }

    @Test
    void scanOnceIgnoresNonJarFilesAndIsIdempotent() throws IOException
    {
        List<String> logs = new CopyOnWriteArrayList<>();
        try (ModWatchService watcher = ModWatchService.manual(logs::add))
        {
            assertTrue(watcher.start(tempDir, logs::add));

            Files.writeString(tempDir.resolve("notes.txt"), "ignore me");
            Files.createDirectories(tempDir.resolve("folder.jar"));
            assertTrue(watcher.scanOnce().isEmpty());

            Files.writeString(tempDir.resolve("a.jar"), "a");
            Files.writeString(tempDir.resolve("b.jar"), "b");
            List<ModChangeReport> changes = watcher.scanOnce();

            assertEquals(2, changes.size());
            assertEquals(ModChangeReport.Kind.ADDED, changes.get(0).kind());
            assertEquals(ModChangeReport.Kind.ADDED, changes.get(1).kind());
            assertTrue(watcher.scanOnce().isEmpty());
        }
    }

    @Test
    void scanOncePostsModsChangedEvent() throws IOException
    {
        List<ModsChangedEvent> events = new CopyOnWriteArrayList<>();
        ListenerHandle handle = EventBus.GAME.addListener(ModsChangedEvent.class, events::add);
        try (ModWatchService watcher = ModWatchService.manual(message -> { }))
        {
            assertTrue(watcher.start(tempDir, message -> { }));
            Files.writeString(tempDir.resolve("event.jar"), "event");

            assertFalse(watcher.scanOnce().isEmpty());
            assertEquals(1, events.size());
            assertEquals(1, events.get(0).changes().size());
            assertEquals("ADDED", events.get(0).changes().get(0).kind().name());
            assertTrue(events.get(0).restartRequired());

            assertTrue(watcher.scanOnce().isEmpty());
            assertEquals(1, events.size());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void closeIsIdempotentAndStopsScanning() throws IOException
    {
        List<String> logs = new CopyOnWriteArrayList<>();
        ModWatchService watcher = ModWatchService.manual(logs::add);
        assertTrue(watcher.start(tempDir, logs::add));
        Files.writeString(tempDir.resolve("late.jar"), "late");

        watcher.close();
        watcher.close();

        assertTrue(watcher.isClosed());
        assertFalse(watcher.isWatching());
        assertTrue(watcher.scanOnce().isEmpty());
        assertFalse(watcher.start(tempDir));
    }

    @Test
    void missingDirectoryDoesNotThrow()
    {
        Path missing = tempDir.resolve("missing");
        List<String> logs = new CopyOnWriteArrayList<>();
        try (ModWatchService watcher = ModWatchService.manual(logs::add))
        {
            assertTrue(watcher.start(missing, logs::add));
            assertTrue(watcher.scanOnce().isEmpty());
            assertTrue(logs.stream().anyMatch(line -> line.contains("MODS WATCH PENDING")),
                    () -> "missing directory was not logged: " + logs);
        }

        try (ModWatchService unstarted = ModWatchService.manual(message -> { }))
        {
            assertTrue(unstarted.scanOnce().isEmpty());
        }
    }

    @Test
    void watcherThreadDebouncesRapidChanges() throws Exception
    {
        List<String> logs = new CopyOnWriteArrayList<>();
        try (ModWatchService watcher = new ModWatchService(logs::add))
        {
            assertTrue(watcher.start(tempDir, logs::add));

            Files.writeString(tempDir.resolve("one.jar"), "one");
            Files.writeString(tempDir.resolve("two.jar"), "two");

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
            while (summaryLines(logs) == 0 && System.nanoTime() < deadline)
            {
                Thread.sleep(25L);
            }

            assertEquals(1, summaryLines(logs),
                    () -> "rapid changes were not coalesced: " + logs);
            assertEquals(2, changeLines(logs),
                    () -> "expected one report per jar: " + logs);

            Thread.sleep(ModWatchService.DEBOUNCE_MILLIS + 400L);
            assertEquals(1, summaryLines(logs),
                    () -> "an unchanged directory reported again: " + logs);
        }
    }

    private static long summaryLines(List<String> logs)
    {
        return logs.stream()
                .filter(line -> line.startsWith("[weaver] MODS CHANGED files="))
                .count();
    }

    private static long changeLines(List<String> logs)
    {
        return logs.stream()
                .filter(line -> line.startsWith("[weaver] MODS CHANGED path="))
                .count();
    }
}
