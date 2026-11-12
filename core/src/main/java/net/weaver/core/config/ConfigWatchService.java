package net.weaver.core.config;

import net.weaver.api.config.ConfigLoadResult;
import net.weaver.api.config.ConfigManager;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Watches configuration files and reloads their managers after external edits.
 *
 * <p>The service owns one {@link WatchService} and one daemon thread. Each
 * registered {@link ConfigManager} contributes the directory of its store's
 * {@link net.weaver.api.config.ConfigStore#location() location}. Only
 * {@link StandardWatchEventKinds#ENTRY_MODIFY} and
 * {@link StandardWatchEventKinds#ENTRY_CREATE} are registered. Events are
 * coalesced with a 250 millisecond debounce so an editor that writes a file in
 * several steps causes at most one reload, and a reload runs only when the
 * store's {@link net.weaver.api.config.ConfigStore#lastModified() change token}
 * differs from the value observed by the previous scan.
 *
 * <p>Automatic wiring is opt-in. {@code ConfigManager.open} registers the
 * manager with {@link #shared()} when the {@code weaver.config.watch} system
 * property is {@code true}; the property defaults to {@code false} so normal
 * boot order and I/O stay deterministic. The API module cannot depend on core,
 * so that registration is reflective and quietly keeps the property ineffective
 * in an API-only deployment.
 *
 * <p>A watcher-driven reload, and the {@code ConfigReloadedEvent} it posts, run
 * on the watcher daemon thread. Listeners that touch game state must marshal to
 * the appropriate game thread. The watcher thread never lets an exception
 * escape. Failures and lifecycle events are logged with
 * {@code [weaver] CONFIG WATCH ...} lines. The default sink is
 * {@link System#out}; construct an instance with a different {@link Consumer}
 * when embedding the service. {@link #close()} is idempotent and is safe to
 * call from any thread.
 *
 * <p>Tests and embedders that need deterministic behavior can bypass the
 * debounce with {@link #scanOnce()}; it checks every tracked file immediately
 * and returns the paths that were reloaded, including a reload the watcher
 * thread already performed.
 */
public final class ConfigWatchService implements AutoCloseable
{
    private static final long DEBOUNCE_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final long POLL_MILLIS = 50L;
    private static final Object SHARED_LOCK = new Object();

    private static volatile ConfigWatchService shared;

    private final Consumer<String> log;
    private final boolean autoStart;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private final Object registrationLock = new Object();
    private final ConcurrentHashMap<Path, WatchEntry> registrations =
            new ConcurrentHashMap<>();

    private volatile WatchService watchService;
    private volatile Thread thread;

    /**
     * Creates a watcher that logs to standard output.
     */
    public ConfigWatchService()
    {
        this(System.out::println);
    }

    /**
     * Creates a watcher with an explicit diagnostic sink.
     *
     * @param log the log sink, or {@code null} to discard watch diagnostics
     */
    public ConfigWatchService(Consumer<String> log)
    {
        this(log, true);
    }

    ConfigWatchService(Consumer<String> log, boolean autoStart)
    {
        this.log = log == null ? message -> { } : log;
        this.autoStart = autoStart;
    }

    /**
     * Returns the process-wide watcher, creating and starting it on first use.
     *
     * <p>The service is shared because one daemon thread and one watch service
     * are enough for every mod configuration in the process. Closing the shared
     * instance clears the shared slot, so a later call creates a fresh service.
     *
     * @return the shared watcher, never {@code null}
     */
    public static ConfigWatchService shared()
    {
        ConfigWatchService current = shared;
        if (current != null)
        {
            return current;
        }
        synchronized (SHARED_LOCK)
        {
            if (shared == null)
            {
                ConfigWatchService created = new ConfigWatchService();
                created.start();
                shared = created;
            }
            return shared;
        }
    }

    /**
     * Starts the watcher thread.
     *
     * <p>The call is idempotent and does nothing after {@link #close()}. A
     * failure to create the platform watch service is logged and leaves the
     * instance usable for explicit {@link #scanOnce()} calls. Watch services
     * are created lazily so constructing the object never performs I/O.
     */
    public void start()
    {
        if (closed.get())
        {
            return;
        }
        synchronized (lifecycleLock)
        {
            if (thread != null || closed.get())
            {
                return;
            }
            WatchService service;
            try
            {
                service = FileSystems.getDefault().newWatchService();
            }
            catch (IOException | RuntimeException failure)
            {
                logLine("[weaver] CONFIG WATCH START FAILED reason=" + reason(failure));
                return;
            }
            watchService = service;
            Thread watcher = new Thread(this::runLoop, "weaver-config-watch");
            watcher.setDaemon(true);
            thread = watcher;
            watcher.start();
            logLine("[weaver] CONFIG WATCH STARTED");
        }
    }

    /**
     * Registers a manager's configuration file for change detection.
     *
     * <p>The parent directory must exist when the file is first created or the
     * registration is retried on later scans and watcher passes. Registering the
     * same manager again is a no-op and preserves the last observed change
     * token. Registering a different manager for the same path replaces the
     * previous registration.
     *
     * <p>The watcher thread is started lazily when needed. {@link #scanOnce()}
     * also works without the thread and reports every reload, including one
     * already performed by the watcher thread.
     *
     * @param manager the manager to reload on change, not {@code null}
     * @return {@code true} when the file is tracked by this service
     * @throws NullPointerException when {@code manager} is {@code null}
     */
    public boolean watch(ConfigManager manager)
    {
        Objects.requireNonNull(manager, "manager");
        if (closed.get())
        {
            logLine("[weaver] CONFIG WATCH REJECTED mod=" + manager.spec().modId()
                    + " reason=closed");
            return false;
        }
        if (autoStart)
        {
            start();
        }
        synchronized (registrationLock)
        {
            if (closed.get())
            {
                logLine("[weaver] CONFIG WATCH REJECTED mod=" + manager.spec().modId()
                        + " reason=closed");
                return false;
            }
            Path file = normalize(manager.store().location());
            WatchEntry existing = registrations.get(file);
            if (existing != null && existing.manager == manager)
            {
                return true;
            }
            Path directory = file.getParent();
            if (directory == null)
            {
                logLine("[weaver] CONFIG WATCH FAILED mod=" + manager.spec().modId()
                        + " file=" + file + " reason=no-parent-directory");
                return false;
            }
            if (existing != null)
            {
                existing.active = false;
                logLine("[weaver] CONFIG WATCH REPLACED mod=" + manager.spec().modId()
                        + " file=" + file);
            }
            long baseline;
            try
            {
                baseline = manager.store().lastModified();
            }
            catch (RuntimeException failure)
            {
                baseline = Long.MIN_VALUE;
                logLine("[weaver] CONFIG WATCH FAILED mod=" + manager.spec().modId()
                        + " file=" + file + " reason=" + reason(failure));
            }
            WatchEntry entry = new WatchEntry(file, directory, manager, baseline);
            registrations.put(file, entry);
            WatchService service = watchService;
            if (service == null)
            {
                logLine("[weaver] CONFIG WATCH PENDING mod=" + manager.spec().modId()
                        + " file=" + file + " reason=watcher-not-started");
            }
            else if (!Files.isDirectory(directory))
            {
                logLine("[weaver] CONFIG WATCH PENDING mod=" + manager.spec().modId()
                        + " file=" + file + " reason=directory-missing dir=" + directory);
            }
            else
            {
                registerKey(entry);
            }
            logLine("[weaver] CONFIG WATCH ADD mod=" + manager.spec().modId()
                    + " file=" + file);
            return true;
        }
    }

    /**
     * Removes a manager's configuration file from change detection.
     *
     * @param manager the manager to stop watching, not {@code null}
     * @return {@code true} when a registration was removed
     * @throws NullPointerException when {@code manager} is {@code null}
     */
    public boolean unwatch(ConfigManager manager)
    {
        Objects.requireNonNull(manager, "manager");
        return unwatch(manager.store().location());
    }

    /**
     * Removes a configuration file from change detection.
     *
     * @param path the configuration file path, not {@code null}
     * @return {@code true} when a registration was removed
     * @throws NullPointerException when {@code path} is {@code null}
     */
    public boolean unwatch(Path path)
    {
        Objects.requireNonNull(path, "path");
        Path file = normalize(path);
        synchronized (registrationLock)
        {
            WatchEntry removed = registrations.remove(file);
            if (removed == null)
            {
                return false;
            }
            removed.active = false;
            releaseKey(removed);
            logLine("[weaver] CONFIG WATCH REMOVED file=" + file);
            return true;
        }
    }

    /**
     * Checks every tracked file immediately, bypassing the debounce window.
     *
     * <p>This is the deterministic entry point for tests and explicit polling.
     * A file is reloaded only when its change token differs from the token
     * observed by the previous scan. The watcher thread uses the same check
     * after its debounce expires. A reload already performed by the watcher
     * thread is reported by the next scan and is not repeated.
     *
     * @return the paths that were reloaded, in registration iteration order
     */
    public List<Path> scanOnce()
    {
        List<Path> reloaded = new ArrayList<>();
        for (WatchEntry entry : registrations.values())
        {
            if (!entry.active)
            {
                continue;
            }
            boolean reloadedNow = reloadEntry(entry, true);
            if (reloadedNow)
            {
                entry.takeReloadedSinceScan();
                reloaded.add(entry.file);
            }
            else if (entry.takeReloadedSinceScan())
            {
                reloaded.add(entry.file);
            }
        }
        return List.copyOf(reloaded);
    }

    /**
     * Whether a manager's configuration file is tracked.
     *
     * @param manager the manager to test, not {@code null}
     * @return {@code true} when the manager's store location is registered
     * @throws NullPointerException when {@code manager} is {@code null}
     */
    public boolean isWatching(ConfigManager manager)
    {
        Objects.requireNonNull(manager, "manager");
        return registrations.containsKey(normalize(manager.store().location()));
    }

    /**
     * The number of tracked configuration files.
     *
     * @return the number of live registrations
     */
    public int watchedCount()
    {
        return registrations.size();
    }

    /**
     * Stops the watcher thread and releases every registration.
     *
     * <p>The call is idempotent, never throws, and is safe from any thread. If
     * this instance is the process-wide {@link #shared()} watcher, the shared
     * slot is cleared so a later call creates a new service.
     */
    @Override
    public void close()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        int count;
        synchronized (registrationLock)
        {
            count = registrations.size();
            for (WatchEntry entry : registrations.values())
            {
                entry.active = false;
                releaseKey(entry);
            }
            registrations.clear();
        }
        synchronized (lifecycleLock)
        {
            Thread watcher = thread;
            thread = null;
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
                    logLine("[weaver] CONFIG WATCH CLOSE FAILED reason=" + reason(failure));
                }
            }
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
        synchronized (SHARED_LOCK)
        {
            if (shared == this)
            {
                shared = null;
            }
        }
        logLine("[weaver] CONFIG WATCH CLOSED entries=" + count);
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
                refreshRegistrations();
                processDue(System.nanoTime());
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
                logLine("[weaver] CONFIG WATCH FAILED thread reason=" + reason(failure));
            }
        }
    }

    private void drain(WatchKey key)
    {
        Path directory = null;
        if (key.watchable() instanceof Path path)
        {
            directory = path;
        }
        for (WatchEvent<?> event : key.pollEvents())
        {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW)
            {
                logLine("[weaver] CONFIG WATCH OVERFLOW dir=" + directory);
                markDirectoryDirty(directory);
                continue;
            }
            if (directory == null)
            {
                continue;
            }
            if (!(event.context() instanceof Path relative))
            {
                continue;
            }
            Path changed = normalize(directory.resolve(relative));
            WatchEntry entry = registrations.get(changed);
            if (entry == null || !entry.active)
            {
                continue;
            }
            if (kind == StandardWatchEventKinds.ENTRY_MODIFY
                    || kind == StandardWatchEventKinds.ENTRY_CREATE)
            {
                entry.markDirty();
                logLine("[weaver] CONFIG WATCH EVENT mod=" + entry.manager.spec().modId()
                        + " file=" + entry.file + " kind=" + kind.name());
            }
        }
        boolean valid = key.reset();
        if (!valid)
        {
            logLine("[weaver] CONFIG WATCH KEY INVALID dir=" + directory);
            invalidateKey(key);
        }
    }

    private void markDirectoryDirty(Path directory)
    {
        if (directory == null)
        {
            return;
        }
        for (WatchEntry entry : registrations.values())
        {
            if (entry.directory.equals(directory))
            {
                entry.markDirty();
            }
        }
    }

    private void invalidateKey(WatchKey key)
    {
        synchronized (registrationLock)
        {
            for (WatchEntry entry : registrations.values())
            {
                if (entry.key == key)
                {
                    entry.key = null;
                }
            }
        }
    }

    private void refreshRegistrations()
    {
        synchronized (registrationLock)
        {
            if (watchService == null || closed.get())
            {
                return;
            }
            for (WatchEntry entry : registrations.values())
            {
                if (entry.active && entry.key == null)
                {
                    registerKey(entry);
                }
            }
        }
    }

    private void registerKey(WatchEntry entry)
    {
        WatchService service = watchService;
        if (service == null || closed.get() || !entry.active || entry.key != null)
        {
            return;
        }
        try
        {
            if (!Files.isDirectory(entry.directory))
            {
                return;
            }
            WatchKey key = entry.directory.register(service,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            entry.key = key;
            try
            {
                if (entry.manager.store().lastModified() != entry.lastModified)
                {
                    entry.markDirty();
                }
            }
            catch (RuntimeException ignored)
            {
            }
            logLine("[weaver] CONFIG WATCH DIRECTORY mod=" + entry.manager.spec().modId()
                    + " file=" + entry.file + " dir=" + entry.directory);
        }
        catch (ClosedWatchServiceException failure)
        {
        }
        catch (IOException | RuntimeException failure)
        {
            logLine("[weaver] CONFIG WATCH FAILED mod=" + entry.manager.spec().modId()
                    + " file=" + entry.file + " reason=" + reason(failure));
        }
    }

    private void processDue(long now)
    {
        for (WatchEntry entry : registrations.values())
        {
            if (entry.active && entry.isDue(now))
            {
                reloadEntry(entry, false);
            }
        }
    }

    private boolean reloadEntry(WatchEntry entry, boolean force)
    {
        synchronized (entry)
        {
            if (!entry.active)
            {
                return false;
            }
            long pending = entry.pendingSince();
            if (!force && pending == 0L)
            {
                return false;
            }
            long current;
            try
            {
                current = entry.manager.store().lastModified();
            }
            catch (RuntimeException failure)
            {
                entry.clearIfPending(pending);
                logLine("[weaver] CONFIG WATCH FAILED mod=" + entry.manager.spec().modId()
                        + " file=" + entry.file + " reason=" + reason(failure));
                return false;
            }
            if (current == entry.lastModified)
            {
                entry.clearIfPending(pending);
                if (!force)
                {
                    logLine("[weaver] CONFIG WATCH UNCHANGED mod="
                            + entry.manager.spec().modId() + " file=" + entry.file);
                }
                return false;
            }
            long previous = entry.lastModified;
            entry.lastModified = current;
            try
            {
                ConfigLoadResult result = entry.manager.reload();
                long after = entry.manager.store().lastModified();
                if (after != current)
                {
                    entry.markDirty();
                    logLine("[weaver] CONFIG WATCH CHANGED DURING RELOAD mod="
                            + entry.manager.spec().modId() + " file=" + entry.file);
                }
                else
                {
                    entry.clearIfPending(pending);
                }
                entry.recordReload();
                logLine("[weaver] CONFIG WATCH RELOADED mod=" + entry.manager.spec().modId()
                        + " file=" + entry.file + " version=" + result.appliedVersion()
                        + " generation=" + entry.manager.generation()
                        + " problems=" + result.problems().size());
                return true;
            }
            catch (RuntimeException failure)
            {
                entry.lastModified = previous;
                entry.clearIfPending(pending);
                logLine("[weaver] CONFIG WATCH RELOAD FAILED mod="
                        + entry.manager.spec().modId() + " file=" + entry.file
                        + " reason=" + reason(failure));
                return false;
            }
        }
    }

    private void releaseKey(WatchEntry entry)
    {
        WatchKey key = entry.key;
        entry.key = null;
        if (key == null)
        {
            return;
        }
        for (WatchEntry other : registrations.values())
        {
            if (other != entry && other.key == key)
            {
                return;
            }
        }
        key.cancel();
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

    private static Path normalize(Path path)
    {
        return path.toAbsolutePath().normalize();
    }

    private static String reason(Throwable failure)
    {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getName()
                : message;
    }

    private static final class WatchEntry
    {
        private final Path file;
        private final Path directory;
        private final ConfigManager manager;
        private volatile long lastModified;
        private volatile long pendingSince;
        private volatile WatchKey key;
        private volatile boolean active = true;
        private volatile boolean reloadedSinceScan;

        private WatchEntry(Path file, Path directory, ConfigManager manager,
                           long lastModified)
        {
            this.file = file;
            this.directory = directory;
            this.manager = manager;
            this.lastModified = lastModified;
        }

        private void markDirty()
        {
            pendingSince = System.nanoTime();
        }

        private void recordReload()
        {
            reloadedSinceScan = true;
        }

        private synchronized boolean takeReloadedSinceScan()
        {
            if (!reloadedSinceScan)
            {
                return false;
            }
            reloadedSinceScan = false;
            return true;
        }

        private long pendingSince()
        {
            return pendingSince;
        }

        private void clearIfPending(long expected)
        {
            if (pendingSince == expected)
            {
                pendingSince = 0L;
            }
        }

        private boolean isDue(long now)
        {
            long since = pendingSince;
            return since != 0L && now - since >= DEBOUNCE_NANOS;
        }
    }
}
