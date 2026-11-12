package net.weaver.api.config;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.Side;
import net.weaver.api.event.events.config.ConfigLoadedEvent;
import net.weaver.api.event.events.config.ConfigReloadedEvent;
import net.weaver.api.event.events.config.ConfigSyncedEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Loads, validates, exposes, and saves one mod's configuration.
 *
 * <p>A manager is created with {@link #open(ConfigSpec, ConfigStore, Consumer)}
 * or {@link #open(ConfigSpec, ConfigStore, EventBus, Consumer)}. Opening
 * validates that the spec and store belong to the same {@link Side}, rejects a
 * duplicate open for the same mod id, registers the manager in a process-wide
 * registry keyed by mod id, and then performs the initial load. Registration
 * happens before the load so a {@link ConfigLoadedEvent} listener can call
 * {@link #get(String)} for the mod that is loading.
 *
 * <p>Loading is lenient. A missing file applies every default without writing.
 * Unparseable content, an invalid schema version, a newer schema version, a
 * failed migration, or an unreadable file is logged, reported as a problem,
 * and loaded with defaults; a corrupt file is backed up when possible. An
 * individual invalid value is logged with a {@code CONFIG INVALID} line, kept
 * at its default, and reported, while the remaining values still load. Load
 * never throws for these cases.
 *
 * <p>Hot reload is explicit through {@link #reload()} or opt-in through the
 * core configuration watcher enabled by the {@code weaver.config.watch} system
 * property. A reload repeats the complete load pipeline (read, migrate,
 * validate, default) and publishes a new {@link ConfigReloadedEvent}.
 * Generation {@code 1} is the initial load; every completed reload increments
 * it.
 *
 * <p>Saving is explicit. It validates every value first, merges preserved
 * unknown keys from the loaded file, writes the spec version, and then asks the
 * store to persist atomically. Store failures propagate as
 * {@link ConfigStoreException}. A save is not a multi-key transactional
 * snapshot: values may change while a save is in progress, and concurrent
 * changes are not rolled back. A save never reloads the file and never posts a
 * configuration event.
 *
 * <p>A manager whose spec uses
 * {@link ConfigSyncPolicy#SERVER_TO_CLIENT} may receive a server snapshot
 * through {@link #applySyncedValues(Map, int)}. The transfer is read-only on the
 * client: it reuses the load validation path but never writes the file. The
 * server document wins for every declared key in the snapshot, and undeclared
 * keys are ignored. The values the client had before the first snapshot are
 * kept aside: {@link #save()} writes those (never a server's values), and they
 * come back when the client disconnects.
 *
 * <p>Thread safety: the spec is immutable, each value read is a volatile read,
 * the manager registry is concurrent, load is guarded so it runs exactly once,
 * and load, reload, save, document snapshots, and sync application share the
 * manager's load lock. Opening the same mod id twice is rejected even when the
 * calls race.
 */
public final class ConfigManager
{
    private static final ConcurrentHashMap<String, ConfigManager> REGISTRY =
            new ConcurrentHashMap<>();
    private static final String FILE_KEY = "<file>";
    private static final String CORRUPT_BACKUP_REASON = "corrupt";
    private static final String UNREADABLE_BACKUP_REASON = "unreadable";
    private static final String NEWER_SCHEMA_BACKUP_REASON = "newer-schema";
    private static final String MIGRATION_BACKUP_REASON = "migration-failed";
    private static final String WATCH_PROPERTY = "weaver.config.watch";
    private static final String WATCH_SERVICE_CLASS =
            "net.weaver.core.config.ConfigWatchService";
    private static final String SYNC_CALLER = "net.weaver.core.config.ConfigSync";
    private static final StackWalker STACK_WALKER = StackWalker.getInstance();

    private final ConfigSpec spec;
    private final ConfigStore store;
    private final EventBus eventBus;
    private final Consumer<String> log;
    private final Path file;
    private final Map<Integer, ConfigMigration> migrations;
    private final Object loadLock = new Object();
    private volatile ConfigLoadResult result;
    private volatile ConfigSyncResult syncResult;
    /** The file-backed values before the first server snapshot, restored on disconnect. */
    private Map<String, Object> localBeforeSync;
    private volatile Map<String, Object> preserved = Map.of();
    private volatile int generation;
    /**
     * Backup reason for a file the last load could not use, or {@code null}.
     *
     * <p>Set when the last load fell back to defaults while leaving the
     * existing file in place (read failure, newer schema, failed migration,
     * failed corrupt backup). The next save moves that file aside first so the
     * user's settings are never silently replaced by defaults.
     */
    private String pendingSaveBackup;

    private ConfigManager(ConfigSpec spec, ConfigStore store, EventBus eventBus,
                          Consumer<String> log)
    {
        this.spec = spec;
        this.store = store;
        this.eventBus = eventBus;
        this.log = log;
        this.file = store.location();
        LinkedHashMap<Integer, ConfigMigration> byVersion = new LinkedHashMap<>();
        for (ConfigMigration migration : spec.migrations())
        {
            byVersion.put(migration.fromVersion(), migration);
        }
        this.migrations = Collections.unmodifiableMap(byVersion);
    }

    /**
     * Opens and loads a manager without an event bus.
     *
     * <p>Equivalent to {@link #open(ConfigSpec, ConfigStore, EventBus, Consumer)}
     * with a {@code null} bus.
     *
     * @param spec  the immutable specification, not {@code null}
     * @param store the persistence backend, not {@code null}
     * @param log   the log sink, or {@code null} to discard log lines
     * @return the opened and loaded manager, never {@code null}
     * @throws ConfigException when the sides differ or a manager is already open
     *                         for the spec's mod id
     */
    public static ConfigManager open(ConfigSpec spec, ConfigStore store, Consumer<String> log)
    {
        return open(spec, store, null, log);
    }

    /**
     * Opens and loads a manager.
     *
     * <p>The spec side and the store side must be exactly equal, including
     * {@link Side#BOTH}. A second open for the same mod id is rejected. The
     * manager is registered before loading, then {@link #load()} is called
     * once; the returned manager therefore always has a result.
     *
     * <p>When the {@code weaver.config.watch} system property is {@code true}
     * and the core module is present, the opened manager is also registered
     * with {@code net.weaver.core.config.ConfigWatchService}. The watcher is
     * opt-in so normal boot order and I/O stay deterministic. Because the API
     * module cannot depend on core, this registration is performed
     * reflectively; an API-only deployment simply keeps the property
     * ineffective.
     *
     * @param spec     the immutable specification, not {@code null}
     * @param store    the persistence backend, not {@code null}
     * @param eventBus the bus that receives the one {@link ConfigLoadedEvent},
     *                 or {@code null} to skip posting
     * @param log      the log sink, or {@code null} to discard log lines
     * @return the opened and loaded manager, never {@code null}
     * @throws NullPointerException when {@code spec} or {@code store} is
     *                              {@code null}
     * @throws ConfigException      when the sides differ or a manager is
     *                              already open for the spec's mod id
     */
    public static ConfigManager open(ConfigSpec spec, ConfigStore store, EventBus eventBus,
                                      Consumer<String> log)
    {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(store, "store");
        Side specSide = spec.side();
        Side storeSide = store.side();
        if (specSide != storeSide)
        {
            throw new ConfigException("Config side mismatch for mod '" + spec.modId()
                    + "': spec location mod=" + spec.modId() + " side=" + specSide
                    + " does not match store location file=" + store.location()
                    + " side=" + storeSide);
        }
        ConfigManager manager = new ConfigManager(spec, store, eventBus, log);
        ConfigManager existing = REGISTRY.putIfAbsent(spec.modId(), manager);
        if (existing != null)
        {
            throw new ConfigException("A config manager is already open for mod '"
                    + spec.modId() + "'; use ConfigManager.find('" + spec.modId()
                    + "') to access it");
        }
        manager.load();
        manager.installWatcherIfEnabled();
        return manager;
    }

    /**
     * Finds the manager registered for a mod id.
     *
     * @param modId the mod id, not {@code null}
     * @return the manager, or an empty optional when none is open
     * @throws NullPointerException when {@code modId} is {@code null}
     */
    public static Optional<ConfigManager> find(String modId)
    {
        Objects.requireNonNull(modId, "modId");
        return Optional.ofNullable(REGISTRY.get(modId));
    }

    /**
     * Returns the manager registered for a mod id.
     *
     * @param modId the mod id, not {@code null}
     * @return the manager, never {@code null}
     * @throws NullPointerException when {@code modId} is {@code null}
     * @throws ConfigException      when no manager is open for {@code modId};
     *                              the message lists the open mods
     */
    public static ConfigManager get(String modId)
    {
        Objects.requireNonNull(modId, "modId");
        ConfigManager manager = REGISTRY.get(modId);
        if (manager == null)
        {
            String known = String.join(", ", new TreeSet<>(REGISTRY.keySet()));
            throw new ConfigException("No config manager is open for mod '" + modId
                    + "'; call ConfigManager.open(spec, store, log) during mod initialization"
                    + (known.isEmpty() ? "" : "; open mods: " + known));
        }
        return manager;
    }

    /**
     * Removes a manager from the process-wide registry.
     *
     * <p>This is a test and embedding helper; it is a no-op when the mod id is
     * not registered. It does not unregister event listeners, close files, or
     * invalidate references already held by callers.
     *
     * @param modId the mod id to remove, not {@code null}
     * @throws NullPointerException when {@code modId} is {@code null}
     */
    public static void unregister(String modId)
    {
        REGISTRY.remove(Objects.requireNonNull(modId, "modId"));
    }

    /**
     * An immutable snapshot of every open manager that completed its load.
     *
     * <p>The result is sorted by mod id so sync and diagnostic passes are
     * deterministic. The registry is concurrent, so the snapshot is a
     * point-in-time view; managers opened after the call are not included. The
     * sync layer uses this to enumerate open configurations without requiring a
     * mod callback.</p>
     *
     * @return an immutable list of loaded managers, never {@code null}
     */
    public static List<ConfigManager> loadedManagers()
    {
        List<ConfigManager> loaded = new ArrayList<>();
        for (ConfigManager manager : REGISTRY.values())
        {
            if (manager.isLoaded())
            {
                loaded.add(manager);
            }
        }
        loaded.sort(Comparator.comparing(manager -> manager.spec().modId()));
        return List.copyOf(loaded);
    }

    /**
     * The immutable specification this manager serves.
     *
     * @return the spec, never {@code null}
     */
    public ConfigSpec spec()
    {
        return spec;
    }

    /**
     * The persistence backend this manager uses.
     *
     * @return the store, never {@code null}
     */
    public ConfigStore store()
    {
        return store;
    }

    /**
     * Loads the configuration at most once.
     *
     * <p>The first call reads the store, applies migrations and values, logs
     * the outcome, and posts one {@link ConfigLoadedEvent} when a bus was
     * supplied. Later calls return the same result without I/O and without
     * re-posting. Reentrant calls from a load listener are safe because the
     * result is published before the event is posted.
     *
     * @return the load result, never {@code null}
     */
    public ConfigLoadResult load()
    {
        ConfigLoadResult current = result;
        if (current != null)
        {
            return current;
        }
        synchronized (loadLock)
        {
            if (result != null)
            {
                return result;
            }
            ConfigLoadResult loaded = doLoad(false);
            result = loaded;
            generation = 1;
            postLoaded(loaded);
            return loaded;
        }
    }

    /**
     * Re-reads and reapplies the configuration file.
     *
     * <p>A reload repeats the complete load pipeline: it reads the store again,
     * applies migrations, validates every value, falls back to defaults for
     * missing or invalid values, updates the preserved unknown keys, publishes
     * a new result, and posts a new {@link ConfigReloadedEvent} when a bus was
     * supplied. Corrupt content is handled exactly like the initial load:
     * the manager logs {@code CONFIG CORRUPT}, asks the store to back the file
     * up, applies defaults, and records a file-level problem.
     *
     * <p>The initial load is generation {@code 1}. Every completed reload
     * increments the generation, so the first reload is generation {@code 2}.
     * A reload that falls back to defaults still increments the generation
     * because it publishes a new in-memory state. If reload is somehow called
     * before {@link #load()} completed, it behaves as the first load and uses
     * generation {@code 1}.
     *
     * <p>This method shares the manager's load lock with {@link #load()} and
     * {@link #save()}, so a reload cannot observe a half-finished save and two
     * reloads cannot interleave. The load result is published before the event
     * is posted, so a listener can call {@link #reload()} again safely.
     *
     * <p>The event is posted on the calling thread. A manual reload runs on
     * that thread; a watcher-driven reload runs on the watcher daemon thread,
     * so listeners that touch game state must marshal to the game thread.
     *
     * @return the reload result, never {@code null}
     */
    public ConfigLoadResult reload()
    {
        synchronized (loadLock)
        {
            boolean first = result == null;
            ConfigLoadResult loaded = doLoad(true);
            result = loaded;
            generation = first ? 1 : generation + 1;
            postReloaded(loaded);
            return loaded;
        }
    }

    /**
     * Whether a load attempt has completed.
     *
     * @return {@code true} once {@link #load()} has returned
     */
    public boolean isLoaded()
    {
        return result != null;
    }

    /**
     * The load generation currently applied.
     *
     * <p>The value is {@code 0} before the first load completes, {@code 1}
     * after the initial load, and one higher after every completed reload.
     *
     * @return the current generation
     */
    public int generation()
    {
        return generation;
    }

    /**
     * The result of the most recent load or reload.
     *
     * @return the load result, never {@code null} after opening
     * @throws IllegalStateException when called before the first load completes
     */
    public ConfigLoadResult result()
    {
        ConfigLoadResult current = result;
        if (current == null)
        {
            throw new IllegalStateException("Config for mod '" + spec.modId()
                    + "' has not been loaded yet");
        }
        return current;
    }

    /**
     * Validates and saves the current values.
     *
     * <p>Every declared value is validated before any I/O. An invalid value is
     * logged and raises {@link ConfigValidationException} without calling the
     * store. Preserved unknown keys from the loaded file are written first and
     * the declared values are written last, so declared keys always win. The
     * store is responsible for replacing the file atomically; when it throws,
     * the previous file must remain intact and the failure propagates as a
     * {@link ConfigStoreException}.
     *
     * <p>When the last load or reload fell back to defaults while leaving the
     * existing file in place (a read failure, a newer schema version, a failed
     * or missing migration, or a corrupt file whose backup failed), the first
     * save afterwards first asks the store to back that file up with
     * {@link ConfigStore#backup(String)} (reason {@code unreadable},
     * {@code newer-schema}, {@code migration-failed}, or {@code corrupt}) and
     * logs {@code CONFIG BACKUP}. If that backup fails, the save is aborted with
     * {@code CONFIG BACKUP FAILED ... action=abort-save} and a
     * {@link ConfigStoreException}, and the file is not written.
     *
     * <p>This method shares the manager's load lock with {@link #load()} and
     * {@link #reload()} but is not a transactional snapshot of all keys:
     * values changed on other threads while the save is in progress are not
     * rolled back. A save never reloads the file and never posts a
     * configuration event.
     *
     * @throws ConfigValidationException when a current value is invalid
     * @throws ConfigStoreException      when the store cannot write the document
     */
    public void save()
    {
        synchronized (loadLock)
        {
            for (ConfigValue<?> value : spec.values())
            {
                try
                {
                    value.ensureValid();
                }
                catch (ConfigValidationException failure)
                {
                    logLine("[weaver] CONFIG SAVE INVALID mod=" + id() + " side=" + sideName()
                            + " key=" + value.key() + " reason=" + failure.reason()
                            + " action=abort file=" + file);
                    throw failure;
                }
            }
            // While a server's values are applied, the file keeps the player's own values.
            ConfigDocument document = localBeforeSync != null
                    ? new ConfigDocument(spec.schemaVersion(), localBeforeSync)
                    : buildDocument();
            backupBeforeSave();
            try
            {
                store.write(document);
            }
            catch (ConfigStoreException failure)
            {
                logLine("[weaver] CONFIG SAVE FAILED mod=" + id() + " side=" + sideName()
                        + " file=" + file + " reason=" + reason(failure));
                throw failure;
            }
            logLine("[weaver] CONFIG SAVED mod=" + id() + " side=" + sideName() + " file=" + file
                    + " version=" + spec.schemaVersion()
                    + " values=" + document.values().size());
        }
    }

    /**
     * Creates a snapshot of the current values without writing the file.
     *
     * <p>The snapshot uses the same encode model as {@link #save()}: preserved
     * unknown keys from the last normal load come first and every declared key
     * is written last, so declared keys win. The map is frozen by
     * {@link ConfigDocument}. The call shares the manager's load lock with
     * {@link #load()}, {@link #reload()}, and {@link #save()}, so the snapshot
     * cannot observe a half-finished save.</p>
     *
     * @return an immutable document at the spec's schema version, never
     *         {@code null}
     */
    public ConfigDocument document()
    {
        synchronized (loadLock)
        {
            return buildDocument();
        }
    }

    /**
     * The most recent server-authoritative snapshot applied to this manager.
     *
     * @return the sync result, or an empty optional when no snapshot has been
     *         applied
     */
    public Optional<ConfigSyncResult> syncResult()
    {
        return Optional.ofNullable(syncResult);
    }

    /**
     * Applies a server-authoritative value snapshot without writing the file.
     *
     * <p>This is the client half of
     * {@link ConfigSyncPolicy#SERVER_TO_CLIENT}. The caller must be the core
     * sync layer ({@code net.weaver.core.config.ConfigSync}); a direct call
     * from a mod is rejected with {@link ConfigException}. The manager side may
     * not be {@link Side#SERVER}; {@link Side#CLIENT} and {@link Side#BOTH} are
     * accepted. The snapshot schema version must equal the spec's schema
     * version exactly.</p>
     *
     * <p>Values are applied through the same validation path as
     * {@link #load()} and {@link #reload()}. A declared key that is present but
     * invalid is logged as {@code CONFIG SYNC INVALID}, reset to its default,
     * and reported in the returned result. A declared key that is absent from
     * the snapshot also falls back to its default. Undeclared keys are counted
     * and ignored. The transfer never writes the store, never changes the load
     * generation, and never changes the file load result; the outcome is
     * published separately through {@link #syncResult()} and a
     * {@link ConfigSyncedEvent}.</p>
     *
     * <p>The server document wins for every declared key in the snapshot, so a
     * pending unsaved local edit to a synced key is overwritten. The method
     * shares the manager's load lock with {@link #load()}, {@link #reload()},
     * {@link #save()}, and {@link #document()}.</p>
     *
     * @param values        the encoded values to apply, not {@code null}
     * @param schemaVersion the schema version of the snapshot
     * @return the completed sync result, never {@code null}
     * @throws NullPointerException     when {@code values} is {@code null}
     * @throws ConfigException          when the caller is not the sync layer,
     *                                  the manager side is {@code SERVER}, or
     *                                  the schema version differs
     */
    public ConfigSyncResult applySyncedValues(Map<String, Object> values, int schemaVersion)
    {
        Objects.requireNonNull(values, "values");
        if (!isSyncCaller())
        {
            throw new ConfigException("Config sync values for mod '" + spec.modId()
                    + "' may only be applied by net.weaver.core.config.ConfigSync");
        }
        if (spec.side() == Side.SERVER)
        {
            throw new ConfigException("Config sync values for mod '" + spec.modId()
                    + "' cannot be applied to a SERVER manager; the server is authoritative");
        }
        if (schemaVersion != spec.schemaVersion())
        {
            throw new ConfigException("Config sync schema mismatch for mod '" + spec.modId()
                    + "': received " + schemaVersion + ", expected " + spec.schemaVersion());
        }
        synchronized (loadLock)
        {
            if (localBeforeSync == null)
            {
                localBeforeSync = new java.util.LinkedHashMap<>(buildDocument().values());
            }
            ConfigDocument frozen = new ConfigDocument(schemaVersion, values);
            List<ConfigProblem> problems = new ArrayList<>();
            int applied = applyValues(frozen.values(), problems, true);
            int ignored = 0;
            for (String key : frozen.values().keySet())
            {
                if (!spec.keySet().contains(key))
                {
                    ignored++;
                }
            }
            ConfigSyncResult synced = new ConfigSyncResult(schemaVersion, applied,
                    spec.size() - applied, ignored, problems);
            syncResult = synced;
            logLine("[weaver] CONFIG SYNC APPLIED mod=" + id() + " side=" + sideName()
                    + " version=" + schemaVersion + " applied=" + applied
                    + " defaults=" + (spec.size() - applied) + " ignored=" + ignored
                    + " problems=" + problems.size());
            postSynced(synced);
            return synced;
        }
    }

    /**
     * Restores the values the client had before a server snapshot was applied.
     * Called by the sync layer when the client disconnects; does nothing when no
     * snapshot was applied.
     *
     * @return {@code true} when values were restored
     * @throws ConfigException when the caller is not the sync layer
     */
    public boolean restoreLocalValues()
    {
        if (!isSyncCaller())
        {
            throw new ConfigException("Config sync values for mod '" + spec.modId()
                    + "' may only be restored by net.weaver.core.config.ConfigSync");
        }
        synchronized (loadLock)
        {
            Map<String, Object> local = localBeforeSync;
            if (local == null)
            {
                return false;
            }
            localBeforeSync = null;
            syncResult = null;
            List<ConfigProblem> problems = new ArrayList<>();
            int restored = applyValues(new ConfigDocument(spec.schemaVersion(), local).values(), problems);
            logLine("[weaver] CONFIG SYNC RESTORED mod=" + id() + " side=" + sideName()
                    + " values=" + restored + " problems=" + problems.size());
            return true;
        }
    }

    /**
     * Looks up a declared value by key.
     *
     * @param key the dotted configuration key, not {@code null}
     * @return the value, never {@code null}
     * @throws ConfigException when the key is not declared by this spec
     */
    public ConfigValue<?> value(String key)
    {
        return spec.value(key);
    }

    /**
     * The problems recorded by the load attempt, in discovery order.
     *
     * @return an immutable problem list, never {@code null}; empty before the
     *         first load and when the load had no problems
     */
    public List<ConfigProblem> problems()
    {
        ConfigLoadResult current = result;
        return current == null ? List.of() : current.problems();
    }

    private ConfigLoadResult doLoad(boolean reload)
    {
        pendingSaveBackup = null;
        List<ConfigProblem> problems = new ArrayList<>();
        ConfigDocument document;
        try
        {
            document = store.read();
        }
        catch (ConfigStoreException failure)
        {
            if (failure.corrupt())
            {
                logLine("[weaver] CONFIG CORRUPT mod=" + id() + " side=" + sideName()
                        + " file=" + file + " reason=" + reason(failure));
                Path backup = backupCorrupt();
                if (backup == null)
                {
                    pendingSaveBackup = CORRUPT_BACKUP_REASON;
                }
                applyDefaults();
                preserved = Map.of();
                problems.add(new ConfigProblem(FILE_KEY, reason(failure)));
                return complete(reload, true, 0, spec.schemaVersion(), 0, spec.size(),
                        false, true, backup, problems);
            }
            logLine("[weaver] CONFIG LOAD FAILED mod=" + id() + " side=" + sideName()
                    + " file=" + file + " reason=" + reason(failure) + " action=defaults");
            pendingSaveBackup = UNREADABLE_BACKUP_REASON;
            applyDefaults();
            preserved = Map.of();
            problems.add(new ConfigProblem(FILE_KEY,
                    "Could not read the config file: " + reason(failure)));
            return complete(reload, true, 0, spec.schemaVersion(), 0, spec.size(),
                    false, false, null, problems);
        }
        if (document == null)
        {
            applyDefaults();
            preserved = Map.of();
            return complete(reload, false, 0, spec.schemaVersion(), 0, spec.size(),
                    false, false, null, problems);
        }
        int fileVersion = document.schemaVersion();
        if (fileVersion < 1)
        {
            String reason = "invalid schema version " + fileVersion;
            logLine("[weaver] CONFIG CORRUPT mod=" + id() + " side=" + sideName()
                    + " file=" + file + " reason=" + reason);
            Path backup = backupCorrupt();
            if (backup == null)
            {
                pendingSaveBackup = CORRUPT_BACKUP_REASON;
            }
            applyDefaults();
            preserved = Map.of();
            problems.add(new ConfigProblem(FILE_KEY, reason));
            return complete(reload, true, fileVersion, spec.schemaVersion(), 0, spec.size(),
                    false, true, backup, problems);
        }
        if (fileVersion > spec.schemaVersion())
        {
            logLine("[weaver] CONFIG SCHEMA NEWER mod=" + id() + " side=" + sideName()
                    + " file=" + file + " version=" + fileVersion + " supported="
                    + spec.schemaVersion() + " action=defaults file-untouched");
            pendingSaveBackup = NEWER_SCHEMA_BACKUP_REASON;
            applyDefaults();
            preserved = Map.of();
            problems.add(new ConfigProblem(FILE_KEY, "File schema version " + fileVersion
                    + " is newer than supported version " + spec.schemaVersion()));
            return complete(reload, true, fileVersion, spec.schemaVersion(), 0, spec.size(),
                    false, false, null, problems);
        }
        Map<String, Object> raw = new LinkedHashMap<>(document.values());
        boolean migrated = false;
        if (fileVersion < spec.schemaVersion())
        {
            raw = migrate(fileVersion, raw, problems);
            if (raw == null)
            {
                pendingSaveBackup = MIGRATION_BACKUP_REASON;
                applyDefaults();
                preserved = Map.of();
                return complete(reload, true, fileVersion, spec.schemaVersion(), 0, spec.size(),
                        false, false, null, problems);
            }
            migrated = true;
        }
        preserved = unknownKeys(raw);
        int applied = applyValues(raw, problems);
        return complete(reload, true, fileVersion, spec.schemaVersion(), applied,
                spec.size() - applied, migrated, false, null, problems);
    }

    private ConfigDocument buildDocument()
    {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>(preserved);
        for (ConfigValue<?> value : spec.values())
        {
            merged.put(value.key(), value.encode());
        }
        return new ConfigDocument(spec.schemaVersion(), merged);
    }

    private static boolean isSyncCaller()
    {
        return STACK_WALKER.walk(frames -> frames
                .skip(2)
                .findFirst()
                .map(frame -> SYNC_CALLER.equals(frame.getClassName()))
                .orElse(false));
    }

    /**
     * Moves aside a file the last load could not use before it is replaced.
     *
     * <p>Runs at most once per fallback load. A backup failure aborts the save
     * so the user's file is never overwritten with defaults.
     */
    private void backupBeforeSave()
    {
        String reason = pendingSaveBackup;
        if (reason == null)
        {
            return;
        }
        Path backup;
        try
        {
            backup = store.backup(reason);
        }
        catch (RuntimeException failure)
        {
            logLine("[weaver] CONFIG BACKUP FAILED mod=" + id() + " side=" + sideName()
                    + " file=" + file + " reason=" + reason(failure) + " action=abort-save");
            logLine("[weaver] CONFIG SAVE FAILED mod=" + id() + " side=" + sideName()
                    + " file=" + file + " reason=" + reason(failure));
            if (failure instanceof ConfigStoreException storeFailure)
            {
                throw storeFailure;
            }
            throw ConfigStoreException.io("cannot back up config " + file
                    + " before saving", failure);
        }
        pendingSaveBackup = null;
        if (backup != null)
        {
            logLine("[weaver] CONFIG BACKUP mod=" + id() + " side=" + sideName()
                    + " file=" + file + " backup=" + backup);
        }
    }

    private Path backupCorrupt()
    {
        try
        {
            Path backup = store.backup(CORRUPT_BACKUP_REASON);
            logLine("[weaver] CONFIG BACKUP mod=" + id() + " side=" + sideName()
                    + " file=" + file + " backup=" + backup);
            return backup;
        }
        catch (RuntimeException failure)
        {
            logLine("[weaver] CONFIG BACKUP FAILED mod=" + id() + " side=" + sideName()
                    + " file=" + file + " reason=" + reason(failure));
            return null;
        }
    }

    private Map<String, Object> migrate(int fromVersion, Map<String, Object> raw,
                                        List<ConfigProblem> problems)
    {
        ConfigData data = new ConfigData(fromVersion, raw);
        for (int version = fromVersion; version < spec.schemaVersion(); version++)
        {
            ConfigMigration migration = migrations.get(version);
            if (migration == null)
            {
                logLine("[weaver] CONFIG MIGRATION MISSING mod=" + id() + " side=" + sideName()
                        + " file=" + file + " from=" + version + " to=" + (version + 1)
                        + " action=defaults file-untouched");
                problems.add(new ConfigProblem(FILE_KEY, "No config migration from schema version "
                        + version + " to " + (version + 1)));
                return null;
            }
            try
            {
                migration.migrate(data);
            }
            catch (RuntimeException failure)
            {
                logLine("[weaver] CONFIG MIGRATION FAILED mod=" + id() + " side=" + sideName()
                        + " file=" + file + " from=" + version + " to=" + (version + 1)
                        + " reason=" + reason(failure) + " action=defaults file-untouched");
                problems.add(new ConfigProblem(FILE_KEY, "Config migration from schema version "
                        + version + " to " + (version + 1) + " failed: " + reason(failure)));
                return null;
            }
            data = new ConfigData(version + 1, data.values());
        }
        return new LinkedHashMap<>(data.values());
    }

    private Map<String, Object> unknownKeys(Map<String, Object> raw)
    {
        LinkedHashMap<String, Object> unknown = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet())
        {
            if (!spec.keySet().contains(entry.getKey()))
            {
                unknown.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(unknown);
    }

    private int applyValues(Map<String, Object> raw, List<ConfigProblem> problems)
    {
        return applyValues(raw, problems, false);
    }

    private int applyValues(Map<String, Object> raw, List<ConfigProblem> problems,
                            boolean synced)
    {
        int applied = 0;
        for (ConfigValue<?> value : spec.values())
        {
            if (!raw.containsKey(value.key()))
            {
                value.resetToDefault();
                continue;
            }
            try
            {
                value.applyRaw(raw.get(value.key()));
                applied++;
            }
            catch (ConfigValidationException failure)
            {
                String action = synced
                        ? "[weaver] CONFIG SYNC INVALID mod=" + id() + " side=" + sideName()
                                + " key=" + value.key() + " reason=" + failure.reason()
                                + " action=default"
                        : "[weaver] CONFIG INVALID mod=" + id() + " side=" + sideName()
                                + " key=" + value.key() + " reason=" + failure.reason()
                                + " action=default file=" + file;
                logLine(action);
                value.resetToDefault();
                problems.add(new ConfigProblem(value.key(), failure.reason()));
            }
        }
        return applied;
    }

    private int applyDefaults()
    {
        for (ConfigValue<?> value : spec.values())
        {
            value.resetToDefault();
        }
        return spec.size();
    }

    private ConfigLoadResult complete(boolean reload, boolean filePresent, int fileVersion,
                                      int appliedVersion, int appliedValues, int defaultedValues,
                                      boolean migrated, boolean corrupt, Path backup,
                                      List<ConfigProblem> problems)
    {
        ConfigLoadResult loaded = new ConfigLoadResult(filePresent, fileVersion, appliedVersion,
                appliedValues, defaultedValues, migrated, corrupt, backup, problems);
        String action = reload ? "RELOADED" : "LOADED";
        logLine("[weaver] CONFIG " + action + " mod=" + id() + " side=" + sideName()
                + " file=" + file + " version=" + appliedVersion + " applied=" + appliedValues
                + " defaults=" + defaultedValues + " problems=" + loaded.problems().size());
        return loaded;
    }

    private void postLoaded(ConfigLoadResult loaded)
    {
        if (eventBus == null)
        {
            return;
        }
        eventBus.post(new ConfigLoadedEvent(spec, loaded));
    }

    private void postReloaded(ConfigLoadResult loaded)
    {
        if (eventBus == null)
        {
            return;
        }
        eventBus.post(new ConfigReloadedEvent(spec, loaded, generation));
    }

    private void postSynced(ConfigSyncResult synced)
    {
        if (eventBus == null)
        {
            return;
        }
        eventBus.post(new ConfigSyncedEvent(spec, synced));
    }

    private void installWatcherIfEnabled()
    {
        if (!Boolean.getBoolean(WATCH_PROPERTY))
        {
            return;
        }
        try
        {
            Class<?> watcherType = Class.forName(WATCH_SERVICE_CLASS, true,
                    ConfigManager.class.getClassLoader());
            Object watcher = watcherType.getMethod("shared").invoke(null);
            watcherType.getMethod("watch", ConfigManager.class).invoke(watcher, this);
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException failure)
        {
            logLine("[weaver] CONFIG WATCH UNAVAILABLE mod=" + id() + " side=" + sideName()
                    + " file=" + file + " reason=" + reason(failure));
        }
    }

    private String id()
    {
        return spec.modId();
    }

    private String sideName()
    {
        return spec.side().name();
    }

    private void logLine(String message)
    {
        if (log == null)
        {
            return;
        }
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
        return message != null ? message : failure.getClass().getName();
    }
}
