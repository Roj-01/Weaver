package net.weaver.api.game.registry;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Extra blocks a block entity type is valid for, on top of the blocks it was
 * built with (a mod's sign or chest blocks using the vanilla sign or chest block
 * entity). Vanilla refuses a block entity on a block it does not list; a block
 * added here is accepted.
 */
public final class BlockEntityValidBlocks {
    private static final Map<BlockEntityType<?>, Set<Block>> EXTRA = new ConcurrentHashMap<>();

    private BlockEntityValidBlocks() {
    }

    /** Makes {@code type} valid for {@code blocks} too. */
    public static void add(BlockEntityType<?> type, Block... blocks) {
        Set<Block> set = EXTRA.computeIfAbsent(type, ignored -> ConcurrentHashMap.newKeySet());
        for (Block block : blocks) {
            set.add(block);
        }
    }

    /** Whether {@code block} was added for {@code type} here (vanilla's own blocks not included). */
    public static boolean has(BlockEntityType<?> type, Block block) {
        Set<Block> set = EXTRA.get(type);
        return set != null && set.contains(block);
    }
}
