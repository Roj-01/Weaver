package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.game.event.living.LivingDamagePostEvent;
import net.weaver.api.game.event.living.LivingDamagePreEvent;
import net.weaver.api.game.event.player.CriticalHitEvent;
import net.weaver.api.game.event.player.SweepAttackEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player attacks (critical hits and sweeps) and the player's own damage
 * pipeline, which overrides {@code LivingEntity#actuallyHurt}.
 */
@Mixin(Player.class)
public abstract class PlayerCombatEventsMixin {
    @Unique
    private static final ThreadLocal<Entity> weaver$attackTarget = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Float> weaver$critMultiplier = new ThreadLocal<>();

    @Unique
    private DamageSource weaver$damageSource;
    @Unique
    private float weaver$damage;
    @Unique
    private float weaver$healthBefore;
    @Unique
    private boolean weaver$damageFired;

    @Unique
    private Player weaver$self() {
        return (Player) (Object) this;
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void weaver$attackStart(Entity target, CallbackInfo callback) {
        weaver$attackTarget.set(target);
        weaver$critMultiplier.remove();
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
    private void weaver$attackEnd(Entity target, CallbackInfo callback) {
        weaver$attackTarget.remove();
        weaver$critMultiplier.remove();
    }

    @ModifyExpressionValue(method = "attack(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;canCriticalAttack(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean weaver$critical(boolean critical) {
        Player self = weaver$self();
        CriticalHitEvent event = GameEvents.post(
                new CriticalHitEvent(self, weaver$attackTarget.get(), critical), self.level());
        weaver$critMultiplier.set(event.damageMultiplier());
        return event.critical();
    }

    @ModifyExpressionValue(method = "attack(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "CONSTANT", args = "floatValue=1.5F"))
    private float weaver$criticalMultiplier(float vanilla) {
        Float chosen = weaver$critMultiplier.get();
        return chosen != null ? chosen : vanilla;
    }

    @ModifyExpressionValue(method = "attack(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;isSweepAttack(ZZZ)Z"))
    private boolean weaver$sweep(boolean sweeping) {
        Player self = weaver$self();
        SweepAttackEvent event = GameEvents.post(
                new SweepAttackEvent(self, weaver$attackTarget.get(), sweeping), self.level());
        return event.sweeping();
    }

    @Inject(method = "actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("HEAD"))
    private void weaver$damageStart(ServerLevel level, DamageSource source, float damage, CallbackInfo callback) {
        weaver$damageSource = source;
        weaver$healthBefore = weaver$self().getHealth();
        weaver$damageFired = false;
    }

    @ModifyExpressionValue(method = "actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float weaver$damagePre(float damage) {
        if (weaver$damageSource == null || !GameEvents.hasListeners(LivingDamagePreEvent.class)
                && !GameEvents.hasListeners(LivingDamagePostEvent.class)) {
            return damage;
        }
        Player self = weaver$self();
        LivingDamagePreEvent event = GameEvents.post(new LivingDamagePreEvent(self, weaver$damageSource, damage),
                self.level());
        float result = GameEvents.canceled(event) ? 0.0F : Math.max(0.0F, event.damage());
        weaver$damage = result;
        weaver$damageFired = true;
        return result;
    }

    @Inject(method = "actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("TAIL"))
    private void weaver$damagePost(ServerLevel level, DamageSource source, float damage, CallbackInfo callback) {
        if (weaver$damageFired && weaver$damage > 0.0F) {
            Player self = weaver$self();
            GameEvents.post(new LivingDamagePostEvent(self, source, weaver$damage,
                    Math.max(0.0F, weaver$healthBefore - self.getHealth())), level);
        }
        weaver$damageSource = null;
        weaver$damageFired = false;
    }
}
