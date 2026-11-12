package net.weaver.api.game.event.mixin.client;

import net.minecraft.world.entity.Entity;
import net.weaver.api.game.entity.MultipartEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A client level tracks the parts of a multipart entity with it: the crosshair can pick them. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel$EntityCallbacks")
public abstract class MultipartClientTrackingMixin {
    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void weaver$trackParts(Entity entity, CallbackInfo callback) {
        MultipartEntities.track(entity.level(), entity);
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void weaver$untrackParts(Entity entity, CallbackInfo callback) {
        MultipartEntities.untrack(entity.level(), entity);
    }
}
