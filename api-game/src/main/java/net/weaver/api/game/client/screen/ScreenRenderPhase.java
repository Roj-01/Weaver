package net.weaver.api.game.client.screen;

/**
 * The phase of one screen render callback.
 */
public enum ScreenRenderPhase
{
    /**
     * Before the screen's content is extracted.
     */
    PRE,

    /**
     * Before the screen's background is extracted. This is the only phase that
     * honours {@link ScreenRenderResult#CANCEL}.
     */
    BACKGROUND,

    /**
     * After the screen's content is extracted.
     */
    POST
}
