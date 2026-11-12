package net.weaver.api.game.client.fog;

import net.weaver.api.game.client.util.OrderedClientListener;

/**
 * Mutates or observes the fog data for one frame.
 *
 * <p>Listeners are registered through
 * {@link FogRenderRegistry#register(FogRenderListener)} and run in ascending
 * {@link #order()} with registration order as the tie-break. They run on the
 * render thread from the built-in {@code FogRenderMixin}.
 */
public interface FogRenderListener extends OrderedClientListener
{
    /**
     * Observes or mutates the fog data.
     *
     * <p>Do not retain the context, camera, level, or fog data, and keep the
     * callback short. Exceptions are isolated by the registry.
     *
     * @param context the mutable fog snapshot, never {@code null}
     */
    void onSetupFog(FogRenderContext context);
}
