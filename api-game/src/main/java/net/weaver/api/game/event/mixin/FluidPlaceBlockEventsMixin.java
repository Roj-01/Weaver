package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.event.support.FluidPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lava meeting water in the world: obsidian, cobblestone, basalt. */
@Mixin(LiquidBlock.class)
public abstract class FluidPlaceBlockEventsMixin {
    @WrapOperation(method = "shouldSpreadLiquid(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean weaver$fluidPlace(Level level, BlockPos pos, BlockState state, Operation<Boolean> original,
            @Local(argsOnly = true) BlockPos liquidPos) {
        BlockState placed = FluidPlacement.resolve(level, pos, liquidPos, state);
        return placed != null && original.call(level, pos, placed);
    }
}
