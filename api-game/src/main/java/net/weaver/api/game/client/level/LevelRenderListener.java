package net.weaver.api.game.client.level;

import net.weaver.api.game.client.util.OrderedClientListener;

/**
 * Observes one stage of the level render pass.
 *
 * <p>Listeners are registered through
 * {@link LevelRenderRegistry#register(LevelRenderListener)} and run in ascending
 * {@link #order()} with registration order as the tie-break. They run on the
 * render thread from the built-in {@code LevelRenderMixin}.
 *
 * <p>Level render listeners are observation hooks. They do not replace the
 * gizmo-based {@link net.weaver.api.game.client.render.WorldRenderRegistry};
 * see that registry for world-space drawing.
 */
public interface LevelRenderListener extends OrderedClientListener
{
    /**
     * Observes one stage.
     *
     * <p>Do not retain the context or its game objects, do not mutate the
     * render state, and keep the callback short. Exceptions are isolated by the
     * registry.
     *
     * @param context the immutable stage snapshot, never {@code null}
     */
    void onRender(LevelRenderContext context);
}
