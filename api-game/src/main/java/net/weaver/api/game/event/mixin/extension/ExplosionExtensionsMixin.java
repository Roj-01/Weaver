package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link BlockExtension#getExplosionResistance}, for every explosion. */
@Mixin(ExplosionDamageCalculator.class)
public abstract class ExplosionExtensionsMixin {
    @WrapOperation(method = "getBlockExplosionResistance", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;getExplosionResistance()F"))
    private float weaver$resistance(Block block, Operation<Float> original, @Local(argsOnly = true) Explosion explosion,
            @Local(argsOnly = true) BlockGetter level, @Local(argsOnly = true) BlockPos pos,
            @Local(argsOnly = true) BlockState state) {
        if (block instanceof BlockExtension extension) {
            return extension.getExplosionResistance(state, level, pos, explosion);
        }
        return original.call(block);
    }
}
