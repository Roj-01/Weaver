package net.weaver.api.game.event.support;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.weaver.api.game.event.mixin.client.BufferBuilderAccessor;
import net.weaver.api.game.event.mixin.client.SectionCompilerInvoker;

/** The layer buffers of one section compile, for {@code AddSectionGeometryEvent}. Client only. */
public final class SectionBuffers implements Function<ChunkSectionLayer, VertexConsumer> {
    private final SectionCompiler compiler;
    private final Map<ChunkSectionLayer, BufferBuilder> builders;
    private final SectionBufferBuilderPack pack;
    private final Set<BufferBuilder> handedOut = Collections.newSetFromMap(new IdentityHashMap<>());

    public SectionBuffers(SectionCompiler compiler, Map<ChunkSectionLayer, BufferBuilder> builders,
            SectionBufferBuilderPack pack) {
        this.compiler = compiler;
        this.builders = builders;
        this.pack = pack;
    }

    @Override
    public VertexConsumer apply(ChunkSectionLayer layer) {
        BufferBuilder builder = ((SectionCompilerInvoker) compiler).weaver$getOrBeginLayer(builders, pack, layer);
        handedOut.add(builder);
        return builder;
    }

    /**
     * Completes a vertex a listener left half-written (it threw before setting every
     * element): vanilla would otherwise stop the section build with "Missing elements
     * in vertex". The filler values (white, no texture offset, full light, up normal)
     * only affect that one broken vertex.
     */
    public void finish() {
        for (BufferBuilder builder : handedOut) {
            try {
                if (((BufferBuilderAccessor) builder).weaver$elementsToFill() != 0) {
                    builder.setColor(-1).setUv(0.0F, 0.0F).setUv1(0, 0).setUv2(240, 240).setUv3(0.0F, 0.0F)
                            .setNormal(0.0F, 1.0F, 0.0F).setLineWidth(1.0F);
                    CrashShield.report("section-geometry", "incomplete-vertex", "completed",
                            new IllegalStateException("a listener left a vertex half-written"));
                }
            } catch (RuntimeException unfixable) {
                CrashShield.report("section-geometry", "incomplete-vertex", "left", unfixable);
            }
        }
    }
}
