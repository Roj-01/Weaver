package net.weaver.minimap.waypoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates the in-memory waypoint list with its persistent store.
 *
 * <p>The list keeps insertion order unless a caller explicitly sorts it.
 * Mutating methods do not write to disk; callers invoke {@link #save()} when
 * changes should be persisted. Instances are not thread-safe.</p>
 *
 * <p>Each manager persists one world's waypoints; the dimension remains a
 * field on every waypoint so a single file can hold all dimensions of that
 * world. Use {@link #forWorld(Path)} to create the conventional
 * {@code waypoints.json} store inside a world directory.</p>
 */
public final class WaypointManager
{
    /** The default ARGB color used for death waypoints. */
    public static final int DEFAULT_DEATH_COLOR = 0xFFFF5C6C;

    /** The display name used for generated death waypoints. */
    public static final String DEATH_WAYPOINT_NAME = "Death";

    /** The icon id used for generated death waypoints. */
    public static final String DEATH_WAYPOINT_ICON = "skull";

    private final WaypointStore store;
    private final List<Waypoint> waypoints = new ArrayList<>();

    /**
     * Creates a manager backed by a store.
     *
     * @param store the waypoint store, never {@code null}
     * @throws NullPointerException when {@code store} is {@code null}
     */
    public WaypointManager(WaypointStore store)
    {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Creates a manager for the conventional waypoint file in a world
     * directory. Loading is still explicit.
     *
     * @param worldDirectory the per-world data directory, never {@code null}
     * @return a manager backed by {@code waypoints.json} in that directory
     * @throws NullPointerException when {@code worldDirectory} is {@code null}
     */
    public static WaypointManager forWorld(Path worldDirectory)
    {
        Objects.requireNonNull(worldDirectory, "worldDirectory");
        return new WaypointManager(new WaypointStore(worldDirectory.resolve("waypoints.json")));
    }

    /** The backing store, never {@code null}. */
    public WaypointStore store()
    {
        return store;
    }

    /**
     * Loads waypoints from the store, replacing the in-memory list.
     *
     * <p>The current list is replaced only after the store loads
     * successfully.</p>
     *
     * @throws IOException when the store cannot be read
     */
    public void load() throws IOException
    {
        List<Waypoint> loaded = store.load();
        waypoints.clear();
        waypoints.addAll(loaded);
    }

    /**
     * Returns an unmodifiable copy of all waypoints in insertion order.
     *
     * @return an unmodifiable list, never {@code null}
     */
    public List<Waypoint> all()
    {
        return List.copyOf(waypoints);
    }

    /** Returns the number of waypoints. */
    public int count()
    {
        return waypoints.size();
    }

    /** Returns whether the manager holds no waypoints. */
    public boolean isEmpty()
    {
        return waypoints.isEmpty();
    }

    /** Removes every waypoint from memory; the store is untouched. */
    public void clear()
    {
        waypoints.clear();
    }

    /**
     * Returns a waypoint by identifier.
     *
     * @param id the identifier, never {@code null}
     * @return the waypoint when present
     * @throws NullPointerException when {@code id} is {@code null}
     */
    public Optional<Waypoint> get(UUID id)
    {
        Objects.requireNonNull(id, "id");
        for (Waypoint waypoint : waypoints)
        {
            if (waypoint.id().equals(id))
            {
                return Optional.of(waypoint);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns whether a waypoint with an identifier exists.
     *
     * @param id the identifier, never {@code null}
     * @return {@code true} when present
     * @throws NullPointerException when {@code id} is {@code null}
     */
    public boolean contains(UUID id)
    {
        return get(id).isPresent();
    }

    /**
     * Returns the waypoints in one dimension, in insertion order.
     *
     * @param dimension the dimension identifier, never {@code null}
     * @return an unmodifiable filtered list, never {@code null}
     * @throws NullPointerException when {@code dimension} is {@code null}
     */
    public List<Waypoint> inDimension(String dimension)
    {
        Objects.requireNonNull(dimension, "dimension");
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : waypoints)
        {
            if (waypoint.dimension().equals(dimension))
            {
                result.add(waypoint);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Creates and appends a visible waypoint.
     *
     * @param name      the display name, never {@code null} or blank
     * @param dimension the dimension identifier, never {@code null} or blank
     * @param x         the block X coordinate
     * @param y         the block Y coordinate
     * @param z         the block Z coordinate
     * @param color     the packed color value
     * @return the created waypoint, never {@code null}
     * @throws NullPointerException     when {@code name} or {@code dimension}
     *                                  is {@code null}
     * @throws IllegalArgumentException when {@code name} or {@code dimension}
     *                                  is blank
     */
    public Waypoint add(String name, String dimension, int x, int y, int z, int color)
    {
        return add(Waypoint.create(name, dimension, x, y, z, color));
    }

    /**
     * Appends an existing waypoint.
     *
     * @param waypoint the waypoint to append, never {@code null}
     * @return the same waypoint, never {@code null}
     * @throws NullPointerException when {@code waypoint} is {@code null}
     */
    public Waypoint add(Waypoint waypoint)
    {
        Objects.requireNonNull(waypoint, "waypoint");
        waypoints.add(waypoint);
        return waypoint;
    }

    /**
     * Replaces a waypoint with the same identifier.
     *
     * <p>A zero update timestamp is replaced with the current time so
     * callers can construct records without timestamps.</p>
     *
     * @param waypoint the replacement, never {@code null}
     * @return {@code true} when a waypoint with the same identifier existed
     * @throws NullPointerException when {@code waypoint} is {@code null}
     */
    public boolean update(Waypoint waypoint)
    {
        Objects.requireNonNull(waypoint, "waypoint");
        int index = indexOf(waypoint.id());
        if (index < 0)
        {
            return false;
        }
        Waypoint replacement = waypoint.updatedAt() > 0L
                ? waypoint
                : waypoint.withUpdatedAt(System.currentTimeMillis());
        waypoints.set(index, replacement);
        return true;
    }

    /**
     * Removes the first waypoint with an identifier.
     *
     * @param id the identifier, never {@code null}
     * @return {@code true} when a waypoint was removed
     * @throws NullPointerException when {@code id} is {@code null}
     */
    public boolean remove(UUID id)
    {
        Objects.requireNonNull(id, "id");
        return waypoints.removeIf(waypoint -> waypoint.id().equals(id));
    }

    /**
     * Moves a waypoint to a new block position.
     *
     * @param id the identifier, never {@code null}
     * @param x  the new block X
     * @param y  the new block Y
     * @param z  the new block Z
     * @return the updated waypoint when it exists
     * @throws NullPointerException when {@code id} is {@code null}
     */
    public Optional<Waypoint> move(UUID id, int x, int y, int z)
    {
        Objects.requireNonNull(id, "id");
        int index = indexOf(id);
        if (index < 0)
        {
            return Optional.empty();
        }
        Waypoint moved = waypoints.get(index).withPosition(x, y, z);
        waypoints.set(index, moved);
        return Optional.of(moved);
    }

    /**
     * Sets the visibility of the first waypoint with an identifier.
     *
     * @param id      the identifier, never {@code null}
     * @param visible the new visibility
     * @return {@code true} when a waypoint was found and updated
     * @throws NullPointerException when {@code id} is {@code null}
     */
    public boolean setVisible(UUID id, boolean visible)
    {
        Objects.requireNonNull(id, "id");
        int index = indexOf(id);
        if (index < 0)
        {
            return false;
        }
        waypoints.set(index, waypoints.get(index).withVisible(visible));
        return true;
    }

    /**
     * Toggles the visibility of the first waypoint with an identifier.
     *
     * @param id the identifier, never {@code null}
     * @return {@code true} when a waypoint was found and updated
     * @throws NullPointerException when {@code id} is {@code null}
     */
    public boolean toggleVisible(UUID id)
    {
        Objects.requireNonNull(id, "id");
        int index = indexOf(id);
        if (index < 0)
        {
            return false;
        }
        Waypoint waypoint = waypoints.get(index);
        waypoints.set(index, waypoint.withVisible(!waypoint.visible()));
        return true;
    }

    /**
     * Sets the group of the first waypoint with an identifier.
     *
     * @param id    the identifier, never {@code null}
     * @param group the new group; blank means ungrouped
     * @return {@code true} when a waypoint was found and updated
     * @throws NullPointerException when {@code id} or {@code group} is {@code null}
     */
    public boolean setGroup(UUID id, String group)
    {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(group, "group");
        int index = indexOf(id);
        if (index < 0)
        {
            return false;
        }
        waypoints.set(index, waypoints.get(index).withGroup(group));
        return true;
    }

    /**
     * Returns the distinct non-blank group names, sorted case-insensitively.
     *
     * @return an unmodifiable list, never {@code null}
     */
    public List<String> groups()
    {
        List<String> groups = new ArrayList<>();
        for (Waypoint waypoint : waypoints)
        {
            String group = waypoint.group();
            if (!group.isBlank() && !groups.contains(group))
            {
                groups.add(group);
            }
        }
        groups.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(groups);
    }

    /**
     * Returns the waypoints in a group, in insertion order.
     *
     * @param group the group name, never {@code null}
     * @return an unmodifiable filtered list, never {@code null}
     * @throws NullPointerException when {@code group} is {@code null}
     */
    public List<Waypoint> inGroup(String group)
    {
        Objects.requireNonNull(group, "group");
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : waypoints)
        {
            if (waypoint.group().equals(group))
            {
                result.add(waypoint);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Renames a group across all waypoints.
     *
     * @param from the current group; blank means the ungrouped set
     * @param to   the new group; blank means ungrouped
     * @return the number of waypoints updated
     * @throws NullPointerException when {@code from} or {@code to} is {@code null}
     */
    public int renameGroup(String from, String to)
    {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to))
        {
            return 0;
        }
        int changed = 0;
        for (int index = 0; index < waypoints.size(); index++)
        {
            Waypoint waypoint = waypoints.get(index);
            if (waypoint.group().equals(from))
            {
                waypoints.set(index, waypoint.withGroup(to));
                changed++;
            }
        }
        return changed;
    }

    /**
     * Searches names, groups, dimensions, and icons case-insensitively.
     *
     * @param query the search text, never {@code null}; blank matches all
     * @return an unmodifiable list in insertion order, never {@code null}
     * @throws NullPointerException when {@code query} is {@code null}
     */
    public List<Waypoint> search(String query)
    {
        Objects.requireNonNull(query, "query");
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty())
        {
            return all();
        }
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : waypoints)
        {
            if (waypoint.name().toLowerCase(Locale.ROOT).contains(needle)
                    || waypoint.group().toLowerCase(Locale.ROOT).contains(needle)
                    || waypoint.dimension().toLowerCase(Locale.ROOT).contains(needle)
                    || waypoint.icon().toLowerCase(Locale.ROOT).contains(needle))
            {
                result.add(waypoint);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Sort keys supported by {@link #sorted(SortKey, double, double, boolean)}.
     */
    public enum SortKey
    {
        NAME,
        DISTANCE,
        COLOR,
        CREATED,
        UPDATED
    }

    /**
     * Sorts waypoints around the world origin.
     *
     * @param key       the sort key, never {@code null}
     * @param ascending whether the order is ascending
     * @return an unmodifiable sorted list, never {@code null}
     * @throws NullPointerException when {@code key} is {@code null}
     */
    public List<Waypoint> sorted(SortKey key, boolean ascending)
    {
        return sorted(key, 0.0D, 0.0D, ascending);
    }

    /**
     * Sorts waypoints, using a horizontal origin for distance sorting.
     *
     * @param key       the sort key, never {@code null}
     * @param originX   the origin block X for {@link SortKey#DISTANCE}
     * @param originZ   the origin block Z for {@link SortKey#DISTANCE}
     * @param ascending whether the order is ascending
     * @return an unmodifiable sorted list, never {@code null}
     * @throws NullPointerException when {@code key} is {@code null}
     */
    public List<Waypoint> sorted(SortKey key, double originX, double originZ, boolean ascending)
    {
        Objects.requireNonNull(key, "key");
        List<Waypoint> result = new ArrayList<>(waypoints);
        Comparator<Waypoint> comparator = switch (key)
        {
            case NAME -> Comparator.comparing(
                    (Waypoint waypoint) -> waypoint.name().toLowerCase(Locale.ROOT))
                    .thenComparing(waypoint -> waypoint.id().toString());
            case DISTANCE -> Comparator.comparingDouble(
                    waypoint -> distanceSquared(waypoint, originX, originZ));
            case COLOR -> (first, second) -> Integer.compareUnsigned(
                    first.color(), second.color());
            case CREATED -> Comparator.comparingLong(Waypoint::createdAt);
            case UPDATED -> Comparator.comparingLong(Waypoint::updatedAt);
        };
        if (!ascending)
        {
            comparator = comparator.reversed();
        }
        result.sort(comparator);
        return List.copyOf(result);
    }

    /** Sorts by name case-insensitively. */
    public List<Waypoint> sortedByName(boolean ascending)
    {
        return sorted(SortKey.NAME, ascending);
    }

    /** Sorts by unsigned color value. */
    public List<Waypoint> sortedByColor(boolean ascending)
    {
        return sorted(SortKey.COLOR, ascending);
    }

    /** Sorts by horizontal distance from an origin. */
    public List<Waypoint> sortedByDistance(double originX, double originZ, boolean ascending)
    {
        return sorted(SortKey.DISTANCE, originX, originZ, ascending);
    }

    /**
     * Removes duplicates by identifier and by visible identity.
     *
     * <p>Identifier duplicates keep the entry with the newest update time.
     * Identity duplicates (same name, dimension, coordinates, color, icon, and
     * group) also keep the newest entry. Removed entries are not recoverable;
     * save afterwards to persist the result.</p>
     *
     * @return the number of waypoints removed
     */
    public int dedupe()
    {
        int before = waypoints.size();
        List<Waypoint> byId = new ArrayList<>();
        Map<UUID, Integer> ids = new HashMap<>();
        for (Waypoint waypoint : waypoints)
        {
            Integer index = ids.get(waypoint.id());
            if (index == null)
            {
                ids.put(waypoint.id(), byId.size());
                byId.add(waypoint);
            }
            else if (waypoint.updatedAt() > byId.get(index).updatedAt())
            {
                byId.set(index, waypoint);
            }
        }
        List<Waypoint> unique = new ArrayList<>();
        Map<String, Integer> signatures = new HashMap<>();
        for (Waypoint waypoint : byId)
        {
            String signature = signature(waypoint);
            Integer index = signatures.get(signature);
            if (index == null)
            {
                signatures.put(signature, unique.size());
                unique.add(waypoint);
            }
            else if (waypoint.updatedAt() > unique.get(index).updatedAt())
            {
                unique.set(index, waypoint);
            }
        }
        waypoints.clear();
        waypoints.addAll(unique);
        return before - unique.size();
    }

    /**
     * Returns the temporary waypoints.
     *
     * @return an unmodifiable list, never {@code null}
     */
    public List<Waypoint> temporary()
    {
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : waypoints)
        {
            if (waypoint.temporary())
            {
                result.add(waypoint);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Removes temporary waypoints that have outlived a time-to-live.
     *
     * @param nowEpochMillis the current time in epoch milliseconds
     * @param ttlMillis      the time-to-live in milliseconds
     * @return the number of waypoints removed
     */
    public int expireTemporary(long nowEpochMillis, long ttlMillis)
    {
        int before = waypoints.size();
        waypoints.removeIf(waypoint -> waypoint.expired(nowEpochMillis, ttlMillis));
        return before - waypoints.size();
    }

    /**
     * Removes temporary waypoints older than a number of minutes.
     *
     * @param minutes the time-to-live in minutes; zero or negative does nothing
     * @return the number of waypoints removed
     */
    public int expireTemporaryMinutes(int minutes)
    {
        if (minutes <= 0)
        {
            return 0;
        }
        long ttl = minutes > Long.MAX_VALUE / 60_000L
                ? Long.MAX_VALUE : minutes * 60_000L;
        return expireTemporary(System.currentTimeMillis(), ttl);
    }

    /**
     * Returns the death waypoints.
     *
     * @return an unmodifiable list, never {@code null}
     */
    public List<Waypoint> deathWaypoints()
    {
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint waypoint : waypoints)
        {
            if (waypoint.death())
            {
                result.add(waypoint);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Creates or replaces the single death waypoint of a dimension.
     *
     * @param dimension the dimension identifier, never {@code null} or blank
     * @param x         the block X coordinate
     * @param y         the block Y coordinate
     * @param z         the block Z coordinate
     * @return the new death waypoint, never {@code null}
     * @throws NullPointerException     when {@code dimension} is {@code null}
     * @throws IllegalArgumentException when {@code dimension} is blank
     */
    public Waypoint setDeathWaypoint(String dimension, int x, int y, int z)
    {
        return setDeathWaypoint(dimension, x, y, z, DEFAULT_DEATH_COLOR);
    }

    /**
     * Creates or replaces the single death waypoint of a dimension.
     *
     * @param dimension the dimension identifier, never {@code null} or blank
     * @param x         the block X coordinate
     * @param y         the block Y coordinate
     * @param z         the block Z coordinate
     * @param color     the packed ARGB color
     * @return the new death waypoint, never {@code null}
     * @throws NullPointerException     when {@code dimension} is {@code null}
     * @throws IllegalArgumentException when {@code dimension} is blank
     */
    public Waypoint setDeathWaypoint(String dimension, int x, int y, int z, int color)
    {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank())
        {
            throw new IllegalArgumentException("dimension must not be blank");
        }
        waypoints.removeIf(waypoint -> waypoint.death()
                && waypoint.dimension().equals(dimension));
        long now = System.currentTimeMillis();
        Waypoint death = new Waypoint(UUID.randomUUID(), DEATH_WAYPOINT_NAME, dimension,
                x, y, z, color, DEATH_WAYPOINT_ICON, Waypoint.NO_GROUP, true, false, true,
                now, now, Map.of());
        waypoints.add(death);
        return death;
    }

    /**
     * Serializes the current waypoints to a version 2 JSON string.
     *
     * @return the JSON text, never {@code null}
     */
    public String exportJson()
    {
        return store.toJson(waypoints);
    }

    /**
     * Replaces the current waypoints from a JSON string.
     *
     * <p>The in-memory list is replaced only when parsing succeeds.</p>
     *
     * @param json the JSON text, never {@code null}
     * @throws NullPointerException when {@code json} is {@code null}
     * @throws IOException          when the text is malformed
     */
    public void importJson(String json) throws IOException
    {
        importJson(json, ImportMode.REPLACE);
    }

    /**
     * Imports waypoints from a JSON string.
     *
     * @param json the JSON text, never {@code null}
     * @param mode the replace or merge strategy, never {@code null}
     * @return the number of imported entries (all of them for
     *         {@link ImportMode#REPLACE})
     * @throws NullPointerException when an argument is {@code null}
     * @throws IOException          when the text is malformed
     */
    public int importJson(String json, ImportMode mode) throws IOException
    {
        Objects.requireNonNull(mode, "mode");
        List<Waypoint> incoming = store.fromJson(json);
        if (mode == ImportMode.REPLACE)
        {
            waypoints.clear();
            waypoints.addAll(incoming);
            return incoming.size();
        }
        return merge(incoming);
    }

    /**
     * Writes the current waypoints to a standalone JSON file.
     *
     * @param file the destination, never {@code null}
     * @throws NullPointerException when {@code file} is {@code null}
     * @throws IOException          when the file cannot be written
     */
    public void exportFile(Path file) throws IOException
    {
        Objects.requireNonNull(file, "file");
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }
        Files.writeString(file, exportJson() + "\n", StandardCharsets.UTF_8);
    }

    /**
     * Imports waypoints from a standalone JSON file.
     *
     * @param file the source file, never {@code null}
     * @param mode the replace or merge strategy, never {@code null}
     * @return the number of imported entries
     * @throws NullPointerException when an argument is {@code null}
     * @throws IOException          when the file cannot be read or is malformed
     */
    public int importFile(Path file, ImportMode mode) throws IOException
    {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(mode, "mode");
        String json = Files.readString(file, StandardCharsets.UTF_8);
        return importJson(json, mode);
    }

    /**
     * Returns a transport-neutral snapshot of every waypoint.
     *
     * @param revision the caller-supplied sync revision
     * @return an unmodifiable list, never {@code null}
     */
    public List<WaypointSyncRecord> syncRecords(long revision)
    {
        List<WaypointSyncRecord> records = new ArrayList<>(waypoints.size());
        for (Waypoint waypoint : waypoints)
        {
            records.add(WaypointSyncRecord.of(waypoint, revision));
        }
        return List.copyOf(records);
    }

    /**
     * Saves the current waypoint list.
     *
     * @throws IOException when the store cannot be written
     */
    public void save() throws IOException
    {
        store.save(waypoints);
    }

    /**
     * The import strategy used by {@link #importJson(String, ImportMode)}.
     */
    public enum ImportMode
    {
        /** Discard the current list and use the imported list. */
        REPLACE,
        /** Keep existing entries and overwrite entries with a matching id. */
        MERGE
    }

    private int merge(List<Waypoint> incoming)
    {
        Map<UUID, Integer> indexById = new HashMap<>();
        for (int index = 0; index < waypoints.size(); index++)
        {
            indexById.putIfAbsent(waypoints.get(index).id(), index);
        }
        int imported = 0;
        for (Waypoint waypoint : incoming)
        {
            Integer index = indexById.get(waypoint.id());
            if (index == null)
            {
                indexById.put(waypoint.id(), waypoints.size());
                waypoints.add(waypoint);
            }
            else
            {
                waypoints.set(index, waypoint);
            }
            imported++;
        }
        return imported;
    }

    private int indexOf(UUID id)
    {
        for (int index = 0; index < waypoints.size(); index++)
        {
            if (waypoints.get(index).id().equals(id))
            {
                return index;
            }
        }
        return -1;
    }

    private static double distanceSquared(Waypoint waypoint, double originX, double originZ)
    {
        double dx = waypoint.x() - originX;
        double dz = waypoint.z() - originZ;
        return dx * dx + dz * dz;
    }

    private static String signature(Waypoint waypoint)
    {
        return waypoint.name().toLowerCase(Locale.ROOT)
                + '\u0000' + waypoint.dimension()
                + '\u0000' + waypoint.x()
                + '\u0000' + waypoint.y()
                + '\u0000' + waypoint.z()
                + '\u0000' + Integer.toHexString(waypoint.color())
                + '\u0000' + waypoint.icon().toLowerCase(Locale.ROOT)
                + '\u0000' + waypoint.group().toLowerCase(Locale.ROOT);
    }
}
