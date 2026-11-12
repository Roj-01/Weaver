package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired when an entity falling on farmland is about to trample it back to dirt
 * ({@code FarmlandBlock#fallOn}). Cancelable: a canceled event keeps the farmland.
 */
public final class FarmlandTrampleEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final Entity entity;
    private final double fallDistance;

    public FarmlandTrampleEvent(ServerLevel level, BlockPos pos, BlockState state, Entity entity,
                                double fallDistance) {
        super(true);
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.entity = entity;
        this.fallDistance = fallDistance;
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

    public Entity entity() {
        return entity;
    }

    public double fallDistance() {
        return fallDistance;
    }
}
