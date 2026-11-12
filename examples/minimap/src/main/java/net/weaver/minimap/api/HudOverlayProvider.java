package net.weaver.minimap.api;

import java.util.List;

/**
 * Supplies text lines for the minimap HUD.
 *
 * <p>Providers run on the client thread. Returning {@code null} or an empty
 * list contributes nothing. Exceptions are isolated and logged once.</p>
 */
@FunctionalInterface
public interface HudOverlayProvider
{
    /**
     * Returns overlay lines for a frame.
     *
     * @param context the HUD context, never {@code null}
     * @return the lines, or {@code null} for none
     */
    List<HudOverlayLine> lines(HudOverlayContext context);
}
