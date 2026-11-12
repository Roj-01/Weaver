package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@link BlockExtension#addDestroyEffects} and {@link BlockExtension#addHitEffects} on the client. */
@Mixin(ClientLevel.class)
public abstract class BlockParticleExtensionsMixin {
    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
    private void weaver$destroyEffects(BlockPos pos, BlockState state, CallbackInfo callback) {
        if (state.getBlock() instanceof BlockExtension extension
                && extension.addDestroyEffects(state, (ClientLevel) (Object) this, pos)) {
            callback.cancel();
        }
    }

    @Inject(method = "addBreakingParticles", at = @At("HEAD"), cancellable = true)
    private void weaver$hitEffects(BlockPos pos, Direction side, BlockState state, CallbackInfo callback) {
        if (state.getBlock() instanceof BlockExtension extension
                && extension.addHitEffects(state, (ClientLevel) (Object) this, pos, side)) {
            callback.cancel();
        }
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "playBreakingSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private net.minecraft.world.level.block.SoundType weaver$hitSound(BlockState state,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<net.minecraft.world.level.block.SoundType> original,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) BlockPos pos) {
        return state.getBlock() instanceof BlockExtension extension
                ? extension.getSoundType(state, (ClientLevel) (Object) this, pos, net.minecraft.client.Minecraft.getInstance().player)
                : original.call(state);
    }
}
