package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.game.event.living.LivingIncomingDamageEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;

/** The incoming damage of a hit, changeable before every reduction. */
@Mixin(LivingEntity.class)
public abstract class IncomingDamageEventsMixin {
    @WrapMethod(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    private boolean weaver$incomingDamage(ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        if (!GameEvents.hasListeners(LivingIncomingDamageEvent.class)) {
            return original.call(level, source, amount);
        }
        LivingIncomingDamageEvent event = GameEvents.post(
                new LivingIncomingDamageEvent((LivingEntity) (Object) this, source, amount), level);
        if (GameEvents.canceled(event)) {
            return false;
        }
        return original.call(level, source, event.amount());
    }
}
