package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link BlockExtension#isFireSource}, {@link BlockExtension#getFlammability},
 * {@link BlockExtension#getFireSpreadSpeed}, and {@link BlockExtension#onBurnedByFire}.
 */
@Mixin(FireBlock.class)
public abstract class FireExtensionsMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/core/HolderSet;)Z"))
    private boolean weaver$fireSource(BlockState below, HolderSet<Block> infiniburn, Operation<Boolean> original,
            @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) BlockPos pos) {
        if (below.getBlock() instanceof BlockExtension extension) {
            return extension.isFireSource(below, level, pos.below(), Direction.UP);
        }
        return original.call(below, infiniburn);
    }

    @Inject(method = "getBurnOdds", at = @At("HEAD"), cancellable = true)
    private void weaver$flammability(BlockState state, CallbackInfoReturnable<Integer> callback) {
        if (state.getBlock() instanceof BlockExtension extension && !weaver$waterlogged(state)) {
            int value = extension.getFlammability(state);
            if (value > 0) {
                callback.setReturnValue(value);
            }
        }
    }

    @Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/block/state/BlockState;)I", at = @At("HEAD"), cancellable = true)
    private void weaver$spreadSpeed(BlockState state, CallbackInfoReturnable<Integer> callback) {
        if (state.getBlock() instanceof BlockExtension extension && !weaver$waterlogged(state)) {
            int value = extension.getFireSpreadSpeed(state);
            if (value > 0) {
                callback.setReturnValue(value);
            }
        }
    }

    @Inject(method = "checkBurnOut", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;"))
    private void weaver$burned(net.minecraft.world.level.Level level, BlockPos pos, int chance,
            net.minecraft.util.RandomSource random, int age, CallbackInfo callback, @Local BlockState burned) {
        if (burned.getBlock() instanceof BlockExtension extension) {
            extension.onBurnedByFire(burned, level, pos);
        }
    }

    private static boolean weaver$waterlogged(BlockState state) {
        return state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);
    }
}
