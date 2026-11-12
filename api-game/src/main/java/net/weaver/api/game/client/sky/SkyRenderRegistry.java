package net.weaver.api.game.client.sky;

import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.OrderedListenerRegistry;

import java.util.Objects;

/**
 * Registry for sky state listeners.
 *
 * <p>The built-in {@code SkyRenderMixin} injects at the return of
 * {@code SkyRenderer#extractRenderState(ClientLevel, float, Camera, SkyRenderState)}
 * and dispatches a {@link SkyRenderContext} before the sky is rendered.
 * Listeners may mutate the {@link net.minecraft.client.renderer.state.level.SkyRenderState}
 * fields directly.
 *
 * <p>The registry is thread-safe. Registration may happen from any thread;
 * dispatch runs on the render thread. One failing listener is isolated and
 * reported once per id.
 */
public final class SkyRenderRegistry
{
    private static final OrderedListenerRegistry<SkyRenderListener> LISTENERS =
            new OrderedListenerRegistry<>("sky render listener");

    private SkyRenderRegistry()
    {
    }

    /**
     * Registers a sky listener.
     *
     * @param listener the listener, never {@code null}
     * @return a registration handle, never {@code null}
     * @throws net.weaver.api.client.ClientException when the listener id is
     *                                               blank or already registered
     */
    public static ClientRegistration register(SkyRenderListener listener)
    {
        return LISTENERS.register(listener);
    }

    /**
     * Dispatches the sky state extraction.
     *
     * <p>Integration entry point for the built-in mixin. Mod code must not call
     * this method directly.
     *
     * @param context the mutable sky snapshot, never {@code null}
     */
    @net.weaver.api.annotation.Internal
    public static void dispatch(SkyRenderContext context)
    {
        Objects.requireNonNull(context, "context");
        LISTENERS.forEach("SKY RENDER LISTENER",
                listener -> listener.onExtractSky(context));
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
