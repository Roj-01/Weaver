package net.weaver.api.game.event.mixin.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.Map;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Opens the per-layer buffer lookup of a section compile. */
@Mixin(SectionCompiler.class)
public interface SectionCompilerInvoker {
    @Invoker("getOrBeginLayer")
    BufferBuilder weaver$getOrBeginLayer(Map<ChunkSectionLayer, BufferBuilder> builders, SectionBufferBuilderPack pack,
            ChunkSectionLayer layer);
}
