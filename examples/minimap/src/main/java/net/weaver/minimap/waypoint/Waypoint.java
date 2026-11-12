package net.weaver.minimap.waypoint;

import com.google.gson.JsonElement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An immutable named location in a dimension.
 *
 * <p>All 32 bits of {@code color} are preserved; callers normally interpret
 * the value as ARGB.</p>
 *
 * <p>The record is the version 2 model. Version 1 files are read into this
 * model with v2 defaults ({@code icon = "pin"}, empty group, non-temporary and
 * non-death flags, zero timestamps). Unknown JSON fields seen while loading are
 * retained in {@link #extensions()} so a future version can round-trip them
 * without data loss.</p>
 *
 * @param id         the stable identifier, never {@code null}
 * @param name       the display name, never {@code null} or blank
 * @param dimension  the dimension identifier, never {@code null} or blank
 * @param x          the block X coordinate
 * @param y          the block Y coordinate
 * @param z          the block Z coordinate
 * @param color      the packed color value, all 32 bits preserved
 * @param icon       the icon identifier, never {@code null} or blank
 * @param group      the group name, never {@code null}; empty means ungrouped
 * @param visible    whether the waypoint is shown on the map
 * @param temporary  whether the waypoint expires after a configured age
 * @param death      whether the waypoint marks a player death
 * @param createdAt  creation time in epoch milliseconds, zero when unknown
 * @param updatedAt  last update time in epoch milliseconds, zero when unknown
 * @param extensions unknown future JSON fields, never {@code null}
 */
public record Waypoint(UUID id, String name, String dimension, int x, int y, int z,
                       int color, String icon, String group, boolean visible,
                       boolean temporary, boolean death, long createdAt, long updatedAt,
                       Map<String, JsonElement> extensions)
{
    /** The icon id used when none is supplied. */
    public static final String DEFAULT_ICON = "pin";

    /** The group value used for ungrouped waypoints. */
    public static final String NO_GROUP = "";

    private static final Set<String> KNOWN_FIELDS = Set.of(
            "id", "name", "dimension", "x", "y", "z", "color", "icon", "group",
            "visible", "temporary", "death", "createdAt", "updatedAt");

    /**
     * Validates identifiers, text fields, and extension keys.
     *
     * @throws NullPointerException     when {@code id}, {@code name}, or
     *                                  {@code dimension} is {@code null}
     * @throws IllegalArgumentException when a text field is blank or an
     *                                  extension key is reserved
     */
    public Waypoint
    {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dimension, "dimension");
        if (name.isBlank())
        {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (dimension.isBlank())
        {
            throw new IllegalArgumentException("dimension must not be blank");
        }
        icon = icon == null || icon.isBlank() ? DEFAULT_ICON : icon;
        group = group == null ? NO_GROUP : group;
        if (createdAt < 0L)
        {
            createdAt = 0L;
        }
        if (updatedAt < 0L)
        {
            updatedAt = 0L;
        }
        extensions = immutableExtensions(extensions);
    }

    /**
     * Creates a version 1 compatible waypoint with v2 defaults.
     *
     * <p>This overload keeps the original eight-argument constructor source
     * compatible. Timestamps are set to the current time, the icon is
     * {@link #DEFAULT_ICON}, and the waypoint is not temporary or a death
     * marker.</p>
     *
     * @param id        the stable identifier
     * @param name      the display name
     * @param dimension the dimension identifier
     * @param x         the block X coordinate
     * @param y         the block Y coordinate
     * @param z         the block Z coordinate
     * @param color     the packed color value
     * @param visible   whether the waypoint is shown
     */
    public Waypoint(UUID id, String name, String dimension, int x, int y, int z,
                    int color, boolean visible)
    {
        this(id, name, dimension, x, y, z, color, DEFAULT_ICON, NO_GROUP, visible,
                false, false, System.currentTimeMillis(), System.currentTimeMillis(),
                Map.of());
    }

    /**
     * Creates a visible waypoint with a random identifier and timestamps.
     *
     * @param name      the display name, never {@code null} or blank
     * @param dimension the dimension identifier, never {@code null} or blank
     * @param x         the block X coordinate
     * @param y         the block Y coordinate
     * @param z         the block Z coordinate
     * @param color     the packed color value
     * @return a new visible waypoint, never {@code null}
     * @throws NullPointerException     when {@code name} or {@code dimension}
     *                                  is {@code null}
     * @throws IllegalArgumentException when {@code name} or {@code dimension}
     *                                  is blank
     */
    public static Waypoint create(String name, String dimension, int x, int y, int z, int color)
    {
        long now = System.currentTimeMillis();
        return new Waypoint(UUID.randomUUID(), name, dimension, x, y, z, color,
                DEFAULT_ICON, NO_GROUP, true, false, false, now, now, Map.of());
    }

    /**
     * Returns the names of the JSON fields that are part of the current store
     * format. Every other field is treated as an extension.
     *
     * @return an unmodifiable set of known field names
     */
    public static Set<String> knownFields()
    {
        return KNOWN_FIELDS;
    }

    /**
     * Returns a copy with the visibility changed.
     *
     * @param visible the new visibility
     * @return a waypoint with the same identifier and position
     */
    public Waypoint withVisible(boolean visible)
    {
        return new Waypoint(id, name, dimension, x, y, z, color, icon, group, visible,
                temporary, death, createdAt, touched(), extensions);
    }

    /** Returns a copy with a new name and a fresh update timestamp. */
    public Waypoint withName(String name)
    {
        return new Waypoint(id, name, dimension, x, y, z, color, icon, group, visible,
                temporary, death, createdAt, touched(), extensions);
    }

    /** Returns a copy at a new block position and a fresh update timestamp. */
    public Waypoint withPosition(int x, int y, int z)
    {
        return new Waypoint(id, name, dimension, x, y, z, color, icon, group, visible,
                temporary, death, createdAt, touched(), extensions);
    }

    /** Returns a copy with a new color and a fresh update timestamp. */
    public Waypoint withColor(int color)
    {
        return new Waypoint(id, name, dimension, x, y, z, color, icon, group, visible,
                temporary, death, createdAt, touched(), extensions);
    }

    /** Returns a copy with a new icon and a fresh update timestamp. */
    public Waypoint withIcon(String icon)
    {
        return new Waypoint(id, name, dimension, x, y, z, color, icon, group, visible,
                temporary, death, createdAt, touched(), extensions);
    }

    /** Returns a copy with a new group and a fresh update timestamp. */
    public Waypoint withGroup(String group)
    {
        return new Waypoint(id, name, dimension, x, y, z, color, icon, group, visible,
                temporary, death, createdAt, touched(), extensions);
    }

    /** Returns a copy with the temporary flag changed and a fresh update timestamp. */
    public Waypoint withTemporary(boolean temporary)
    {
        return new Waypoint(id, name, dimension, x, y, z, color, icon, group, visible,
                temporary, death, createdAt, touched(), extensions);
    }

    /** Returns a copy with the death flag changed and a fresh update timestamp. */
    public Waypoint withDeath(boolean death)
    {
        return new Waypoint(id, name, dimension, x, y, z, color, icon, group, visible,
                temporary, death, createdAt, touched(), extensions);
    }

    /**
     * Returns a copy with an extension field added or replaced.
     *
     * @param key   the extension key, not a reserved field name
     * @param value the JSON value, never {@code null}
     * @return a copy carrying the extension
     * @throws IllegalArgumentException when the key is blank or reserved
     */
    public Waypoint withExtension(String key, JsonElement value)
    {
        Objects.requireNonNull(value, "value");
        validateExtensionKey(key);
        Map<String, JsonElement> updated = new LinkedHashMap<>(extensions);
        updated.put(key, value);
        return new Waypoint(id, name, dimension, x, y, z, color, icon, group, visible,
                temporary, death, createdAt, touched(), updated);
    }

    /**
     * Returns a copy with an extension field removed.
     *
     * @param key the extension key
     * @return a copy without the extension
     */
    public Waypoint withoutExtension(String key)
    {
        if (key == null || !extensions.containsKey(key))
        {
            return this;
        }
        Map<String, JsonElement> updated = new LinkedHashMap<>(extensions);
        updated.remove(key);
        return new Waypoint(id, name, dimension, x, y, z, color, icon, group, visible,
                temporary, death, createdAt, touched(), updated);
    }

    /**
     * Returns a copy with an explicit update timestamp.
     *
     * @param updatedAt the update time in epoch milliseconds
     * @return a copy with the supplied timestamp
     */
    public Waypoint withUpdatedAt(long updatedAt)
    {
        return new Waypoint(id, name, dimension, x, y, z, color, icon, group, visible,
                temporary, death, createdAt, updatedAt, extensions);
    }

    /**
     * Tests whether this temporary waypoint has outlived a time-to-live.
     *
     * <p>A zero creation time means the age is unknown and the waypoint is
     * never treated as expired, so migrated data is not silently removed.</p>
     *
     * @param nowEpochMillis the current time in epoch milliseconds
     * @param ttlMillis      the time-to-live in milliseconds; zero or negative
     *                       disables expiry
     * @return {@code true} when the waypoint is temporary and old enough
     */
    public boolean expired(long nowEpochMillis, long ttlMillis)
    {
        if (!temporary || ttlMillis <= 0L || createdAt <= 0L || nowEpochMillis < createdAt)
        {
            return false;
        }
        return nowEpochMillis - createdAt >= ttlMillis;
    }

    private long touched()
    {
        return Math.max(System.currentTimeMillis(), updatedAt);
    }

    private static Map<String, JsonElement> immutableExtensions(
            Map<String, JsonElement> source)
    {
        if (source == null || source.isEmpty())
        {
            return Map.of();
        }
        Map<String, JsonElement> copy = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : source.entrySet())
        {
            String key = entry.getKey();
            validateExtensionKey(key);
            copy.put(key, Objects.requireNonNull(entry.getValue(),
                    "extension value for " + key));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void validateExtensionKey(String key)
    {
        if (key == null || key.isBlank())
        {
            throw new IllegalArgumentException("extension key must not be blank");
        }
        if (KNOWN_FIELDS.contains(key))
        {
            throw new IllegalArgumentException("extension key is reserved: " + key);
        }
    }
}
