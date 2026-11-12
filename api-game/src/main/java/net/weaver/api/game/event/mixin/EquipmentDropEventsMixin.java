package net.weaver.api.game.event.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.weaver.api.game.event.living.GetEquipmentDropChanceEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Equipment drop chance after enchantment effects. */
@Mixin(EnchantmentHelper.class)
public abstract class EquipmentDropEventsMixin {
    @Inject(method = "processEquipmentDropChance(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("RETURN"), cancellable = true)
    private static void weaver$dropChance(ServerLevel level, LivingEntity entity, DamageSource source, float chance,
            CallbackInfoReturnable<Float> callback) {
        if (GameEvents.hasListeners(GetEquipmentDropChanceEvent.class)) {
            GetEquipmentDropChanceEvent event = GameEvents.post(
                    new GetEquipmentDropChanceEvent(entity, source, callback.getReturnValueF()), level);
            callback.setReturnValue(event.dropChance());
        }
    }
}
