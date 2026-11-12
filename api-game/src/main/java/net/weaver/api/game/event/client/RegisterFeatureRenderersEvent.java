package net.weaver.api.game.event.client;

import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.weaver.api.event.Event;

/**
 * Fired once on the client when the feature render dispatcher is created
 * ({@code FeatureRenderDispatcher}), to add renderers for a mod's own kind of
 * submitted geometry.
 *
 * <p>Create the type once with {@code FeatureRendererType.create("mymod:glow")},
 * register its renderer here, and submit nodes whose {@code featureType()} is
 * that type into a phase of the frame's submit collection, for example
 * {@code event.phases().solid.submit(node)} in {@link PrepareRenderBuffersEvent}.
 * Vanilla groups the nodes by type and hands them to the renderer to prepare and
 * draw. Register the listener during mod initialization.
 */
public final class RegisterFeatureRenderersEvent extends Event {
    private final FeatureRendererMap renderers;
    private final ModelManager modelManager;
    private final AtlasManager atlasManager;
    private final Font font;

    public RegisterFeatureRenderersEvent(FeatureRendererMap renderers, ModelManager modelManager,
            AtlasManager atlasManager, Font font) {
        this.renderers = renderers;
        this.modelManager = modelManager;
        this.atlasManager = atlasManager;
        this.font = font;
    }

    /** Registers the renderer of a feature type; a vanilla type is replaced. */
    public <S extends SubmitNode> void register(FeatureRendererType<S> type, FeatureRenderer<S> renderer) {
        renderers.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(renderer, "renderer"));
    }

    /** The renderer registered for a type, or null. */
    public <S extends SubmitNode> FeatureRenderer<S> get(FeatureRendererType<S> type) {
        return renderers.get(type);
    }

    public ModelManager modelManager() {
        return modelManager;
    }

    public AtlasManager atlasManager() {
        return atlasManager;
    }

    public Font font() {
        return font;
    }
}
