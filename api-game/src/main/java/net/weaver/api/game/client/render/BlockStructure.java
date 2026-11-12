package net.weaver.api.game.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A group of blocks drawn together as one moving object: a contraption, a
 * schematic preview, a structure in a GUI scene. Positions are local to the
 * structure's origin; transform the pose stack (translate, rotate, scale) before
 * submitting to move the whole thing.
 */
public final class BlockStructure {
    private final Map<BlockPos, BlockState> blocks;

    private BlockStructure(Map<BlockPos, BlockState> blocks) {
        this.blocks = Map.copyOf(blocks);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<BlockPos, BlockState> blocks() {
        return blocks;
    }

    /** The centre of the bounding box, for rotating the structure around its middle. */
    public double[] centre() {
        if (blocks.isEmpty()) {
            return new double[] {0, 0, 0};
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks.keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new double[] {(minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0};
    }

    /**
     * Draws every block lit as in the world: each block takes the light at
     * {@code lightOrigin} plus its local position.
     */
    public void submit(SubmitNodeCollector collector, PoseStack poses, ClientLevel level, BlockPos lightOrigin) {
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos local = entry.getKey();
            poses.pushPose();
            poses.translate(local.getX(), local.getY(), local.getZ());
            WorldGeometry.block(collector, poses, level, lightOrigin.offset(local), entry.getValue());
            poses.popPose();
        }
    }

    /** Draws every block with a fixed light, without a level (GUI scenes, previews). */
    public void submitWithLight(SubmitNodeCollector collector, PoseStack poses, int light) {
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos local = entry.getKey();
            poses.pushPose();
            poses.translate(local.getX(), local.getY(), local.getZ());
            WorldGeometry.blockWithLight(collector, poses, entry.getValue(), light);
            poses.popPose();
        }
    }

    public static final class Builder {
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

        private Builder() {
        }

        /** Adds (or replaces) a block; air is ignored. */
        public Builder block(int x, int y, int z, BlockState state) {
            Objects.requireNonNull(state, "state");
            if (!state.is(Blocks.AIR)) {
                blocks.put(new BlockPos(x, y, z), state);
            }
            return this;
        }

        public BlockStructure build() {
            return new BlockStructure(blocks);
        }
    }
}
