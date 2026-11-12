package net.weaver.api.game.event.support;

import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.HudLayerRenderEvent;

/** Asks {@link HudLayerRenderEvent} whether a part of the HUD is drawn this frame. */
public final class HudLayers {
    private HudLayers() {
    }

    /** {@code false} when a listener canceled the event for {@code layer}. */
    public static boolean shown(String layer) {
        return !GameEvents.hasListeners(HudLayerRenderEvent.class)
                || !GameEvents.canceled(GameEvents.post(new HudLayerRenderEvent(layer), LogicalSide.CLIENT));
    }
}
