package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.game.event.living.CanContinueSleepingEvent;
import net.weaver.api.game.event.player.PlayerItemBrokenEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.weaver.api.game.event.living.ArmorHurtEvent;
import net.weaver.api.game.event.living.LivingBreatheEvent;
import net.weaver.api.game.event.living.LivingDrownEvent;
import net.weaver.api.game.event.living.LivingEquipmentChangeEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Living entity equipment changes, armor durability, breathing, drowning, and beds. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityBodyEventsMixin {
    @Unique
    private LivingEntity weaver$self() {
        return (LivingEntity) (Object) this;
    }

    @WrapOperation(method = "collectEquipmentChanges(Ljava/util/Map;)Ljava/util/Map;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;equipmentHasChanged(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean weaver$equipmentChange(LivingEntity self, ItemStack previous, ItemStack current,
                                           Operation<Boolean> original, @Local EquipmentSlot slot) {
        boolean changed = original.call(self, previous, current);
        if (changed && GameEvents.hasListeners(LivingEquipmentChangeEvent.class)) {
            GameEvents.post(new LivingEquipmentChangeEvent(self, slot, previous, current), self.level());
        }
        return changed;
    }

    @WrapOperation(method = "doHurtEquipment(Lnet/minecraft/world/damagesource/DamageSource;F[Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private void weaver$armorHurt(ItemStack armor, int damage, LivingEntity wearer, EquipmentSlot slot,
                                  Operation<Void> original) {
        if (!GameEvents.hasListeners(ArmorHurtEvent.class)) {
            original.call(armor, damage, wearer, slot);
            return;
        }
        ArmorHurtEvent event = GameEvents.post(new ArmorHurtEvent(wearer, slot, armor, damage), wearer.level());
        if (!GameEvents.canceled(event) && event.damage() > 0) {
            original.call(armor, event.damage(), wearer, slot);
        }
    }

    // baseTick reads isEyeInFluid(WATER) exactly once, for the air-supply logic.
    @ModifyExpressionValue(method = "baseTick()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean weaver$breathe(boolean underwater) {
        if (!GameEvents.hasListeners(LivingBreatheEvent.class)) {
            return underwater;
        }
        LivingEntity self = weaver$self();
        LivingBreatheEvent event = GameEvents.post(new LivingBreatheEvent(self, underwater), self.level());
        return !event.canBreathe();
    }

    @WrapOperation(method = "baseTick()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean weaver$drown(LivingEntity self, ServerLevel level, DamageSource source, float damage,
                                 Operation<Boolean> original) {
        if (source.is(DamageTypes.DROWN) && GameEvents.canceled(
                GameEvents.post(new LivingDrownEvent(self, damage), level))) {
            return false;
        }
        return original.call(self, level, source, damage);
    }

    @Inject(method = "checkBedExists()Z", at = @At("RETURN"), cancellable = true)
    private void weaver$bedExists(CallbackInfoReturnable<Boolean> callback) {
        if (!GameEvents.hasListeners(CanContinueSleepingEvent.class)) {
            return;
        }
        LivingEntity self = weaver$self();
        boolean hasBed = callback.getReturnValue();
        CanContinueSleepingEvent event = GameEvents.post(new CanContinueSleepingEvent(self,
                hasBed ? null : Player.BedSleepingProblem.OTHER_PROBLEM), self.level());
        if (event.continueSleeping() != hasBed) {
            callback.setReturnValue(event.continueSleeping());
        }
    }

    @Inject(method = "onEquippedItemBroken(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;)V",
            at = @At("HEAD"))
    private void weaver$itemBroken(ItemStack item, EquipmentSlot slot, CallbackInfo callback) {
        if (weaver$self() instanceof Player player && GameEvents.hasListeners(PlayerItemBrokenEvent.class)) {
            GameEvents.post(new PlayerItemBrokenEvent(player, item, slot), player.level());
        }
    }
}
