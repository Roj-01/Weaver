package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired when a piston is about to extend or retract
 * ({@code PistonBaseBlock#triggerEvent}). Cancelable: a canceled event keeps
 * the piston and the blocks in front of it where they are. Fires on both sides.
 */
public final class PistonMovePreEvent extends Event {
    private final Level level;
    private final BlockPos pos;
    private final BlockState state;
    private final Direction direction;
    private final boolean extending;

    public PistonMovePreEvent(Level level, BlockPos pos, BlockState state, Direction direction,
                              boolean extending) {
        super(true);
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.direction = direction;
        this.extending = extending;
    }

    public Level level() {
        return level;
    }

    /** The position of the piston base. */
    public BlockPos pos() {
        return pos;
    }

    public BlockState state() {
        return state;
    }

    /** The direction the piston faces. */
    public Direction direction() {
        return direction;
    }

    public boolean extending() {
        return extending;
    }
}
