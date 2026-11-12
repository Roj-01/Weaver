package net.weaver.api.game.client.hand;

/**
 * The phase of a first-person hand render pass.
 */
public enum HandRenderPhase
{
    /**
     * Before the first-person hand pass or before one individual hand.
     */
    PRE,

    /**
     * After the first-person hand pass.
     */
    POST
}
