package net.weaver.api.game.client.screen;

import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.OrderedListenerRegistry;

import java.util.Objects;

/**
 * Registry for screen render phase listeners.
 *
 * <p>The built-in {@code ScreenRenderMixin} targets the general
 * {@code Screen} class, so mods that subclass {@code Screen} keep working:
 * <ul>
 *     <li>{@link ScreenRenderPhase#PRE} at the head of
 *     {@code Screen#extractRenderState};</li>
 *     <li>{@link ScreenRenderPhase#BACKGROUND} at the head of
 *     {@code Screen#extractBackground}, cancellable;</li>
 *     <li>{@link ScreenRenderPhase#POST} at the return of
 *     {@code Screen#extractRenderState}.</li>
 * </ul>
 *
 * <p>A subclass that overrides {@code extractBackground} or
 * {@code extractRenderState} without calling the super implementation bypasses
 * the corresponding injection for its own drawing. The background and content
 * of the base {@code Screen} implementation remain covered.
 *
 * <p>The registry is thread-safe. Registration may happen from any thread;
 * dispatch runs on the render thread. One failing listener is isolated and
 * reported once per id.
 */
public final class ScreenRenderRegistry
{
    private static final OrderedListenerRegistry<ScreenRenderListener> LISTENERS =
            new OrderedListenerRegistry<>("screen render listener");

    private ScreenRenderRegistry()
    {
    }

    /**
     * Registers a screen render listener.
     *
     * @param listener the listener, never {@code null}
     * @return a registration handle, never {@code null}
     * @throws net.weaver.api.client.ClientException when the listener id is
     *                                               blank or already registered
     */
    public static ClientRegistration register(ScreenRenderListener listener)
    {
        return LISTENERS.register(listener);
    }

    /**
     * Dispatches one screen phase.
     *
     * <p>Integration entry point for the built-in mixin. Mod code must not call
     * this method directly. For {@link ScreenRenderPhase#BACKGROUND}, the first
     * cancelling listener stops dispatch and returns {@code true}. For all
     * other phases every listener runs and the result is ignored.
     *
     * @param context the phase snapshot, never {@code null}
     * @return {@code true} when the background should be skipped
     */
    @net.weaver.api.annotation.Internal
    public static boolean dispatch(ScreenRenderContext context)
    {
        Objects.requireNonNull(context, "context");
        if (context.phase() != ScreenRenderPhase.BACKGROUND)
        {
            LISTENERS.forEach("SCREEN RENDER LISTENER",
                    listener -> listener.onScreenRender(context));
            return false;
        }
        return LISTENERS.anyMatch("SCREEN RENDER LISTENER",
                listener -> listener.onScreenRender(context) == ScreenRenderResult.CANCEL);
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
