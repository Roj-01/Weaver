package net.weaver.api.game.client.hand;

import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.OrderedListenerRegistry;

import java.util.Objects;

/**
 * Registry for first-person hand render listeners.
 *
 * <p>The built-in {@code HandRenderMixin} dispatches:
 * <ul>
 *     <li>{@link HandRenderPhase#PRE} at the head of
 *     {@code FirstPersonHandsAndItemsRenderer#submitHandsWithItems};</li>
 *     <li>{@link HandRenderPhase#PRE} at the head of the private
 *     {@code submitArmWithItem} for each hand;</li>
 *     <li>{@link HandRenderPhase#POST} at the return of
 *     {@code submitHandsWithItems};</li>
 *     <li>state mutation at the return of
 *     {@code FirstPersonHandsAndItems#extractRenderState}.</li>
 * </ul>
 *
 * <p>The registry is thread-safe. Registration may happen from any thread; the
 * dispatch methods run on the render thread and must not be called by mod code.
 * One failing listener is isolated and reported once per id.
 */
public final class HandRenderRegistry
{
    private static final OrderedListenerRegistry<HandRenderListener> LISTENERS =
            new OrderedListenerRegistry<>("hand render listener");

    private HandRenderRegistry()
    {
    }

    /**
     * Registers a hand render listener.
     *
     * @param listener the listener, never {@code null}
     * @return a registration handle, never {@code null}
     * @throws net.weaver.api.client.ClientException when the listener id is
     *                                               blank or already registered
     */
    public static ClientRegistration register(HandRenderListener listener)
    {
        return LISTENERS.register(listener);
    }

    /**
     * Dispatches the state-extraction mutation callback.
     *
     * <p>Integration entry point for the built-in mixin. Failure from one
     * listener is isolated.
     *
     * @param context the mutable state snapshot, never {@code null}
     */
    @net.weaver.api.annotation.Internal
    public static void dispatchState(HandRenderStateContext context)
    {
        Objects.requireNonNull(context, "context");
        LISTENERS.forEach("HAND RENDER STATE LISTENER",
                listener -> listener.onExtractState(context));
    }

    /**
     * Dispatches one render callback.
     *
     * <p>Integration entry point for the built-in mixin. A {@code CANCEL}
     * result during {@link HandRenderPhase#PRE} stops at the first cancelling
     * listener. A failure from one listener is isolated.
     *
     * @param context the render snapshot, never {@code null}
     * @return {@code true} when the callback was cancelled
     */
    @net.weaver.api.annotation.Internal
    public static boolean dispatch(HandRenderContext context)
    {
        Objects.requireNonNull(context, "context");
        if (context.phase() != HandRenderPhase.PRE)
        {
            LISTENERS.forEach("HAND RENDER LISTENER",
                    listener -> listener.onRender(context));
            return false;
        }
        return LISTENERS.anyMatch("HAND RENDER LISTENER",
                listener -> listener.onRender(context) == HandRenderResult.CANCEL);
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
