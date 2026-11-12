package net.weaver.api.game.client.screen;

import net.weaver.api.game.client.util.OrderedClientListener;

/**
 * Observes one phase of a screen render pass.
 *
 * <p>Listeners are registered through
 * {@link ScreenRenderRegistry#register(ScreenRenderListener)} and run in
 * ascending {@link #order()} with registration order as the tie-break. They run
 * on the render thread from the built-in {@code ScreenRenderMixin}.
 */
public interface ScreenRenderListener extends OrderedClientListener
{
    /**
     * Observes or cancels one screen phase.
     *
     * <p>Return {@link ScreenRenderResult#CANCEL} only from a
     * {@link ScreenRenderPhase#BACKGROUND} callback to skip the vanilla
     * background. The result is ignored for other phases.
     *
     * @param context the phase snapshot, never {@code null}
     * @return {@link ScreenRenderResult#CONTINUE} or
     *         {@link ScreenRenderResult#CANCEL}
     */
    ScreenRenderResult onScreenRender(ScreenRenderContext context);
}
