package net.weaver.minimap.api;

import java.util.List;

/**
 * Supplies actions for the fullscreen map context menu and toolbar.
 *
 * <p>Providers run on the client thread. Returning {@code null} or an empty
 * list contributes nothing. Exceptions are isolated and logged once.</p>
 */
@FunctionalInterface
public interface MapActionProvider
{
    /**
     * Returns actions for the current map context.
     *
     * @param context the map context, never {@code null}
     * @return the actions, or {@code null} for none
     */
    List<MapAction> actions(MapActionContext context);
}
