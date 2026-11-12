package net.weaver.api.game.client.level;

/**
 * The stages of one level render pass.
 *
 * <p>The order in this enum matches the vanilla frame order. The built-in
 * {@code LevelRenderMixin} dispatches each stage from a verified Minecraft 26.3
 * target.
 */
public enum LevelRenderStage
{
    /**
     * Before {@code LevelRenderer#render} starts the frame.
     */
    START,

    /**
     * Before {@code LevelRenderer#addSkyPass} schedules the sky pass. The pose
     * stack and submit collector are not available at this stage.
     */
    SKY,

    /**
     * Before {@code LevelRenderer#submitEntities}. The pose stack, submit
     * collector, and level render state are available.
     */
    ENTITIES,

    /**
     * Before {@code LevelRenderer#submitBlockEntities}. The pose stack, submit
     * collector, and level render state are available.
     */
    BLOCK_ENTITIES,

    /**
     * After {@code LevelRenderer#render} returns. The pose stack and submit
     * collector are not available at this stage.
     */
    END
}
