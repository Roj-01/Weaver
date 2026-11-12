package net.weaver.api.game.event.living;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired when the wither is about to break a block around itself
 * ({@code WitherBoss#customServerAiStep}). Cancelable: a canceled event keeps
 * that block.
 */
public final class LivingDestroyBlockEvent extends Event {
    private final LivingEntity entity;
    private final BlockPos pos;
    private final BlockState state;

    public LivingDestroyBlockEvent(LivingEntity entity, BlockPos pos, BlockState state) {
        super(true);
        this.entity = entity;
        this.pos = pos;
        this.state = state;
    }

    public LivingEntity entity() {
        return entity;
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockState state() {
        return state;
    }
}
