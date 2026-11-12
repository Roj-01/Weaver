package net.weaver.api.game.client.hud;

import net.weaver.api.game.client.util.OrderedClientListener;

/**
 * Observes one stage of a GUI extraction pass.
 *
 * <p>Listeners are registered through
 * {@link HudRenderRegistry#register(HudRenderListener)} and run in ascending
 * {@link #order()} with registration order as the tie-break. They run on the
 * render thread from the built-in {@code HudStageMixin},
 * {@code HudLayerMixin}, and {@code GuiStageMixin}.
 */
public interface HudRenderListener extends OrderedClientListener
{
    /**
     * Observes one stage.
     *
     * <p>Draw through {@link HudRenderContext#graphics()} when it is available.
     * Do not retain the context or any game object, and keep the callback
     * short. Exceptions are isolated by the registry.
     *
     * @param context the stage snapshot, never {@code null}
     */
    void onRender(HudRenderContext context);
}
