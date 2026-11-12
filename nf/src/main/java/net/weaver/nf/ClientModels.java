package net.weaver.nf;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;
import net.weaver.api.game.client.model.ModelLoaderRegistry;
import net.weaver.api.game.client.render.CustomRendering;

/**
 * Client model and pipeline registrations, as NeoForge's
 * {@code RegisterItemModelsEvent} and {@code RegisterRenderPipelinesEvent}:
 * backed by Weaver's {@code ModelLoaderRegistry} and {@code CustomRendering}.
 */
public final class ClientModels {
    private ClientModels() {
    }

    /** An item model type, used by {@code "type": "<id>"} in item model files. Before the first resource load. */
    public static void registerItemModel(Identifier id, MapCodec<? extends ItemModel.Unbaked> codec) {
        ModelLoaderRegistry.registerItemModelType(id, codec);
    }

    /** A render pipeline, compiled with the game's own during resource loading. */
    public static void registerPipeline(RenderPipeline pipeline) {
        CustomRendering.precompile(pipeline);
    }
}
