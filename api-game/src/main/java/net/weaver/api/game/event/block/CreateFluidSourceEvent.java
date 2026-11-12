package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired when flowing fluid with enough neighboring sources checks whether it may
 * become a new source block ({@code FlowingFluid#getNewLiquid}, the
 * {@code canConvertToSource} check). Set {@link #setCanConvert(boolean)} to
 * allow or forbid it, for example to disable infinite water in a region.
 */
public final class CreateFluidSourceEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private boolean canConvert;

    public CreateFluidSourceEvent(ServerLevel level, BlockPos pos, BlockState state, boolean canConvert) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.canConvert = canConvert;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockState state() {
        return state;
    }

    /** The vanilla answer, or the value set by an earlier listener. */
    public boolean canConvert() {
        return canConvert;
    }

    public void setCanConvert(boolean canConvert) {
        this.canConvert = canConvert;
    }
}
