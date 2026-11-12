package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.support.DropCapture;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.EntityInvulnerabilityCheckEvent;
import net.weaver.api.game.event.world.EntityTravelToDimensionEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity invulnerability, death-drop capture, and dimension travel. */
@Mixin(Entity.class)
public abstract class EntityCombatEventsMixin {
    @Inject(method = "isInvulnerableToBase(Lnet/minecraft/world/damagesource/DamageSource;)Z", at = @At("RETURN"),
            cancellable = true)
    private void weaver$invulnerability(DamageSource source, CallbackInfoReturnable<Boolean> callback) {
        if (!GameEvents.hasListeners(EntityInvulnerabilityCheckEvent.class)) {
            return;
        }
        Entity self = (Entity) (Object) this;
        boolean invulnerable = callback.getReturnValueZ();
        EntityInvulnerabilityCheckEvent event = GameEvents.post(
                new EntityInvulnerabilityCheckEvent(self, source, invulnerable), self.level());
        if (event.invulnerable() != invulnerable) {
            callback.setReturnValue(event.invulnerable());
        }
    }

    @WrapOperation(method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean weaver$captureDrop(ServerLevel level, Entity spawned, Operation<Boolean> original) {
        if (spawned instanceof ItemEntity item && DropCapture.offer((Entity) (Object) this, item)) {
            return true;
        }
        return original.call(level, spawned);
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"), cancellable = true)
    private void weaver$travel(TeleportTransition transition, CallbackInfoReturnable<Entity> callback) {
        Entity self = (Entity) (Object) this;
        if (transition.newLevel() != self.level() && GameEvents.canceled(GameEvents.post(
                new EntityTravelToDimensionEvent(self, transition.newLevel().dimension()), self.level()))) {
            callback.setReturnValue(null);
        }
    }
}
