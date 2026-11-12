package net.weaver.api.game.client.render;

/**
 * Receives one world render pass per rendered frame.
 *
 * <p>Listeners are registered through
 * {@link WorldRenderRegistry#register(WorldRenderListener)}. Lower
 * {@link #order()} values render first. The callback runs on the render thread
 * from the built-in {@code LevelRenderer#addMainThreadGizmos} hook.
 */
public interface WorldRenderListener
{
    /**
     * The unique listener id.
     *
     * @return the non-null, non-blank listener id
     */
    String id();

    /**
     * The render order; lower values render first.
     *
     * @return the render order
     */
    default int order()
    {
        return 0;
    }

    /**
     * Draws one frame.
     *
     * <p>Use {@link WorldRenderContext#sink()} for world-space primitives. Do
     * not mutate game state, do not retain the context or its game objects, and
     * keep the call short. Exceptions are isolated by the registry.
     *
     * @param context the immutable render snapshot, never {@code null}
     */
    void render(WorldRenderContext context);
}
