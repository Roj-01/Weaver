package net.weaver.minimap.api;

/**
 * Immutable context passed to {@link MapActionProvider}.
 *
 * @param dimension the current dimension id, or {@code null} outside a world
 * @param fullscreen whether the fullscreen map is open
 * @param centerX   the map center block X
 * @param centerZ   the map center block Z
 * @param zoom      blocks shown per pixel
 */
public record MapActionContext(String dimension, boolean fullscreen, double centerX,
                               double centerZ, double zoom)
{
}
