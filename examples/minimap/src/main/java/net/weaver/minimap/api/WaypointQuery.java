package net.weaver.minimap.api;

/**
 * Immutable waypoint query passed to {@link WaypointProvider}.
 *
 * @param dimension   the current dimension id, or {@code null} outside a world
 * @param centerX     the query center block X
 * @param centerZ     the query center block Z
 * @param radiusBlocks the horizontal query radius in blocks
 * @param fullscreen  whether the fullscreen map is rendering
 */
public record WaypointQuery(String dimension, double centerX, double centerZ,
                            double radiusBlocks, boolean fullscreen)
{
}
