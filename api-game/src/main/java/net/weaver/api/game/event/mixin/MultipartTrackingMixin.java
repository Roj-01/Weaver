package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.Entity;
import net.weaver.api.game.entity.MultipartEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A server level starts and stops tracking the parts of a multipart entity with it. */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class MultipartTrackingMixin {
    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void weaver$trackParts(Entity entity, CallbackInfo callback) {
        MultipartEntities.track(entity.level(), entity);
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void weaver$untrackParts(Entity entity, CallbackInfo callback) {
        MultipartEntities.untrack(entity.level(), entity);
    }
}
