package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired when a fluid places a block: lava meeting water makes obsidian, cobblestone, stone, or
 * basalt ({@code LiquidBlock#shouldSpreadLiquid}, {@code LavaFluid#spreadTo}), and lava sets fire to
 * nearby blocks ({@code LavaFluid#randomTick}). Cancelable: nothing is placed. The block can be
 * replaced. Server side.
 */
public final class FluidPlaceBlockEvent extends Event {
    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockPos liquidPos;
    private BlockState newState;

    public FluidPlaceBlockEvent(LevelAccessor level, BlockPos pos, BlockPos liquidPos, BlockState newState) {
        super(true);
        this.level = level;
        this.pos = pos;
        this.liquidPos = liquidPos;
        this.newState = newState;
    }

    /** The level. */
    public LevelAccessor level() {
        return level;
    }

    /** Where the block goes. */
    public BlockPos pos() {
        return pos;
    }

    /** The fluid that placed it. */
    public BlockPos liquidPos() {
        return liquidPos;
    }

    /** The block placed. */
    public BlockState newState() {
        return newState;
    }

    public void setNewState(BlockState newState) {
        this.newState = newState;
    }
}
