package net.weaver.minimap.api;

/**
 * Immutable HUD context passed to {@link HudOverlayProvider}.
 *
 * @param fullscreen  whether the fullscreen map is rendering
 * @param width       the screen width in pixels
 * @param height      the screen height in pixels
 * @param hudScale    the configured HUD scale
 * @param dimension   the current dimension id, or {@code null} outside a world
 * @param playerX     the player block X
 * @param playerY     the player block Y
 * @param playerZ     the player block Z
 * @param zoom        blocks shown per pixel
 */
public record HudOverlayContext(boolean fullscreen, int width, int height, double hudScale,
                                String dimension, int playerX, int playerY, int playerZ,
                                double zoom)
{
}
