package net.weaver.api.game.event.mixin.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@link BlockExtension#addRunningEffects}: the block under a sprinting entity. */
@Mixin(Entity.class)
public abstract class SprintParticleExtensionsMixin {
    @Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
    private void weaver$runningEffects(CallbackInfo callback) {
        Entity self = (Entity) (Object) this;
        BlockPos pos = self.getOnPosLegacy();
        BlockState state = self.level().getBlockState(pos);
        if (state.getBlock() instanceof BlockExtension extension && extension.addRunningEffects(state, self.level(), pos, self)) {
            callback.cancel();
        }
    }
}
