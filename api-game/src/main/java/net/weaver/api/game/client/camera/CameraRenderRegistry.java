package net.weaver.api.game.client.camera;

import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.OrderedListenerRegistry;

import java.util.Objects;

/**
 * Registry for camera render state listeners.
 *
 * <p>The built-in {@code CameraRenderMixin} injects at the return of
 * {@code Camera#extractRenderState(CameraRenderState, DeltaTracker)} and
 * dispatches a {@link CameraRenderContext} before the state is consumed by the
 * level renderer. Listeners may mutate the
 * {@link net.minecraft.client.renderer.state.level.CameraRenderState} fields
 * directly.
 *
 * <p>The registry is thread-safe. Registration may happen from any thread;
 * dispatch runs on the render thread. One failing listener is isolated and
 * reported once per id.
 */
public final class CameraRenderRegistry
{
    private static final OrderedListenerRegistry<CameraRenderListener> LISTENERS =
            new OrderedListenerRegistry<>("camera render listener");

    private CameraRenderRegistry()
    {
    }

    /**
     * Registers a camera listener.
     *
     * @param listener the listener, never {@code null}
     * @return a registration handle, never {@code null}
     * @throws net.weaver.api.client.ClientException when the listener id is
     *                                               blank or already registered
     */
    public static ClientRegistration register(CameraRenderListener listener)
    {
        return LISTENERS.register(listener);
    }

    /**
     * Dispatches the camera state extraction.
     *
     * <p>Integration entry point for the built-in mixin. Mod code must not call
     * this method directly.
     *
     * @param context the mutable camera snapshot, never {@code null}
     */
    @net.weaver.api.annotation.Internal
    public static void dispatch(CameraRenderContext context)
    {
        Objects.requireNonNull(context, "context");
        LISTENERS.forEach("CAMERA RENDER LISTENER",
                listener -> listener.onExtractCamera(context));
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
