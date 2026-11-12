package net.weaver.api.game.client.fog;

import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.OrderedListenerRegistry;

import java.util.Objects;

/**
 * Registry for fog setup listeners.
 *
 * <p>The built-in {@code FogRenderMixin} injects at the return of
 * {@code FogRenderer#setupFog(Camera, int, DeltaTracker, float, ClientLevel)}
 * and dispatches a {@link FogRenderContext} before the frame's fog data is
 * uploaded. Listeners may mutate the {@link net.minecraft.client.renderer.fog.FogData}
 * fields directly.
 *
 * <p>The registry is thread-safe. Registration may happen from any thread;
 * dispatch runs on the render thread. One failing listener is isolated and
 * reported once per id.
 */
public final class FogRenderRegistry
{
    private static final OrderedListenerRegistry<FogRenderListener> LISTENERS =
            new OrderedListenerRegistry<>("fog render listener");

    private FogRenderRegistry()
    {
    }

    /**
     * Registers a fog listener.
     *
     * @param listener the listener, never {@code null}
     * @return a registration handle, never {@code null}
     * @throws net.weaver.api.client.ClientException when the listener id is
     *                                               blank or already registered
     */
    public static ClientRegistration register(FogRenderListener listener)
    {
        return LISTENERS.register(listener);
    }

    /**
     * Dispatches the fog setup pass.
     *
     * <p>Integration entry point for the built-in mixin. Mod code must not call
     * this method directly.
     *
     * @param context the mutable fog snapshot, never {@code null}
     */
    @net.weaver.api.annotation.Internal
    public static void dispatch(FogRenderContext context)
    {
        Objects.requireNonNull(context, "context");
        LISTENERS.forEach("FOG RENDER LISTENER",
                listener -> listener.onSetupFog(context));
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
