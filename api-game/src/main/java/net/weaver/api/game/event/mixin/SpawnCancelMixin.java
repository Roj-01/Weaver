package net.weaver.api.game.event.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.weaver.api.game.event.support.SpawnCancels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every way a level adds a new entity ends in addEntity: a cancelled spawn is refused there. */
@Mixin(ServerLevel.class)
public abstract class SpawnCancelMixin {
    @Inject(method = "addEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void weaver$cancelledSpawn(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (SpawnCancels.consume(entity)) {
            callback.setReturnValue(false);
        }
    }
}
