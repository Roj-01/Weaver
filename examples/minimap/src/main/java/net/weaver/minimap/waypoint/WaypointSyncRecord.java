package net.weaver.minimap.waypoint;

import java.util.Objects;
import java.util.UUID;

/**
 * A transport-neutral snapshot of one waypoint for a future sync layer.
 *
 * <p>Version 2 does not implement any network protocol. This record only
 * describes the payload shape a future sync implementation could exchange:
 * the stable identifier, the user-visible fields, and a monotonically
 * increasing revision supplied by the caller. {@link #toWaypoint()} rebuilds
 * the local model, and {@link WaypointManager#syncRecords(long)} produces a
 * snapshot of the current list.</p>
 *
 * @param id        the stable identifier, never {@code null}
 * @param name      the display name, never {@code null} or blank
 * @param dimension the dimension identifier, never {@code null} or blank
 * @param x         the block X coordinate
 * @param y         the block Y coordinate
 * @param z         the block Z coordinate
 * @param color     the packed ARGB color
 * @param icon      the icon identifier
 * @param group     the group name; empty means ungrouped
 * @param visible   whether the waypoint is shown
 * @param temporary whether the waypoint expires
 * @param death     whether the waypoint is a death marker
 * @param createdAt creation time in epoch milliseconds
 * @param updatedAt last update time in epoch milliseconds
 * @param revision  the caller-supplied sync revision
 */
public record WaypointSyncRecord(UUID id, String name, String dimension, int x, int y, int z,
                                 int color, String icon, String group, boolean visible,
                                 boolean temporary, boolean death, long createdAt,
                                 long updatedAt, long revision)
{
    /**
     * Validates the identifier and text fields.
     *
     * @throws NullPointerException     when {@code id}, {@code name}, or
     *                                  {@code dimension} is {@code null}
     * @throws IllegalArgumentException when {@code name} or {@code dimension}
     *                                  is blank
     */
    public WaypointSyncRecord
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
        icon = icon == null || icon.isBlank() ? Waypoint.DEFAULT_ICON : icon;
        group = group == null ? Waypoint.NO_GROUP : group;
    }

    /**
     * Creates a sync record from a local waypoint.
     *
     * @param waypoint the source waypoint, never {@code null}
     * @param revision the caller-supplied revision
     * @return the sync record, never {@code null}
     * @throws NullPointerException when {@code waypoint} is {@code null}
     */
    public static WaypointSyncRecord of(Waypoint waypoint, long revision)
    {
        Objects.requireNonNull(waypoint, "waypoint");
        return new WaypointSyncRecord(waypoint.id(), waypoint.name(), waypoint.dimension(),
                waypoint.x(), waypoint.y(), waypoint.z(), waypoint.color(), waypoint.icon(),
                waypoint.group(), waypoint.visible(), waypoint.temporary(), waypoint.death(),
                waypoint.createdAt(), waypoint.updatedAt(), revision);
    }

    /**
     * Rebuilds the local waypoint model from this payload.
     *
     * <p>Unknown future fields are not part of the sync payload and are
     * therefore absent from the rebuilt waypoint.</p>
     *
     * @return a waypoint, never {@code null}
     */
    public Waypoint toWaypoint()
    {
        return new Waypoint(id, name, dimension, x, y, z, color, icon, group, visible,
                temporary, death, createdAt, updatedAt, java.util.Map.of());
    }
}
