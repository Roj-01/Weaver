package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired after a random growth tick changed a growing plant
 * (the block state at the position differs from before the tick).
 */
public final class CropGrowPostEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState originalState;
    private final BlockState newState;

    public CropGrowPostEvent(ServerLevel level, BlockPos pos, BlockState originalState, BlockState newState) {
        this.level = level;
        this.pos = pos;
        this.originalState = originalState;
        this.newState = newState;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockState originalState() {
        return originalState;
    }

    /** The block state at the position after the tick. */
    public BlockState newState() {
        return newState;
    }
}
