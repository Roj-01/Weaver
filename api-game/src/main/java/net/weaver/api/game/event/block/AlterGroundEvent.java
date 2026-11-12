package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired when a tree decorator changes the ground around a tree ({@code AlterGroundDecorator}: podzol
 * under spruce and mega trees). The new block can be replaced, or set to {@code null} to leave the
 * ground. Runs on world generation threads, so listeners must be thread-safe.
 */
public final class AlterGroundEvent extends Event {
    private final LevelAccessor level;
    private final BlockPos pos;
    private BlockState newState;

    public AlterGroundEvent(LevelAccessor level, BlockPos pos, BlockState newState) {
        super(false);
        this.level = level;
        this.pos = pos;
        this.newState = newState;
    }

    /** The level being generated. */
    public LevelAccessor level() {
        return level;
    }

    /** The ground position. */
    public BlockPos pos() {
        return pos;
    }

    /** The block placed, or {@code null}. */
    public BlockState newState() {
        return newState;
    }

    public void setNewState(BlockState newState) {
        this.newState = newState;
    }
}
