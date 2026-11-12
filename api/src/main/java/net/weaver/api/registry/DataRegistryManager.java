package net.weaver.api.registry;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.registry.event.DataRegistryFreezeEvent;
import net.weaver.api.registry.event.DataRegistryRegisterEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Static manager for {@link DataRegistry} instances.
 *
 * <p>Registries are keyed by their full registry id. Registering the same id
 * twice throws {@link RegistryException}; this is the API policy for shadowing
 * or replacing a dynamic registry. The manager is thread-safe.</p>
 */
public final class DataRegistryManager
{
    /**
     * The summary of applying one or more data pack snapshots.
     *
     * <p>Counts describe individual load results, including one result per tag
     * value. The problem list contains unknown registry ids and other abnormal
     * outcomes such as decode failures, invalid entries, frozen registries, and
     * missing required tag values.</p>
     *
     * @param added accepted new entries and tag values
     * @param replaced entries replaced by a higher-priority value
     * @param skipped entries skipped by a higher-priority value or a frozen
     *                registry
     * @param duplicates entries ignored because the priority was equal
     * @param decodeFailures entries whose codec threw or returned {@code null}
     * @param missing tag values that were not present in the registry
     * @param problems human-readable problems, never {@code null}
     */
    public record ApplyReport(int added, int replaced, int skipped, int duplicates,
                              int decodeFailures, int missing, List<String> problems)
    {
        /** Copies the problem list. */
        public ApplyReport
        {
            Objects.requireNonNull(problems, "problems");
            problems = List.copyOf(problems);
        }
    }

    /**
     * The summary of a {@link #freezeAll()} pass.
     *
     * <p>A freeze is complete when no registry failed. Registries that froze
     * successfully remain frozen even when the report is not complete.</p>
     *
     * @param frozenCount the number of registries that are frozen
     * @param frozenIds the ids of the frozen registries
     * @param failures registry id to failure reason
     */
    public record FreezeReport(int frozenCount, List<String> frozenIds,
                               Map<String, String> failures)
    {
        /** Copies the id list and failure map. */
        public FreezeReport
        {
            Objects.requireNonNull(frozenIds, "frozenIds");
            Objects.requireNonNull(failures, "failures");
            frozenIds = List.copyOf(frozenIds);
            failures = Collections.unmodifiableMap(new LinkedHashMap<>(failures));
        }

        /**
         * Whether every registry froze successfully.
         *
         * @return {@code true} when no failure was recorded
         */
        public boolean isComplete()
        {
            return failures.isEmpty();
        }
    }

    /**
     * The summary of a {@link #reloadAll(List)} pass.
     *
     * <p>Reload applies snapshots only to registries that are not frozen. Every
     * frozen registry is reported by id and is left untouched: frozen
     * semantics are permanent, so there is no unfreeze and no silent
     * generation swap. The embedded {@link ApplyReport} counts only the work
     * performed on open registries. Unknown registry ids are still reported as
     * apply problems.</p>
     *
     * @param apply the apply report for open registries, never {@code null}
     * @param frozenIds the ids of frozen registries that were refused, never
     *                  {@code null}
     * @param openIds the ids of unfrozen registries that were reloadable, in
     *                registration order, never {@code null}
     */
    public record ReloadReport(ApplyReport apply, List<String> frozenIds,
                               List<String> openIds)
    {
        /** Copies the id lists. */
        public ReloadReport
        {
            Objects.requireNonNull(apply, "apply");
            Objects.requireNonNull(frozenIds, "frozenIds");
            Objects.requireNonNull(openIds, "openIds");
            frozenIds = List.copyOf(frozenIds);
            openIds = List.copyOf(openIds);
        }

        /**
         * Whether every registered registry accepted the reload.
         *
         * @return {@code true} when no frozen registry was refused
         */
        public boolean isComplete()
        {
            return frozenIds.isEmpty();
        }

        /**
         * The number of refused frozen registries.
         *
         * @return the frozen registry count
         */
        public int frozenCount()
        {
            return frozenIds.size();
        }

        /**
         * The apply problems plus one refusal line per frozen registry.
         *
         * @return an immutable problem list, never {@code null}
         */
        public List<String> problems()
        {
            if (frozenIds.isEmpty())
            {
                return apply.problems();
            }
            final List<String> problems = new ArrayList<>(apply.problems());
            for (String id : frozenIds)
            {
                problems.add("data registry '" + id + "' is frozen; reload refused");
            }
            return List.copyOf(problems);
        }
    }

    private static final Object LOCK = new Object();
    private static final Map<String, DataRegistry<?>> registries = new LinkedHashMap<>();
    private static boolean registrationPhase;
    /** Registries whose register event was already posted; identity-based. */
    private static final Set<DataRegistry<?>> POSTED =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private DataRegistryManager()
    {
    }

    /**
     * Creates and registers a data registry.
     *
     * @param key the registry key, not {@code null}
     * @param type the entry type, not {@code null}
     * @param codec the entry codec, not {@code null}
     * @param <T> the entry type
     * @return the new registry
     * @throws RegistryException when a registry with the same id is already
     *                           registered
     */
    public static <T> DataRegistry<T> create(RegistryKey<?> key, Class<T> type,
                                             DataRegistryCodec<T> codec)
    {
        Objects.requireNonNull(key, "key");
        final DataRegistry<T> registry = DataRegistry.create(key, type, codec);
        register(registry);
        return registry;
    }

    /**
     * Creates and registers a data registry by id.
     *
     * @param registryId the full {@code namespace:path} registry id, not
     *                   {@code null}
     * @param type the entry type, not {@code null}
     * @param codec the entry codec, not {@code null}
     * @param <T> the entry type
     * @return the new registry
     * @throws IllegalArgumentException when the registry id is invalid
     * @throws RegistryException when a registry with the same id is already
     *                           registered
     */
    public static <T> DataRegistry<T> create(String registryId, Class<T> type,
                                             DataRegistryCodec<T> codec)
    {
        Objects.requireNonNull(registryId, "registryId");
        return create(RegistryKey.parse(registryId), type, codec);
    }

    /**
     * Registers an existing data registry.
     *
     * @param registry the registry to add, not {@code null}
     * @throws RegistryException when a registry with the same id is already
     *                           registered
     */
    public static void register(DataRegistry<?> registry)
    {
        Objects.requireNonNull(registry, "registry");
        synchronized (LOCK)
        {
            if (registries.containsKey(registry.id()))
            {
                throw new RegistryException("Data registry already registered: "
                        + registry.id());
            }
            registries.put(registry.id(), registry);
        }
    }

    /**
     * Finds a registered data registry.
     *
     * @param key the registry key, not {@code null}
     * @return the registry, or an empty optional
     */
    public static Optional<DataRegistry<?>> find(RegistryKey<?> key)
    {
        Objects.requireNonNull(key, "key");
        synchronized (LOCK)
        {
            return Optional.ofNullable(registries.get(key.id()));
        }
    }

    /**
     * Requires a registered data registry.
     *
     * @param key the registry key, not {@code null}
     * @return the registry, never {@code null}
     * @throws RegistryException when the registry is not registered
     */
    public static DataRegistry<?> require(RegistryKey<?> key)
    {
        Objects.requireNonNull(key, "key");
        synchronized (LOCK)
        {
            final DataRegistry<?> registry = registries.get(key.id());
            if (registry == null)
            {
                throw new RegistryException("Unknown data registry: " + key.id());
            }
            return registry;
        }
    }

    /**
     * All registered data registries in registration order.
     *
     * @return an immutable snapshot
     */
    public static List<DataRegistry<?>> all()
    {
        synchronized (LOCK)
        {
            return List.copyOf(registries.values());
        }
    }

    /**
     * The registered data registry ids in registration order.
     *
     * @return an immutable snapshot
     */
    public static List<String> registryIds()
    {
        synchronized (LOCK)
        {
            return List.copyOf(registries.keySet());
        }
    }

    /**
     * Whether a data registry registration phase is open.
     *
     * @return the phase state
     */
    public static boolean isRegistrationPhase()
    {
        synchronized (LOCK)
        {
            return registrationPhase;
        }
    }

    /**
     * Opens the registration phase and posts one
     * {@link DataRegistryRegisterEvent} per registered registry on
     * {@link EventBus#GAME} with logical side {@link LogicalSide#BOTH}.
     *
     * <p>Events are posted without holding the manager lock, so a listener may
     * call back into the manager.</p>
     *
     * <p>Each registry receives its register event at most once. The loader
     * opens the phase during the game's registry bootstrap, and a mod may also
     * open it earlier from {@code onInitialize}; a repeated call only posts for
     * registries created since the previous call.</p>
     */
    public static void beginRegistration()
    {
        final List<DataRegistry<?>> snapshot = new ArrayList<>();
        synchronized (LOCK)
        {
            registrationPhase = true;
            for (DataRegistry<?> registry : registries.values())
            {
                if (POSTED.add(registry))
                {
                    snapshot.add(registry);
                }
            }
        }
        for (DataRegistry<?> registry : snapshot)
        {
            final DataRegistryRegisterEvent event = new DataRegistryRegisterEvent(registry);
            event.setLogicalSide(LogicalSide.BOTH);
            EventBus.GAME.post(event);
        }
    }

    /**
     * Closes the data registry registration phase.
     */
    public static void endRegistration()
    {
        synchronized (LOCK)
        {
            registrationPhase = false;
        }
    }

    /**
     * Applies one snapshot to the registered data registries.
     *
     * <p>Unknown registry ids are recorded as problems instead of throwing.
     * Entries are loaded with the snapshot priority and pack id; tags use the
     * priority and replace flag from each {@link DataPackSnapshot.TagDeclaration}.
     * A failure in one registry does not stop the remaining registries.</p>
     *
     * @param snapshot the snapshot to apply, not {@code null}
     * @return the apply report, never {@code null}
     */
    public static ApplyReport apply(DataPackSnapshot snapshot)
    {
        Objects.requireNonNull(snapshot, "snapshot");
        final Map<String, DataRegistry<?>> current = registriesSnapshot();
        final Counters counters = new Counters();
        for (Map.Entry<String, Map<String, Map<String, Object>>> registryEntry
                : snapshot.entries().entrySet())
        {
            final DataRegistry<?> registry = current.get(registryEntry.getKey());
            if (registry == null)
            {
                counters.problems.add("unknown data registry '" + registryEntry.getKey()
                        + "' in pack '" + snapshot.packId() + "'");
                continue;
            }
            for (Map.Entry<String, Map<String, Object>> entry
                    : registryEntry.getValue().entrySet())
            {
                try
                {
                    counters.count(registry.load(entry.getKey(), entry.getValue(),
                            snapshot.priority(), snapshot.packId()), registry.id());
                }
                catch (Throwable failure)
                {
                    counters.skipped++;
                    counters.problems.add("failed to load entry '" + entry.getKey()
                            + "' into data registry '" + registry.id() + "': "
                            + describe(failure));
                }
            }
        }
        for (Map.Entry<String, Map<String, DataPackSnapshot.TagDeclaration>> registryEntry
                : snapshot.tags().entrySet())
        {
            final DataRegistry<?> registry = current.get(registryEntry.getKey());
            if (registry == null)
            {
                counters.problems.add("unknown data registry '" + registryEntry.getKey()
                        + "' in pack '" + snapshot.packId() + "'");
                continue;
            }
            for (Map.Entry<String, DataPackSnapshot.TagDeclaration> tag
                    : registryEntry.getValue().entrySet())
            {
                final DataPackSnapshot.TagDeclaration declaration = tag.getValue();
                try
                {
                    counters.countAll(registry.loadTag(tag.getKey(), declaration.values(),
                            declaration.priority(), snapshot.packId(),
                            declaration.replace()), registry.id());
                }
                catch (Throwable failure)
                {
                    counters.skipped++;
                    counters.problems.add("failed to load tag '" + tag.getKey()
                            + "' into data registry '" + registry.id() + "': "
                            + describe(failure));
                }
            }
        }
        return counters.toReport();
    }

    /**
     * Applies several snapshots in ascending priority order.
     *
     * <p>The sort is stable, so equal priorities keep their input order. This
     * makes higher-priority pack entries replace lower-priority entries when
     * the snapshots are applied in turn.</p>
     *
     * @param snapshots the snapshots to apply, not {@code null}
     * @return the merged apply report, never {@code null}
     */
    public static ApplyReport loadAll(List<DataPackSnapshot> snapshots)
    {
        Objects.requireNonNull(snapshots, "snapshots");
        final List<DataPackSnapshot> ordered = new ArrayList<>(snapshots.size());
        for (DataPackSnapshot snapshot : snapshots)
        {
            ordered.add(Objects.requireNonNull(snapshot, "snapshot"));
        }
        ordered.sort(Comparator.comparingInt(DataPackSnapshot::priority));
        final Counters counters = new Counters();
        // Every pack's entries are applied before any pack's tags, so a tag in a
        // lower-priority pack can name an entry that only a higher-priority pack
        // defines.
        for (DataPackSnapshot snapshot : ordered)
        {
            counters.add(apply(part(snapshot, true)));
        }
        for (DataPackSnapshot snapshot : ordered)
        {
            counters.add(apply(part(snapshot, false)));
        }
        return counters.toReport();
    }

    private static DataPackSnapshot part(DataPackSnapshot snapshot, boolean entries)
    {
        final DataPackSnapshot.Builder builder =
                DataPackSnapshot.builder(snapshot.packId(), snapshot.priority());
        if (entries)
        {
            for (Map.Entry<String, Map<String, Map<String, Object>>> registryEntry
                    : snapshot.entries().entrySet())
            {
                for (Map.Entry<String, Map<String, Object>> entry
                        : registryEntry.getValue().entrySet())
                {
                    builder.entry(registryEntry.getKey(), entry.getKey(), entry.getValue());
                }
            }
        }
        else
        {
            for (Map.Entry<String, Map<String, DataPackSnapshot.TagDeclaration>> registryEntry
                    : snapshot.tags().entrySet())
            {
                for (Map.Entry<String, DataPackSnapshot.TagDeclaration> tag
                        : registryEntry.getValue().entrySet())
                {
                    builder.tag(registryEntry.getKey(), tag.getKey(), tag.getValue());
                }
            }
        }
        return builder.build();
    }

    /**
     * Reloads data-pack snapshots without touching frozen registries.
     *
     * <p>Frozen registries are permanent by design. This method applies each
     * snapshot only to registries that are not frozen, reports every frozen
     * registry id in the returned {@link ReloadReport}, and never replaces,
     * unfreezes, or silently mutates a frozen registry. There is deliberately
     * no generation swap in this version: creating fresh registries under the
     * same ids would expose writable instances and break the documented
     * permanent-freeze contract.</p>
     *
     * <p>Snapshots are applied in ascending priority order with the same
     * precedence as {@link #loadAll(List)}. Entries and tags that target a
     * frozen registry are filtered out before the apply pass; entries and tags
     * that target an unknown registry are still reported as problems.</p>
     *
     * @param snapshots the snapshots to apply, not {@code null}
     * @return the reload report, never {@code null}
     */
    public static ReloadReport reloadAll(List<DataPackSnapshot> snapshots)
    {
        Objects.requireNonNull(snapshots, "snapshots");
        final Map<String, DataRegistry<?>> current = registriesSnapshot();
        final List<String> frozenIds = new ArrayList<>();
        final List<String> openIds = new ArrayList<>();
        for (DataRegistry<?> registry : current.values())
        {
            if (registry.isFrozen())
            {
                frozenIds.add(registry.id());
            }
            else
            {
                openIds.add(registry.id());
                // Re-applying the packs must replace, not stack: drop pack-loaded
                // tag layers and pack entry priorities before the apply pass.
                registry.resetPackStateForReload();
            }
        }
        final List<DataPackSnapshot> openSnapshots = new ArrayList<>(snapshots.size());
        for (DataPackSnapshot snapshot : snapshots)
        {
            openSnapshots.add(withoutFrozen(Objects.requireNonNull(snapshot, "snapshot"),
                    current));
        }
        return new ReloadReport(loadAll(openSnapshots), frozenIds, openIds);
    }

    private static DataPackSnapshot withoutFrozen(DataPackSnapshot snapshot,
                                                  Map<String, DataRegistry<?>> current)
    {
        final DataPackSnapshot.Builder builder =
                DataPackSnapshot.builder(snapshot.packId(), snapshot.priority());
        for (Map.Entry<String, Map<String, Map<String, Object>>> registryEntry
                : snapshot.entries().entrySet())
        {
            final DataRegistry<?> registry = current.get(registryEntry.getKey());
            if (registry != null && registry.isFrozen())
            {
                continue;
            }
            for (Map.Entry<String, Map<String, Object>> entry
                    : registryEntry.getValue().entrySet())
            {
                builder.entry(registryEntry.getKey(), entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Map<String, DataPackSnapshot.TagDeclaration>> registryEntry
                : snapshot.tags().entrySet())
        {
            final DataRegistry<?> registry = current.get(registryEntry.getKey());
            if (registry != null && registry.isFrozen())
            {
                continue;
            }
            for (Map.Entry<String, DataPackSnapshot.TagDeclaration> tag
                    : registryEntry.getValue().entrySet())
            {
                builder.tag(registryEntry.getKey(), tag.getKey(), tag.getValue());
            }
        }
        return builder.build();
    }

    /**
     * Freezes every registered data registry.
     *
     * <p>Each registry is frozen independently. A failure is recorded instead
     * of thrown. One {@link DataRegistryFreezeEvent} is posted on
     * {@link EventBus#GAME} with logical side {@link LogicalSide#BOTH} after
     * every freeze attempt; the event reports the actual frozen state.</p>
     *
     * @return the freeze report, never {@code null}
     */
    public static FreezeReport freezeAll()
    {
        final List<DataRegistry<?>> snapshot = all();
        final List<String> frozenIds = new ArrayList<>();
        final Map<String, String> failures = new LinkedHashMap<>();
        for (DataRegistry<?> registry : snapshot)
        {
            try
            {
                registry.freeze();
            }
            catch (Throwable failure)
            {
                failures.put(registry.id(), describe(failure));
            }
            try
            {
                final DataRegistryFreezeEvent event = new DataRegistryFreezeEvent(registry);
                event.setLogicalSide(LogicalSide.BOTH);
                EventBus.GAME.post(event);
            }
            catch (Throwable ignored)
            {
            }
            if (registry.isFrozen())
            {
                frozenIds.add(registry.id());
            }
        }
        return new FreezeReport(frozenIds.size(), frozenIds, failures);
    }

    /**
     * Clears all registered data registries and closes the registration phase.
     * Intended for tests.
     */
    public static void reset()
    {
        synchronized (LOCK)
        {
            registries.clear();
            POSTED.clear();
            registrationPhase = false;
        }
    }

    private static Map<String, DataRegistry<?>> registriesSnapshot()
    {
        synchronized (LOCK)
        {
            return new LinkedHashMap<>(registries);
        }
    }

    private static String describe(Throwable failure)
    {
        if (failure == null)
        {
            return "unknown failure";
        }
        final String message = failure.getMessage();
        return message != null ? message : failure.getClass().getName();
    }

    private static final class Counters
    {
        private int added;
        private int replaced;
        private int skipped;
        private int duplicates;
        private int decodeFailures;
        private int missing;
        private final List<String> problems = new ArrayList<>();

        private void count(DataRegistry.LoadResult result, String registryId)
        {
            switch (result.status())
            {
                case ADDED -> added++;
                case REPLACED -> replaced++;
                case SKIPPED -> skipped++;
                case DUPLICATE -> duplicates++;
                case DECODE_FAILED ->
                {
                    decodeFailures++;
                    problems.add("decode failed for '" + result.entryId()
                            + "' in data registry '" + registryId + "': " + result.detail());
                }
                case FROZEN ->
                {
                    skipped++;
                    problems.add("data registry '" + registryId + "' is frozen");
                }
                case INVALID ->
                {
                    skipped++;
                    problems.add("invalid entry '" + result.entryId()
                            + "' in data registry '" + registryId + "': " + result.detail());
                }
                case MISSING -> missing++;
                case REQUIRED_MISSING ->
                {
                    missing++;
                    problems.add("required tag value '" + result.entryId()
                            + "' is missing from data registry '" + registryId + "'");
                }
            }
        }

        private void countAll(List<DataRegistry.LoadResult> results, String registryId)
        {
            for (DataRegistry.LoadResult result : results)
            {
                count(result, registryId);
            }
        }

        private void add(ApplyReport report)
        {
            added += report.added();
            replaced += report.replaced();
            skipped += report.skipped();
            duplicates += report.duplicates();
            decodeFailures += report.decodeFailures();
            missing += report.missing();
            problems.addAll(report.problems());
        }

        private ApplyReport toReport()
        {
            return new ApplyReport(added, replaced, skipped, duplicates, decodeFailures,
                    missing, problems);
        }
    }
}
