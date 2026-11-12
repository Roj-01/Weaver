package net.weaver.api.game.client.hud;

/**
 * The stages of one GUI extraction pass.
 */
public enum HudRenderStage
{
    /**
     * Before {@code Hud#extractRenderState} draws any vanilla element.
     */
    PRE_HUD,

    /**
     * After {@code Hud#extractRenderState} returns. Legacy
     * {@link net.weaver.api.client.HudLayer} instances also render at this
     * stage.
     */
    POST_HUD,

    /**
     * Before the vanilla crosshair is drawn.
     */
    CROSSHAIR,

    /**
     * Before the vanilla item hotbar is drawn.
     */
    HOTBAR,

    /**
     * After {@code Gui#extractRenderState} returns. No
     * {@code GuiGraphicsExtractor} is available at this stage; the context
     * reports the scaled window size instead.
     */
    POST_GUI
}
