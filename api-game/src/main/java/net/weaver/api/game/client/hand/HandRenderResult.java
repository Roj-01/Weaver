package net.weaver.api.game.client.hand;

/**
 * The result returned by a {@link HandRenderListener}.
 */
public enum HandRenderResult
{
    /**
     * Let the vanilla hand render continue.
     */
    CONTINUE,

    /**
     * Skip the current hand or, for a whole-pass {@link HandRenderPhase#PRE}
     * callback, the whole first-person hand pass.
     *
     * <p>A {@code CANCEL} returned during {@link HandRenderPhase#POST} is
     * ignored because rendering has already happened.
     */
    CANCEL
}
