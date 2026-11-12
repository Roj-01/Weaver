package net.weaver.api.game.client.hud;

import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.OrderedListenerRegistry;

import java.util.Objects;

/**
 * Registry for HUD render stage listeners.
 *
 * <p>The built-in mixins dispatch:
 * <ul>
 *     <li>{@link HudRenderStage#PRE_HUD} at the head of
 *     {@code Hud#extractRenderState};</li>
 *     <li>{@link HudRenderStage#CROSSHAIR} at the head of the private
 *     {@code Hud#extractCrosshair};</li>
 *     <li>{@link HudRenderStage#HOTBAR} at the head of the private
 *     {@code Hud#extractItemHotbar};</li>
 *     <li>{@link HudRenderStage#POST_HUD} at the tail of
 *     {@code Hud#extractRenderState};</li>
 *     <li>{@link HudRenderStage#POST_GUI} at the tail of
 *     {@code Gui#extractRenderState}.</li>
 * </ul>
 *
 * <p>{@link HudRenderStage#POST_HUD} is also where the legacy
 * {@code ClientRegistry.renderHud(HudRenderContext)} entry point runs, so
 * existing {@link net.weaver.api.client.HudLayer} registrations keep their
 * original order relative to vanilla HUD elements.
 *
 * <p>A listener is skipped for a frame when a
 * {@link net.weaver.api.game.event.client.HudLayerRenderEvent} for its id is
 * canceled, so one mod can hide another mod's HUD.
 *
 * <p>The registry is thread-safe. Registration may happen from any thread;
 * dispatch runs on the render thread. One failing listener is isolated and
 * reported once per id.
 */
public final class HudRenderRegistry
{
    private static final OrderedListenerRegistry<HudRenderListener> LISTENERS =
            new OrderedListenerRegistry<>("hud render listener");

    private HudRenderRegistry()
    {
    }

    /**
     * Registers a HUD stage listener.
     *
     * @param listener the listener, never {@code null}
     * @return a registration handle, never {@code null}
     * @throws net.weaver.api.client.ClientException when the listener id is
     *                                               blank or already registered
     */
    public static ClientRegistration register(HudRenderListener listener)
    {
        return LISTENERS.register(listener);
    }

    /**
     * Dispatches one HUD stage.
     *
     * <p>Integration entry point for the built-in mixins. Mod code must not
     * call this method directly.
     *
     * @param context the stage snapshot, never {@code null}
     */
    @net.weaver.api.annotation.Internal
    public static void dispatch(HudRenderContext context)
    {
        Objects.requireNonNull(context, "context");
        LISTENERS.forEach("HUD RENDER LISTENER", listener -> {
            if (net.weaver.api.game.event.support.HudLayers.shown(listener.id()))
            {
                listener.onRender(context);
            }
        });
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
     * <p>Intended for tests and loader shutdown. Legacy {@code HudLayer}
     * registrations are owned by the game-free client backend and are not
     * affected by this method.
     */
    public static void reset()
    {
        LISTENERS.reset();
    }
}
