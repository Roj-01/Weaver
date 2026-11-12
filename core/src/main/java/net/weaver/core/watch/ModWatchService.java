package net.weaver.core.watch;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.events.ModsChangedEvent;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Watches the mods directory and reports jar changes that need a restart.
 *
 * <p>Mods are discovered once at boot. In the supported {@code SYSTEM} class
 * space, jars are added with
 * {@code Instrumentation.appendToSystemClassLoaderSearch}, which has no remove
 * operation, and already-defined classes cannot be replaced. True hot reload or
 * class swapping is therefore not implemented. This service only observes the
 * mods directory and reports what changed; it never mutates the loaded mod set.
 * Every report is a restart request, not a reload.</p>
 *
 * <p>One {@link WatchService} and one daemon thread watch the directory for
 * {@link StandardWatchEventKinds#ENTRY_CREATE},
 * {@link StandardWatchEventKinds#ENTRY_DELETE}, and
 * {@link StandardWatchEventKinds#ENTRY_MODIFY} events. Events are coalesced
 * with a {@value #DEBOUNCE_MILLIS} millisecond debounce, then the directory is
 * scanned and compared with the previous scan. Only regular {@code .jar} files
 * are considered. Reports are bounded to {@value #MAX_REPORTS} entries per
 * batch; the summary still reports the total count and whether the batch was
 * truncated.</p>
 *
 * <p>The watcher is opt-in. Call {@link #shared()} and
 * {@link #start(Path, Consumer)} when the {@code weaver.mods.watch} system
 * property is {@code true}. {@link #shared()} alone does not start watching
 * because the mods directory is supplied by the agent options.</p>
 *
 * <p>Changes are logged as {@code [weaver] MODS CHANGED ... action=restart-required}
 * and posted as {@link ModsChangedEvent} on {@link EventBus#GAME} with logical
 * side {@link LogicalSide#BOTH}. The watcher thread never lets an exception
 * escape. {@link #close()} is idempotent and safe from any thread.</p>
 *
 * <p>Tests and embedders that need deterministic behavior can bypass the
 * debounce with {@link #scanOnce()}. It compares the directory immediately,
 * returns only changes not reported by a previous scan, and is idempotent for
 * an unchanged directory.</p>
 */
public final class ModWatchService implements AutoCloseable
{
    /** System property that opts the loader integration into watching. */
    public static final String WATCH_PROPERTY = "weaver.mods.watch";
    /** The debounce window applied to watcher-thread events. */
    public static final long DEBOUNCE_MILLIS = 500L;
    /** The maximum number of reports returned or emitted in one batch. */
    public static final int MAX_REPORTS = 256;

    private static final long DEBOUNCE_NANOS = TimeUnit.MILLISECONDS.toNanos(DEBOUNCE_MILLIS);
    private static final long POLL_MILLIS = 50L;
    private static final Object SHARED_LOCK = new Object();

    private static volatile ModWatchService shared;

    private final Consumer<String> defaultLog;
    private final boolean autoStart;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private final Object scanLock = new Object();
    private final Map<Path, Snapshot> known = new LinkedHashMap<>();

    private volatile Consumer<String> log;
    private volatile Path watchDirectory;
    private volatile WatchService watchService;
    private volatile WatchKey watchKey;
    private volatile Thread thread;
    private volatile long dirtySince;
    private volatile boolean missingDirectoryLogged;

    /**
     * Creates a watcher that logs to standard output.
     */
    public ModWatchService()
    {
        this(System.out::println);
    }

    /**
     * Creates a watcher with an explicit diagnostic sink.
     *
     * @param log the log sink, or {@code null} to discard watch diagnostics
     */
    public ModWatchService(Consumer<String> log)
    {
        this(log, true);
    }

    ModWatchService(Consumer<String> log, boolean autoStart)
    {
        this.defaultLog = log == null ? message -> { } : log;
        this.log = this.defaultLog;
        this.autoStart = autoStart;
    }

    /**
     * Creates a watcher that registers a directory and baseline but never
     * starts its daemon thread.
     *
     * <p>This is the deterministic entry point for tests and embedders that
     * poll with {@link #scanOnce()}. The returned instance still validates the
     * directory and records the baseline when {@link #start(Path, Consumer)} is
     * called; it simply never consumes changes on its own.</p>
     *
     * @param log the log sink, or {@code null} to discard watch diagnostics
     * @return a manual watcher, never {@code null}
     */
    public static ModWatchService manual(Consumer<String> log)
    {
        return new ModWatchService(log, false);
    }

    /**
     * Whether the {@value #WATCH_PROPERTY} system property opts into watching.
     *
     * @return {@code true} when the property is {@code true}
     */
    public static boolean isEnabled()
    {
        return Boolean.getBoolean(WATCH_PROPERTY);
    }

    /**
     * Returns the process-wide watcher, creating it on first use.
     *
     * <p>The service is shared because one daemon thread and one watch service
     * are enough for a process. The shared instance does not start until
     * {@link #start(Path, Consumer)} is called. Closing the shared instance
     * clears the shared slot, so a later call creates a fresh service.</p>
     *
     * @return the shared watcher, never {@code null}
     */
    public static ModWatchService shared()
    {
        ModWatchService current = shared;
        if (current != null)
        {
            return current;
        }
        synchronized (SHARED_LOCK)
        {
            if (shared == null)
            {
                shared = new ModWatchService();
            }
            return shared;
        }
    }

    /**
     * Starts watching a mods directory with an explicit diagnostic sink.
     *
     * @param modsDir the mods directory, not {@code null}
     * @param log     the log sink, or {@code null} to keep the current sink
     * @return {@code true} when the directory is now watched by this service
     * @throws NullPointerException when {@code modsDir} is {@code null}
     */
    public boolean start(Path modsDir, Consumer<String> log)
    {
        if (log != null)
        {
            this.log = log;
        }
        return start(modsDir);
    }

    /**
     * Starts watching a mods directory.
     *
     * <p>The call is idempotent for the same directory and does nothing after
     * {@link #close()}. A missing directory is logged as pending and retried by
     * the watcher thread, so a later directory creation is picked up. The
     * current contents are recorded as the baseline and are not reported.
     * Starting a second, different directory is rejected.</p>
     *
     * @param modsDir the mods directory, not {@code null}
     * @return {@code true} when the directory is now watched by this service
     * @throws NullPointerException when {@code modsDir} is {@code null}
     */
    public boolean start(Path modsDir)
    {
        Objects.requireNonNull(modsDir, "modsDir");
        if (closed.get())
        {
            logLine("[weaver] MODS WATCH REJECTED reason=closed");
            return false;
        }
        Path normalized = modsDir.toAbsolutePath().normalize();
        synchronized (lifecycleLock)
        {
            if (closed.get())
            {
                logLine("[weaver] MODS WATCH REJECTED reason=closed");
                return false;
            }
            if (watchDirectory != null)
            {
                if (watchDirectory.equals(normalized))
                {
                    return true;
                }
                logLine("[weaver] MODS WATCH REJECTED dir=" + normalized
                        + " reason=already-watching current=" + watchDirectory);
                return false;
            }
            WatchService service;
            try
            {
                service = FileSystems.getDefault().newWatchService();
            }
            catch (IOException | RuntimeException failure)
            {
                logLine("[weaver] MODS WATCH START FAILED dir=" + normalized
                        + " reason=" + reason(failure));
                return false;
            }
            watchDirectory = normalized;
            watchService = service;
            registerDirectory();
            baseline();
            if (autoStart)
            {
                Thread watcher = new Thread(this::runLoop, "weaver-mod-watch");
                watcher.setDaemon(true);
                thread = watcher;
                watcher.start();
            }
            logLine("[weaver] MODS WATCH STARTED dir=" + normalized
                    + " auto=" + autoStart);
            return true;
        }
    }

    /**
     * Checks the watched directory immediately, bypassing the debounce window.
     *
     * <p>This is the deterministic entry point for tests and explicit polling.
     * The first call after {@link #start(Path)} reports changes made after the
     * baseline; a second call with no further changes returns an empty list.
     * Non-jar files are ignored. A missing directory is not an error and simply
     * reports removals for jars that disappeared.</p>
     *
     * @return the changes in deterministic path order, bounded to
     *         {@value #MAX_REPORTS} entries
     */
    public List<ModChangeReport> scanOnce()
    {
        if (closed.get())
        {
            return List.of();
        }
        ScanResult result;
        synchronized (scanLock)
        {
            result = scan();
        }
        if (!result.changes().isEmpty())
        {
            notifyChanges(result);
        }
        return result.changes();
    }

    /**
     * The directory watched by this service, or {@code null} before a
     * successful {@link #start(Path)}.
     *
     * @return the absolute, normalized directory, or {@code null}
     */
    public Path watchedDirectory()
    {
        return watchDirectory;
    }

    /**
     * Whether a directory is currently watched.
     *
     * @return {@code true} after a successful {@link #start(Path)}
     */
    public boolean isWatching()
    {
        return watchDirectory != null && !closed.get();
    }

    /**
     * Whether this service is closed.
     *
     * @return {@code true} after {@link #close()}
     */
    public boolean isClosed()
    {
        return closed.get();
    }

    /**
     * Stops the watcher thread and releases every resource.
     *
     * <p>The call is idempotent, never throws, and is safe from any thread. If
     * this instance is the process-wide {@link #shared()} watcher, the shared
     * slot is cleared so a later call creates a new service.</p>
     */
    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        synchronized (lifecycleLock)
        {
            WatchKey key = watchKey;
            watchKey = null;
            if (key != null)
            {
                key.cancel();
            }
            WatchService service = watchService;
            watchService = null;
            if (service != null)
            {
                try
                {
                    service.close();
                }
                catch (IOException | RuntimeException failure)
                {
                    logLine("[weaver] MODS WATCH CLOSE FAILED reason=" + reason(failure));
                }
            }
            Thread watcher = thread;
            thread = null;
            if (watcher != null && watcher != Thread.currentThread())
            {
                watcher.interrupt();
                try
                {
                    watcher.join(TimeUnit.SECONDS.toMillis(1L));
                }
                catch (InterruptedException failure)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }
        synchronized (scanLock)
        {
            known.clear();
        }
        synchronized (SHARED_LOCK)
        {
            if (shared == this)
            {
                shared = null;
            }
        }
        logLine("[weaver] MODS WATCH CLOSED");
    }

    private void runLoop()
    {
        while (!closed.get())
        {
            try
            {
                WatchService service = watchService;
                if (service == null)
                {
                    return;
                }
                WatchKey key = service.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (key != null)
                {
                    drain(key);
                }
                if (watchKey == null)
                {
                    registerDirectory();
                }
                long pending = dirtySince;
                if (pending != 0L && System.nanoTime() - pending >= DEBOUNCE_NANOS)
                {
                    dirtySince = 0L;
                    scanOnce();
                }
            }
            catch (InterruptedException failure)
            {
                Thread.currentThread().interrupt();
                return;
            }
            catch (ClosedWatchServiceException failure)
            {
                return;
            }
            catch (Throwable failure)
            {
                logLine("[weaver] MODS WATCH FAILED thread reason=" + reason(failure));
            }
        }
    }

    private void drain(WatchKey key)
    {
        Path directory = key.watchable() instanceof Path path ? path : null;
        for (WatchEvent<?> event : key.pollEvents())
        {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW)
            {
                dirtySince = System.nanoTime();
                logLine("[weaver] MODS WATCH OVERFLOW dir=" + directory);
                continue;
            }
            if (directory == null || !(event.context() instanceof Path relative))
            {
                continue;
            }
            if (!isJar(relative))
            {
                continue;
            }
            dirtySince = System.nanoTime();
        }
        if (!key.reset())
        {
            watchKey = null;
            logLine("[weaver] MODS WATCH KEY INVALID dir=" + directory);
        }
    }

    private void registerDirectory()
    {
        synchronized (lifecycleLock)
        {
            if (closed.get() || watchService == null || watchKey != null)
            {
                return;
            }
            Path directory = watchDirectory;
            if (directory == null || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            {
                if (!missingDirectoryLogged)
                {
                    missingDirectoryLogged = true;
                    logLine("[weaver] MODS WATCH PENDING dir=" + directory
                            + " reason=directory-missing");
                }
                return;
            }
            try
            {
                watchKey = directory.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                missingDirectoryLogged = false;
                logLine("[weaver] MODS WATCH DIRECTORY dir=" + directory);
            }
            catch (ClosedWatchServiceException failure)
            {
                watchKey = null;
            }
            catch (IOException | RuntimeException failure)
            {
                watchKey = null;
                logLine("[weaver] MODS WATCH FAILED dir=" + directory
                        + " reason=" + reason(failure));
            }
        }
    }

    private void baseline()
    {
        synchronized (scanLock)
        {
            known.clear();
            Path directory = watchDirectory;
            if (directory == null
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
            {
                return;
            }
            try
            {
                known.putAll(listJars(directory));
            }
            catch (RuntimeException failure)
            {
                logLine("[weaver] MODS WATCH SCAN FAILED dir=" + directory
                        + " reason=" + reason(failure));
            }
        }
    }

    private ScanResult scan()
    {
        if (closed.get())
        {
            return ScanResult.empty();
        }
        Path directory = watchDirectory;
        if (directory == null)
        {
            return ScanResult.empty();
        }
        Map<Path, Snapshot> current;
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
        {
            current = new LinkedHashMap<>();
        }
        else
        {
            try
            {
                current = listJars(directory);
            }
            catch (RuntimeException failure)
            {
                logLine("[weaver] MODS WATCH SCAN FAILED dir=" + directory
                        + " reason=" + reason(failure));
                return ScanResult.empty();
            }
        }

        Comparator<Path> byName = Comparator.comparing(ModWatchService::fileName);
        TreeSet<Path> all = new TreeSet<>(byName);
        all.addAll(known.keySet());
        all.addAll(current.keySet());

        List<ModChangeReport> changes = new ArrayList<>();
        int total = 0;
        for (Path path : all)
        {
            Snapshot before = known.get(path);
            Snapshot after = current.get(path);
            ModChangeReport.Kind kind;
            Snapshot observed;
            if (before == null && after != null)
            {
                kind = ModChangeReport.Kind.ADDED;
                observed = after;
            }
            else if (before != null && after == null)
            {
                kind = ModChangeReport.Kind.REMOVED;
                observed = before;
            }
            else if (before != null && after != null
                    && (before.size() != after.size()
                    || before.lastModified() != after.lastModified()))
            {
                kind = ModChangeReport.Kind.MODIFIED;
                observed = after;
            }
            else
            {
                continue;
            }
            total++;
            if (changes.size() < MAX_REPORTS)
            {
                changes.add(new ModChangeReport(path, kind, observed.size(),
                        observed.lastModified()));
            }
        }

        known.clear();
        known.putAll(current);
        return new ScanResult(List.copyOf(changes), total, total > changes.size());
    }

    private Map<Path, Snapshot> listJars(Path directory)
    {
        Map<Path, Snapshot> jars = new LinkedHashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory))
        {
            for (Path path : stream)
            {
                if (!isJar(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                {
                    continue;
                }
                Path normalized = path.toAbsolutePath().normalize();
                try
                {
                    jars.put(normalized, new Snapshot(Files.size(normalized),
                            Files.getLastModifiedTime(normalized).toMillis()));
                }
                catch (IOException | RuntimeException failure)
                {
                    logLine("[weaver] MODS WATCH FAILED file=" + normalized
                            + " reason=" + reason(failure));
                }
            }
        }
        catch (IOException | RuntimeException failure)
        {
            throw new IllegalStateException(reason(failure), failure);
        }
        return jars;
    }

    private void notifyChanges(ScanResult result)
    {
        for (ModChangeReport change : result.changes())
        {
            logLine("[weaver] MODS CHANGED path=" + change.path()
                    + " kind=" + change.kind() + " size=" + change.size()
                    + " modified=" + change.lastModified()
                    + " action=restart-required");
        }
        logLine("[weaver] MODS CHANGED files=" + result.total()
                + " emitted=" + result.changes().size()
                + " truncated=" + result.truncated()
                + " action=restart-required");
        try
        {
            List<ModsChangedEvent.Change> eventChanges =
                    new ArrayList<>(result.changes().size());
            for (ModChangeReport change : result.changes())
            {
                eventChanges.add(new ModsChangedEvent.Change(
                        change.path().toString(),
                        toEventKind(change.kind()),
                        change.size(),
                        change.lastModified()));
            }
            ModsChangedEvent event = new ModsChangedEvent(eventChanges);
            event.setLogicalSide(LogicalSide.BOTH);
            EventBus.GAME.post(event);
        }
        catch (Throwable failure)
        {
            logLine("[weaver] MODS WATCH EVENT FAILED reason=" + reason(failure));
        }
    }

    private static ModsChangedEvent.ChangeKind toEventKind(ModChangeReport.Kind kind)
    {
        return switch (kind)
        {
            case ADDED -> ModsChangedEvent.ChangeKind.ADDED;
            case REMOVED -> ModsChangedEvent.ChangeKind.REMOVED;
            case MODIFIED -> ModsChangedEvent.ChangeKind.MODIFIED;
        };
    }

    private static boolean isJar(Path path)
    {
        Path name = path.getFileName();
        return name != null && name.toString().endsWith(".jar");
    }

    private static String fileName(Path path)
    {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    private void logLine(String message)
    {
        try
        {
            log.accept(message);
        }
        catch (RuntimeException ignored)
        {
        }
    }

    private static String reason(Throwable failure)
    {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getName()
                : message;
    }

    private record Snapshot(long size, long lastModified)
    {
    }

    private record ScanResult(List<ModChangeReport> changes, int total, boolean truncated)
    {
        private static ScanResult empty()
        {
            return new ScanResult(List.of(), 0, false);
        }
    }
}
