package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.weaver.api.event.Event;

/**
 * Fired after a piston started extending or retracting
 * ({@code PistonBaseBlock#triggerEvent} returned {@code true}). Fires on both sides.
 */
public final class PistonMovePostEvent extends Event {
    private final Level level;
    private final BlockPos pos;
    private final Direction direction;
    private final boolean extending;

    public PistonMovePostEvent(Level level, BlockPos pos, Direction direction, boolean extending) {
        this.level = level;
        this.pos = pos;
        this.direction = direction;
        this.extending = extending;
    }

    public Level level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    public Direction direction() {
        return direction;
    }

    public boolean extending() {
        return extending;
    }
}
