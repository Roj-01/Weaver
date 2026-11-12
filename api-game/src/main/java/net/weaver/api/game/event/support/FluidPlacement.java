package net.weaver.api.game.event.support;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.event.block.FluidPlaceBlockEvent;

/** Shared posting for blocks a fluid places; used by the fluid mixins. */
public final class FluidPlacement {
    private FluidPlacement() {
    }

    /** Returns the state to place, or null when a listener canceled it. */
    public static BlockState resolve(LevelAccessor level, BlockPos pos, BlockPos liquidPos, BlockState state) {
        if (!GameEvents.hasListeners(FluidPlaceBlockEvent.class)) {
            return state;
        }
        FluidPlaceBlockEvent event = GameEvents.post(new FluidPlaceBlockEvent(level, pos.immutable(), liquidPos.immutable(), state),
                level instanceof Level real ? real : null);
        if (GameEvents.canceled(event)) {
            return null;
        }
        return event.newState();
    }
}
