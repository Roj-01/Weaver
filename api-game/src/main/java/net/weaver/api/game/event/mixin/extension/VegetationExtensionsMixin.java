package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import net.weaver.api.game.extension.TriState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link BlockExtension#canSustainPlant}: the soil decides before the plant's own rule. */
@Mixin(VegetationBlock.class)
public abstract class VegetationExtensionsMixin {
    @WrapOperation(method = "canSurvive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/VegetationBlock;mayPlaceOn(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean weaver$sustain(VegetationBlock self, BlockState soil, BlockGetter level, BlockPos soilPos,
            Operation<Boolean> original, @Local(argsOnly = true) BlockState plant) {
        if (soil.getBlock() instanceof BlockExtension extension) {
            TriState answer = extension.canSustainPlant(soil, level, soilPos, plant);
            if (answer != TriState.DEFAULT) {
                return answer == TriState.TRUE;
            }
        }
        return original.call(self, soil, level, soilPos);
    }
}
