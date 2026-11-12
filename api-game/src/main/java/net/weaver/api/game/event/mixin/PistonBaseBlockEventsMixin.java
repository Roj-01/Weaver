package net.weaver.api.game.event.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.event.block.PistonMovePostEvent;
import net.weaver.api.game.event.block.PistonMovePreEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Piston movement. Block event 0 is extend; 1 and 2 are retract (normal and
 * instant). Other event ids are not movement and fire nothing.
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockEventsMixin {
    @Inject(method = "triggerEvent(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void weaver$movePre(BlockState state, Level level, BlockPos pos, int eventId, int eventParam,
                                CallbackInfoReturnable<Boolean> callback) {
        if (eventId < 0 || eventId > 2) {
            return;
        }
        Direction direction = state.getValue(PistonBaseBlock.FACING);
        PistonMovePreEvent event = new PistonMovePreEvent(level, pos, state, direction, eventId == 0);
        if (GameEvents.canceled(GameEvents.post(event, level))) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "triggerEvent(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;II)Z",
            at = @At("RETURN"))
    private void weaver$movePost(BlockState state, Level level, BlockPos pos, int eventId, int eventParam,
                                 CallbackInfoReturnable<Boolean> callback) {
        if (eventId >= 0 && eventId <= 2 && Boolean.TRUE.equals(callback.getReturnValue())) {
            Direction direction = state.getValue(PistonBaseBlock.FACING);
            GameEvents.post(new PistonMovePostEvent(level, pos, direction, eventId == 0), level);
        }
    }
}
