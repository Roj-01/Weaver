package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.weaver.api.event.Event;

/**
 * Fired when a block is about to notify its neighbors of a change
 * ({@code ServerLevel#updateNeighborsAt} and
 * {@code updateNeighborsAtExceptFromFacing}). Cancelable: a canceled event skips
 * the neighbor updates, so redstone and similar reactions do not happen.
 * This fires very often; keep listeners cheap.
 */
public final class NeighborNotifyEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final Block sourceBlock;
    private final Direction skipped;

    public NeighborNotifyEvent(ServerLevel level, BlockPos pos, Block sourceBlock, Direction skipped) {
        super(true);
        this.level = level;
        this.pos = pos;
        this.sourceBlock = sourceBlock;
        this.skipped = skipped;
    }

    public ServerLevel level() {
        return level;
    }

    /** The position whose neighbors are notified. */
    public BlockPos pos() {
        return pos;
    }

    /** The block that caused the update. */
    public Block sourceBlock() {
        return sourceBlock;
    }

    /** The direction that is not notified, or {@code null} when all six are. */
    public Direction skipped() {
        return skipped;
    }
}
