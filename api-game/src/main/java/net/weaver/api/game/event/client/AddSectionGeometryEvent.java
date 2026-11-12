package net.weaver.api.game.event.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.SectionPos;
import net.weaver.api.event.Event;

/**
 * Fired while a 16x16x16 chunk section is compiled into geometry
 * ({@code SectionCompiler#compile}), after vanilla has drawn its blocks and
 * fluids and before the buffers are finished. Mods add static geometry with
 * {@link #buffer(ChunkSectionLayer)}; vertex positions are relative to the
 * section origin ({@link #origin()}), like vanilla block geometry.
 *
 * <p>This event fires on the section-compile worker threads, often several at
 * once: listeners must be thread-safe and must only read the world through
 * {@link #region()}. To redraw a section after your data changes, mark it dirty
 * ({@code LevelRenderer#setSectionDirty}).
 *
 * <p>Write whole vertices (position and every element the layer format needs).
 * If a listener throws in the middle of a vertex, Weaver completes that one vertex
 * with filler values and reports it, so the section still builds.
 */
public final class AddSectionGeometryEvent extends Event {
    private final SectionPos section;
    private final BlockAndTintGetter region;
    private final Function<ChunkSectionLayer, VertexConsumer> buffers;

    public AddSectionGeometryEvent(SectionPos section, BlockAndTintGetter region,
            Function<ChunkSectionLayer, VertexConsumer> buffers) {
        this.section = section;
        this.region = region;
        this.buffers = buffers;
    }

    public SectionPos section() {
        return section;
    }

    /** The world origin of the section; vertex positions are relative to it. */
    public net.minecraft.core.BlockPos origin() {
        return section.origin();
    }

    /** A thread-safe snapshot of the blocks around the section. */
    public BlockAndTintGetter region() {
        return region;
    }

    /** The buffer of a layer (solid, cutout, translucent, ...), created on first use. */
    public VertexConsumer buffer(ChunkSectionLayer layer) {
        return buffers.apply(java.util.Objects.requireNonNull(layer, "layer"));
    }
}
