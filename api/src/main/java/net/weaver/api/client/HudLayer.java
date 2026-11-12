package net.weaver.api.client;

/**
 * One layer drawn on top of the client HUD.
 *
 * <p>Layers are registered through
 * {@link ClientRegistry#registerHudLayer(HudLayer)} and rendered by
 * {@link ClientRegistry#renderHud(HudRenderContext)}. Lower {@link #order()}
 * values render first, so a layer with a larger order draws on top of a layer
 * with a smaller order. Equal orders keep backend-defined registration order.
 */
public interface HudLayer
{
    /**
     * The unique layer id.
     *
     * @return the non-null, non-blank layer id
     */
    String id();

    /**
     * The render order; lower values render first.
     *
     * @return the render order
     */
    int order();

    /**
     * Draws the layer.
     *
     * <p>Called on the render thread only. Implementations must not mutate game
     * state or the world, must not start or stop world tick work, and should
     * keep the call short. Drawing must use {@link HudRenderContext#graphics()}
     * rather than reaching into global state.
     *
     * @param context the immutable render snapshot, never {@code null}
     */
    void render(HudRenderContext context);
}
