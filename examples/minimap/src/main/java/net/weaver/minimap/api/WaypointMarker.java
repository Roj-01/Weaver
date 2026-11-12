package net.weaver.minimap.api;

import java.util.Objects;

/**
 * A transport-neutral waypoint contributed by a {@link WaypointProvider}.
 *
 * @param id        the marker id, non-blank and unique within its provider
 * @param name      the display name, never {@code null}
 * @param dimension the dimension id, never {@code null}
 * @param x         the block X
 * @param y         the block Y
 * @param z         the block Z
 * @param color     the packed ARGB color
 * @param icon      the icon id, or {@code null} for the default
 * @param group     the group name, or {@code null} for ungrouped
 * @param temporary whether the marker expires
 */
public record WaypointMarker(String id, String name, String dimension, int x, int y, int z,
                             int color, String icon, String group, boolean temporary)
{
    /**
     * Validates the identifier and required text.
     *
     * @throws NullPointerException     when a required field is {@code null}
     * @throws IllegalArgumentException when {@code id}, {@code name}, or
     *                                  {@code dimension} is blank
     */
    public WaypointMarker
    {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(dimension, "dimension");
        if (id.isBlank())
        {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name.isBlank())
        {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (dimension.isBlank())
        {
            throw new IllegalArgumentException("dimension must not be blank");
        }
        icon = icon == null || icon.isBlank() ? "pin" : icon;
        group = group == null ? "" : group;
    }
}
