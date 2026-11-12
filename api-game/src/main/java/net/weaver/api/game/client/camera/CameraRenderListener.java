package net.weaver.api.game.client.camera;

import net.weaver.api.game.client.util.OrderedClientListener;

/**
 * Mutates or observes the camera render state.
 *
 * <p>Listeners are registered through
 * {@link CameraRenderRegistry#register(CameraRenderListener)} and run in
 * ascending {@link #order()} with registration order as the tie-break. They run
 * on the render thread from the built-in {@code CameraRenderMixin}.
 */
public interface CameraRenderListener extends OrderedClientListener
{
    /**
     * Observes or mutates the camera state.
     *
     * <p>Do not retain the context, camera, or state, and keep the callback
     * short. Exceptions are isolated by the registry.
     *
     * @param context the mutable camera snapshot, never {@code null}
     */
    void onExtractCamera(CameraRenderContext context);
}
