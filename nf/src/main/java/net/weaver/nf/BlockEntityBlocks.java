package net.weaver.nf;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.weaver.api.game.registry.BlockEntityValidBlocks;

/**
 * Extra blocks for an existing block entity type, as NeoForge's
 * {@code BlockEntityTypeAddBlocksEvent}, backed by Weaver's
 * {@code BlockEntityValidBlocks}.
 */
public final class BlockEntityBlocks {
    private BlockEntityBlocks() {
    }

    public static synchronized void add(BlockEntityType<?> type, Block... blocks) {
        BlockEntityValidBlocks.add(type, blocks);
    }

    public static boolean has(BlockEntityType<?> type, Block block) {
        return BlockEntityValidBlocks.has(type, block);
    }
}
