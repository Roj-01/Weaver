package net.weaver.api.game.client.screen;

/**
 * The result returned by a {@link ScreenRenderListener}.
 */
public enum ScreenRenderResult
{
    /**
     * Let vanilla continue.
     */
    CONTINUE,

    /**
     * Skip the screen background when returned from a
     * {@link ScreenRenderPhase#BACKGROUND} callback.
     *
     * <p>The result is ignored for {@link ScreenRenderPhase#PRE} and
     * {@link ScreenRenderPhase#POST}.
     */
    CANCEL
}
