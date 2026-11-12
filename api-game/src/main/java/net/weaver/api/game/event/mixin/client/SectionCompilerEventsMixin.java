package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.Map;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.AddSectionGeometryEvent;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.support.SectionBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extra static geometry in a compiled chunk section. */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerEventsMixin {
    @Inject(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;", ordinal = 0))
    private void weaver$addGeometry(SectionPos section, RenderSectionRegion region, VertexSorting sorting,
            SectionBufferBuilderPack pack, CallbackInfoReturnable<SectionCompiler.Results> callback,
            @Local(ordinal = 0) Map<ChunkSectionLayer, BufferBuilder> builders) {
        if (GameEvents.hasListeners(AddSectionGeometryEvent.class)) {
            SectionBuffers buffers = new SectionBuffers((SectionCompiler) (Object) this, builders, pack);
            GameEvents.post(new AddSectionGeometryEvent(section, region, buffers), LogicalSide.CLIENT);
            buffers.finish();
        }
    }
}
