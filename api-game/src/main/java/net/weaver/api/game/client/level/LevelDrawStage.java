package net.weaver.api.game.client.level;

/**
 * Points inside the GPU work of one frame, in draw order. Unlike
 * {@link LevelRenderStage}, which reports when the frame is being set up, these
 * run while the frame is drawn: what a listener draws lands between what vanilla
 * drew before and after.
 *
 * <p>Vanilla 26.3 draws the sky, then one main pass: solid terrain, opaque
 * features, then translucent terrain, translucent features (particles among
 * them), clouds, weather, and the world border. Then come outlines and post
 * effects. With improved (order-independent) transparency the translucent part
 * runs once per transparency layer, in passes of its own; the translucent stages
 * then fire once per layer, with the layer named in the context.</p>
 */
public enum LevelDrawStage {
    /** The sky is drawn; nothing of the world yet. No open render pass. */
    AFTER_SKY,
    /**
     * Solid and cutout terrain is drawn; entities, block entities, and the other
     * opaque features are not yet. The main pass is open.
     */
    AFTER_OPAQUE_BLOCKS,
    /** Solid and cutout terrain and every opaque feature (entities, block entities) are drawn. The main pass is open. */
    AFTER_SOLID,
    /** Translucent terrain (water, stained glass, ice) is drawn. */
    AFTER_TRANSLUCENT_BLOCKS,
    /**
     * Translucent particles are drawn (opaque particles are drawn with the opaque
     * features, before {@link #AFTER_SOLID}). With improved transparency, once per
     * transparency layer.
     */
    AFTER_PARTICLES,
    /** Translucent features after terrain (particles, the block outline) are drawn. */
    AFTER_TRANSLUCENT_FEATURES,
    /** Clouds are drawn. */
    AFTER_CLOUDS,
    /** Rain and snow are drawn. */
    AFTER_WEATHER,
    /**
     * All translucent geometry, and the world border, is drawn. With classic
     * transparency the main pass is open; with improved transparency it is not,
     * and the context's pass is null.
     */
    AFTER_TRANSLUCENT,
    /** The whole level is drawn, before the GUI. No open render pass. */
    AFTER_LEVEL
}
