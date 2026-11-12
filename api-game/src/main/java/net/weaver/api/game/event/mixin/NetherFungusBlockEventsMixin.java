package net.weaver.api.game.event.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.NetherFungusBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.event.block.BlockGrowFeatureEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nether fungi growing into huge fungi. */
@Mixin(NetherFungusBlock.class)
public abstract class NetherFungusBlockEventsMixin {
    @Inject(method = "performBonemeal(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/BonemealSource;)V",
            at = @At("HEAD"), cancellable = true)
    private void weaver$grow(ServerLevel level, RandomSource random, BlockPos pos, BlockState state,
                             BonemealSource source, CallbackInfo callback) {
        if (GameEvents.canceled(GameEvents.post(new BlockGrowFeatureEvent(level, pos, state), level))) {
            callback.cancel();
        }
    }
}
