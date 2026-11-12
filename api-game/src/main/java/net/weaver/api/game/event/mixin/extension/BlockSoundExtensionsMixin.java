package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link BlockExtension#getSoundType} for step sounds. */
@Mixin(Entity.class)
public abstract class BlockSoundExtensionsMixin {
    @WrapOperation(method = "playStepSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType weaver$stepSound(BlockState state, Operation<SoundType> original, @Local(argsOnly = true) BlockPos pos) {
        Entity self = (Entity) (Object) this;
        return state.getBlock() instanceof BlockExtension extension
                ? extension.getSoundType(state, self.level(), pos, self) : original.call(state);
    }
}
