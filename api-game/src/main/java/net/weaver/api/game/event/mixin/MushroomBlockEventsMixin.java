package net.weaver.api.game.event.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.event.block.BlockGrowFeatureEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mushrooms growing into huge mushrooms. */
@Mixin(MushroomBlock.class)
public abstract class MushroomBlockEventsMixin {
    @Inject(method = "growMushroom(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)Z",
            at = @At("HEAD"), cancellable = true)
    private void weaver$grow(ServerLevel level, BlockPos pos, BlockState state, RandomSource random,
                             CallbackInfoReturnable<Boolean> callback) {
        if (GameEvents.canceled(GameEvents.post(new BlockGrowFeatureEvent(level, pos, state), level))) {
            callback.setReturnValue(false);
        }
    }
}
