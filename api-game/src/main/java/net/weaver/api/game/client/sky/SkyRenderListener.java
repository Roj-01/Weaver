package net.weaver.api.game.client.sky;

import net.weaver.api.game.client.util.OrderedClientListener;

/**
 * Mutates or observes the sky render state.
 *
 * <p>Listeners are registered through
 * {@link SkyRenderRegistry#register(SkyRenderListener)} and run in ascending
 * {@link #order()} with registration order as the tie-break. They run on the
 * render thread from the built-in {@code SkyRenderMixin}.
 */
public interface SkyRenderListener extends OrderedClientListener
{
    /**
     * Observes or mutates the sky state.
     *
     * <p>Do not retain the context, state, level, or camera, and keep the
     * callback short. Exceptions are isolated by the registry.
     *
     * @param context the mutable sky snapshot, never {@code null}
     */
    void onExtractSky(SkyRenderContext context);
}
