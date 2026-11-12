package net.weaver.api.game.event.client;

import net.weaver.api.event.Event;

/**
 * Fired on the client before one part of the HUD is drawn, each frame.
 * Cancelable: a canceled event leaves that part out for this frame.
 *
 * <p>{@link #layer()} is one of the
 * {@link net.weaver.api.game.client.hud.VanillaHudLayers} ids for the game's own
 * parts (crosshair, hotbar, hearts, food, armor, air, vehicle health,
 * experience bar and level, effects, boss bars, chat, tab list, titles,
 * scoreboard, ...), or the listener id of a
 * {@link net.weaver.api.game.client.hud.HudRenderRegistry} listener, so one mod
 * can also hide another mod's HUD.</p>
 *
 * <p>The event is only built while it has listeners. Hiding the crosshair or
 * the hotbar does not stop the {@code CROSSHAIR} and {@code HOTBAR} stages of
 * {@code HudRenderRegistry}, so a replacement can be drawn there.</p>
 */
public final class HudLayerRenderEvent extends Event {
    private final String layer;

    public HudLayerRenderEvent(String layer) {
        super(true);
        this.layer = layer;
    }

    /** The id of the part about to be drawn. */
    public String layer() {
        return layer;
    }

    /** Whether this is the part {@code id}. */
    public boolean is(String id) {
        return layer.equals(id);
    }
}
