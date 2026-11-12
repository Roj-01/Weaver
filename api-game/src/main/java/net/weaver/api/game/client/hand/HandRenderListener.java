package net.weaver.api.game.client.hand;

import net.weaver.api.game.client.util.OrderedClientListener;

/**
 * Receives first-person hand render callbacks.
 *
 * <p>Listeners are registered through
 * {@link HandRenderRegistry#register(HandRenderListener)}. They run in
 * ascending {@link #order()} with registration order as the tie-break.
 *
 * <p>{@link #onExtractState(HandRenderStateContext)} runs once per state
 * extraction and may mutate the state. {@link #onRender(HandRenderContext)}
 * runs before the whole hand pass, before each individual hand, and after the
 * whole hand pass. A {@link HandRenderResult#CANCEL} returned during a
 * {@link HandRenderPhase#PRE} callback skips the targeted hand or the whole
 * pass. Results returned during {@link HandRenderPhase#POST} are ignored.
 */
public interface HandRenderListener extends OrderedClientListener
{
    /**
     * Mutates the first-person hands render state.
     *
     * <p>The default does nothing. The callback runs on the render thread and
     * may write the public fields of {@link HandRenderStateContext#state()}.
     *
     * @param context the mutable state snapshot, never {@code null}
     */
    default void onExtractState(HandRenderStateContext context)
    {
    }

    /**
     * Observes or cancels one hand render callback.
     *
     * <p>The default lets vanilla render continue. Use
     * {@link HandRenderResult#CANCEL} only during a
     * {@link HandRenderPhase#PRE} callback.
     *
     * @param context the render snapshot, never {@code null}
     * @return {@link HandRenderResult#CONTINUE} or
     *         {@link HandRenderResult#CANCEL}
     */
    default HandRenderResult onRender(HandRenderContext context)
    {
        return HandRenderResult.CONTINUE;
    }
}
