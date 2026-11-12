package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.EntityTeleportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ender pearl teleports. */
@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlEventsMixin {
    @Inject(method = "onHit(Lnet/minecraft/world/phys/HitResult;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$pearl(HitResult hit, CallbackInfo callback) {
        ThrownEnderpearl self = (ThrownEnderpearl) (Object) this;
        Entity owner = self.getOwner();
        if (owner == null || self.level().isClientSide()) {
            return;
        }
        EntityTeleportEvent event = new EntityTeleportEvent(owner, EntityTeleportEvent.Cause.ENDER_PEARL,
                hit.getLocation());
        if (GameEvents.canceled(GameEvents.post(event, self.level()))) {
            self.discard();
            callback.cancel();
        }
    }
}
