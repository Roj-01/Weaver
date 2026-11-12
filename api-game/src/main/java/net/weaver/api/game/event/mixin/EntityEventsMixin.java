package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.Entity;
import net.weaver.api.game.event.world.EntityMountEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity events: mounting and dismounting. */
@Mixin(Entity.class)
public abstract class EntityEventsMixin {
    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
    private void weaver$mount(Entity vehicle, boolean force, boolean sendEventAndTriggers,
                              CallbackInfoReturnable<Boolean> callback) {
        Entity self = (Entity) (Object) this;
        if (vehicle != null
                && GameEvents.canceled(GameEvents.post(new EntityMountEvent(self, vehicle, true), self.level()))) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "removeVehicle()V", at = @At("HEAD"), cancellable = true)
    private void weaver$dismount(CallbackInfo callback) {
        Entity self = (Entity) (Object) this;
        Entity vehicle = self.getVehicle();
        // A removed rider or vehicle must always detach; only voluntary dismounts may be canceled.
        if (vehicle == null || self.isRemoved() || vehicle.isRemoved()) {
            return;
        }
        if (GameEvents.canceled(GameEvents.post(new EntityMountEvent(self, vehicle, false), self.level()))) {
            callback.cancel();
        }
    }
}
