package net.weaver.api.game.client.level;

import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.OrderedListenerRegistry;

import java.util.Objects;

/**
 * Registry for level render stage listeners.
 *
 * <p>This registry reports the frame's structural stages
 * ({@link LevelRenderStage#START}, {@link LevelRenderStage#SKY},
 * {@link LevelRenderStage#ENTITIES}, {@link LevelRenderStage#BLOCK_ENTITIES},
 * and {@link LevelRenderStage#END}). It does not draw anything and does not
 * replace {@link net.weaver.api.game.client.render.WorldRenderRegistry}, which
 * supplies the gizmo-backed world-space sink used for actual drawing.
 *
 * <p>The registry is thread-safe. Registration may happen from any thread;
 * dispatch runs on the render thread from the built-in mixin. One failing
 * listener is isolated and reported once per id.
 */
public final class LevelRenderRegistry
{
    private static final OrderedListenerRegistry<LevelRenderListener> LISTENERS =
            new OrderedListenerRegistry<>("level render listener");

    private LevelRenderRegistry()
    {
    }

    /**
     * Registers a level render listener.
     *
     * @param listener the listener, never {@code null}
     * @return a registration handle, never {@code null}
     * @throws net.weaver.api.client.ClientException when the listener id is
     *                                               blank or already registered
     */
    public static ClientRegistration register(LevelRenderListener listener)
    {
        return LISTENERS.register(listener);
    }

    /**
     * Dispatches one stage to every active listener.
     *
     * <p>Integration entry point for the built-in mixin. Mod code must not call
     * this method directly.
     *
     * @param context the stage snapshot, never {@code null}
     */
    @net.weaver.api.annotation.Internal
    public static void dispatch(LevelRenderContext context)
    {
        Objects.requireNonNull(context, "context");
        LISTENERS.forEach("LEVEL RENDER LISTENER",
                listener -> listener.onRender(context));
    }

    /**
     * Returns the number of registered listeners.
     *
     * @return the listener count
     */
    public static int listenerCount()
    {
        return LISTENERS.listenerCount();
    }

    /**
     * Removes every listener.
     *
     * <p>Intended for tests and loader shutdown.
     */
    public static void reset()
    {
        LISTENERS.reset();
    }
}
