package net.weaver.minimap.api;

import java.util.List;

/**
 * Supplies extra waypoint markers to the map.
 *
 * <p>Providers run on the client thread. Returning {@code null} or an empty
 * list contributes nothing. Exceptions are isolated and logged once.</p>
 */
@FunctionalInterface
public interface WaypointProvider
{
    /**
     * Returns markers for a query.
     *
     * @param query the query, never {@code null}
     * @return the markers, or {@code null} for none
     */
    List<WaypointMarker> waypoints(WaypointQuery query);
}
