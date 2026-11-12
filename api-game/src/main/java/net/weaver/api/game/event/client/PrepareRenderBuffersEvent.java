package net.weaver.api.game.event.client;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.weaver.api.event.Event;

/**
 * Fired on the render thread each frame after vanilla has submitted the
 * level entities, block entities, and particles and before the submitted
 * features are prepared into render buffers ({@code LevelRenderer#render}).
 * Mods can submit more geometry through {@link #collector()}; it is drawn with
 * the vanilla features in the same frame.
 */
public final class PrepareRenderBuffersEvent extends Event {
    private final LevelRenderState levelState;
    private final SubmitNodeCollector collector;

    public PrepareRenderBuffersEvent(LevelRenderState levelState, SubmitNodeCollector collector) {
        this.levelState = levelState;
        this.collector = collector;
    }

    public LevelRenderState levelState() {
        return levelState;
    }

    public SubmitNodeCollector collector() {
        return collector;
    }

    /**
     * The submit collection of the main order: its public phases ({@code solid},
     * {@code oitTranslucent}, {@code afterTerrain}, ...) take any submit node, including
     * nodes of a mod feature type registered with {@link RegisterFeatureRenderersEvent}.
     */
    public net.minecraft.client.renderer.SubmitNodeCollection phases() {
        return collector instanceof net.minecraft.client.renderer.SubmitNodeStorage storage ? storage.order(0) : null;
    }
}
