package net.weaver.api.game.event.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.living.LivingExperienceDropEvent;
import net.weaver.api.game.event.living.LivingFallEvent;
import net.weaver.api.game.event.living.LivingHealEvent;
import net.weaver.api.game.event.living.LivingJumpEvent;
import net.weaver.api.game.event.living.LivingKnockBackEvent;
import net.weaver.api.game.event.living.MobEffectAddedEvent;
import net.weaver.api.game.event.living.MobEffectApplicableEvent;
import net.weaver.api.game.event.living.MobEffectRemoveEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Living entity events: heal, fall, jump, knockback, experience drop, and mob
 * effects. See docs/hooks.md, "Game events".
 *
 * <p>Events that let a listener change an argument cancel the original call and
 * re-invoke the method with the new values. {@link #weaver$reinvoking} marks
 * the entity being re-invoked on this thread so the re-entered hook lets the
 * original body run instead of posting the event twice. Overrides in
 * subclasses dispatch normally and reach this hook through {@code super}.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEventsMixin {
    @Unique
    private static final ThreadLocal<Object> weaver$reinvoking = new ThreadLocal<>();

    @Unique
    private Entity weaver$experienceKiller;

    @Unique
    private LivingEntity weaver$self() {
        return (LivingEntity) (Object) this;
    }

    @Inject(method = "heal(F)V", at = @At("HEAD"), cancellable = true)
    private void weaver$heal(float amount, CallbackInfo callback) {
        if (weaver$reinvoking.get() == this) {
            return;
        }
        if (!GameEvents.hasListeners(LivingHealEvent.class)) {
            return;
        }
        LivingEntity self = weaver$self();
        LivingHealEvent event = GameEvents.post(new LivingHealEvent(self, amount), self.level());
        if (GameEvents.canceled(event) || event.amount() <= 0.0F) {
            callback.cancel();
            return;
        }
        if (event.amount() != amount) {
            callback.cancel();
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                self.heal(event.amount());
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }

    @Inject(method = "causeFallDamage(DFLnet/minecraft/world/damagesource/DamageSource;)Z",
            at = @At("HEAD"), cancellable = true)
    private void weaver$fall(double distance, float multiplier, DamageSource source,
                             CallbackInfoReturnable<Boolean> callback) {
        if (weaver$reinvoking.get() == this) {
            return;
        }
        if (!GameEvents.hasListeners(LivingFallEvent.class)) {
            return;
        }
        LivingEntity self = weaver$self();
        LivingFallEvent event = GameEvents.post(new LivingFallEvent(self, distance, multiplier, source),
                self.level());
        if (GameEvents.canceled(event)) {
            callback.setReturnValue(false);
            return;
        }
        if (event.distance() != distance || event.damageMultiplier() != multiplier) {
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                callback.setReturnValue(self.causeFallDamage(event.distance(), event.damageMultiplier(),
                        source));
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }

    @Inject(method = "jumpFromGround()V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;needsSync:Z",
                    opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void weaver$jump(CallbackInfo callback) {
        if (!GameEvents.hasListeners(LivingJumpEvent.class)) {
            return;
        }
        LivingEntity self = weaver$self();
        GameEvents.post(new LivingJumpEvent(self), self.level());
    }

    @Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
            at = @At("HEAD"), cancellable = true)
    private void weaver$knockback(double strength, double ratioX, double ratioZ, DamageSource source,
                                  float damage, boolean fromEffect, CallbackInfo callback) {
        if (weaver$reinvoking.get() == this) {
            return;
        }
        if (!GameEvents.hasListeners(LivingKnockBackEvent.class)) {
            return;
        }
        LivingEntity self = weaver$self();
        LivingKnockBackEvent event = GameEvents.post(
                new LivingKnockBackEvent(self, strength, ratioX, ratioZ, source), self.level());
        if (GameEvents.canceled(event)) {
            callback.cancel();
            return;
        }
        if (event.strength() != strength || event.ratioX() != ratioX || event.ratioZ() != ratioZ) {
            callback.cancel();
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                self.knockback(event.strength(), event.ratioX(), event.ratioZ(), source, damage,
                        fromEffect);
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }

    @Inject(method = "dropExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"))
    private void weaver$rememberKiller(ServerLevel level, Entity killer, CallbackInfo callback) {
        weaver$experienceKiller = killer;
    }

    @ModifyArg(method = "dropExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"),
            index = 2)
    private int weaver$experienceDrop(int amount) {
        LivingEntity self = weaver$self();
        Entity killer = weaver$experienceKiller;
        weaver$experienceKiller = null;
        LivingExperienceDropEvent event = GameEvents.post(
                new LivingExperienceDropEvent(self, killer, amount), self.level());
        if (GameEvents.canceled(event)) {
            return 0;
        }
        return Math.max(0, event.amount());
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void weaver$effectApplicable(MobEffectInstance effect, Entity source,
                                         CallbackInfoReturnable<Boolean> callback) {
        if (!GameEvents.hasListeners(MobEffectApplicableEvent.class)) {
            return;
        }
        LivingEntity self = weaver$self();
        if (GameEvents.canceled(GameEvents.post(new MobEffectApplicableEvent(self, effect), self.level()))) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"))
    private void weaver$effectAdded(MobEffectInstance effect, Entity source,
                                    CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue()) && GameEvents.hasListeners(MobEffectAddedEvent.class)) {
            LivingEntity self = weaver$self();
            GameEvents.post(new MobEffectAddedEvent(self, effect, source), self.level());
        }
    }

    @Inject(method = "removeEffect(Lnet/minecraft/core/Holder;)Z", at = @At("HEAD"), cancellable = true)
    private void weaver$effectRemove(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> callback) {
        if (!GameEvents.hasListeners(MobEffectRemoveEvent.class)) {
            return;
        }
        LivingEntity self = weaver$self();
        if (self.hasEffect(effect)
                && GameEvents.canceled(GameEvents.post(new MobEffectRemoveEvent(self, effect), self.level()))) {
            callback.setReturnValue(false);
        }
    }
}
