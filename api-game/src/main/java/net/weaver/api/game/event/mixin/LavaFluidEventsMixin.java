package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.LavaFluid;
import net.weaver.api.game.event.support.FluidPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lava setting fire around it, and lava flowing into water making stone. */
@Mixin(LavaFluid.class)
public abstract class LavaFluidEventsMixin {
    @WrapOperation(method = "randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/util/RandomSource;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean weaver$fire(ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original,
            @Local(argsOnly = true) BlockPos liquidPos) {
        BlockState placed = FluidPlacement.resolve(level, pos, liquidPos, state);
        return placed != null && original.call(level, pos, placed);
    }

    @WrapOperation(method = "spreadTo(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean weaver$stone(LevelAccessor level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        BlockState placed = FluidPlacement.resolve(level, pos, pos, state);
        return placed != null && original.call(level, pos, placed);
    }
}
