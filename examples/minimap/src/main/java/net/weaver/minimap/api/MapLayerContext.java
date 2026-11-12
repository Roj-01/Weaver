package net.weaver.minimap.api;

/**
 * Immutable render context passed to {@link MapLayerProvider}.
 *
 * @param dimension the current dimension id, or {@code null} outside a world
 * @param centerX   the map center block X
 * @param centerZ   the map center block Z
 * @param zoom      blocks shown per pixel
 * @param width     the map canvas width in pixels
 * @param height    the map canvas height in pixels
 * @param fullscreen whether the fullscreen map is rendering
 */
public record MapLayerContext(String dimension, int centerX, int centerZ, double zoom,
                              int width, int height, boolean fullscreen)
{
}
