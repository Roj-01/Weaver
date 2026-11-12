package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.living.LivingDamagePostEvent;
import net.weaver.api.game.event.living.LivingDamagePreEvent;
import net.weaver.api.game.event.living.LivingDropsEvent;
import net.weaver.api.game.event.living.LivingShieldBlockEvent;
import net.weaver.api.game.event.living.LivingUseItemStartEvent;
import net.weaver.api.game.event.living.LivingDeathEvent;
import net.weaver.api.game.event.living.LivingUseItemTickEvent;
import net.weaver.api.game.event.living.LivingUseTotemEvent;
import net.weaver.api.game.event.living.MobEffectExpiredEvent;
import net.weaver.api.game.event.support.DropCapture;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.EntityTeleportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Living entity combat, death (the cancelable {@code LivingDeathEvent} and the
 * death drops), item use, effect expiry, and random teleports.
 * {@code Player} overrides {@code actuallyHurt} without calling super; the same
 * damage events are fired there by {@code PlayerCombatEventsMixin}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCombatEventsMixin {
    @Unique
    private static final ThreadLocal<Object> weaver$reinvoking = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Integer> weaver$useDuration = new ThreadLocal<>();

    @Shadow
    protected int useItemRemaining;

    @Unique
    private DamageSource weaver$damageSource;
    @Unique
    private float weaver$damage;
    @Unique
    private float weaver$healthBefore;
    @Unique
    private boolean weaver$damageFired;

    @Unique
    private LivingEntity weaver$self() {
        return (LivingEntity) (Object) this;
    }

    // ---- death --------------------------------------------------------------

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$death(DamageSource source, CallbackInfo callback) {
        LivingEntity self = weaver$self();
        if (self.level() instanceof ServerLevel level && GameEvents.hasListeners(LivingDeathEvent.class)
                && GameEvents.canceled(GameEvents.post(new LivingDeathEvent(self, source), level))) {
            callback.cancel();
        }
    }

    // ---- damage -------------------------------------------------------------

    @Inject(method = "actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At("HEAD"))
    private void weaver$damageStart(ServerLevel level, DamageSource source, float damage, CallbackInfo callback) {
        weaver$damageSource = source;
        weaver$healthBefore = weaver$self().getHealth();
        weaver$damageFired = false;
    }

    @ModifyExpressionValue(method = "actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float weaver$damagePre(float damage) {
        if (weaver$damageSource == null || !GameEvents.hasListeners(LivingDamagePreEvent.class)
                && !GameEvents.hasListeners(LivingDamagePostEvent.class)) {
            return damage;
        }
        LivingEntity self = weaver$self();
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
            LivingEntity self = weaver$self();
            GameEvents.post(new LivingDamagePostEvent(self, source, weaver$damage,
                    Math.max(0.0F, weaver$healthBefore - self.getHealth())), level);
        }
        weaver$damageSource = null;
        weaver$damageFired = false;
    }

    @WrapOperation(method = "applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/BlocksAttacks;resolveBlockedDamage(Lnet/minecraft/world/damagesource/DamageSource;FD)F"))
    private float weaver$shieldBlock(BlocksAttacks blocks, DamageSource source, float damage, double angle,
                                     Operation<Float> original) {
        float blocked = original.call(blocks, source, damage, angle);
        LivingEntity self = weaver$self();
        LivingShieldBlockEvent event = GameEvents.post(
                new LivingShieldBlockEvent(self, source, damage, blocked), self.level());
        return GameEvents.canceled(event) ? 0.0F : Math.max(0.0F, event.blockedDamage());
    }

    @Inject(method = "checkTotemDeathProtection(Lnet/minecraft/world/damagesource/DamageSource;)Z",
            at = @At("HEAD"), cancellable = true)
    private void weaver$totem(DamageSource source, CallbackInfoReturnable<Boolean> callback) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        LivingEntity self = weaver$self();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = self.getItemInHand(hand);
            if (stack.get(DataComponents.DEATH_PROTECTION) != null) {
                if (GameEvents.canceled(GameEvents.post(
                        new LivingUseTotemEvent(self, source, stack, hand), self.level()))) {
                    callback.setReturnValue(false);
                }
                return;
            }
        }
    }

    // ---- death drops ---------------------------------------------------------

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"))
    private void weaver$dropsStart(ServerLevel level, DamageSource source, CallbackInfo callback) {
        if (GameEvents.hasListeners(LivingDropsEvent.class)) {
            DropCapture.begin(weaver$self());
        }
    }

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("TAIL"))
    private void weaver$dropsEnd(ServerLevel level, DamageSource source, CallbackInfo callback) {
        List<ItemEntity> captured = DropCapture.end(weaver$self());
        if (captured == null) {
            return;
        }
        List<ItemEntity> drops = new ArrayList<>(captured);
        LivingDropsEvent event = GameEvents.post(new LivingDropsEvent(weaver$self(), source, drops), level);
        if (!GameEvents.canceled(event)) {
            for (ItemEntity item : drops) {
                if (item == null) {
                    continue;
                }
                if (!level.addFreshEntity(item)) {
                    System.err.println("[weaver] GAME EVENT DROP NOT ADDED entity=" + item
                            + " removed=" + item.isRemoved() + " owner=" + weaver$self());
                }
            }
        }
    }

    // ---- item use -----------------------------------------------------------

    @Inject(method = "startUsingItem(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$useStart(InteractionHand hand, CallbackInfo callback) {
        weaver$useDuration.remove();
        LivingEntity self = weaver$self();
        ItemStack stack = self.getItemInHand(hand);
        if (stack.isEmpty() || self.isUsingItem()) {
            return;
        }
        int duration = stack.getUseDuration(self);
        LivingUseItemStartEvent event = GameEvents.post(
                new LivingUseItemStartEvent(self, stack, hand, duration), self.level());
        if (GameEvents.canceled(event)) {
            callback.cancel();
        } else if (event.duration() != duration) {
            weaver$useDuration.set(event.duration());
        }
    }

    @ModifyExpressionValue(method = "startUsingItem(Lnet/minecraft/world/InteractionHand;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I"))
    private int weaver$useDurationOverride(int duration) {
        Integer override = weaver$useDuration.get();
        weaver$useDuration.remove();
        return override == null ? duration : override;
    }

    @Inject(method = "updateUsingItem(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void weaver$useTick(ItemStack stack, CallbackInfo callback) {
        if (stack.isEmpty() || !GameEvents.hasListeners(LivingUseItemTickEvent.class)) {
            return;
        }
        LivingEntity self = weaver$self();
        LivingUseItemTickEvent event = GameEvents.post(
                new LivingUseItemTickEvent(self, stack, useItemRemaining), self.level());
        useItemRemaining = event.remaining();
    }

    // ---- effects -------------------------------------------------------------

    @WrapOperation(method = "tickEffects()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;onEffectsRemoved(Ljava/util/Collection;)V"))
    private void weaver$effectExpired(LivingEntity self, Collection<MobEffectInstance> effects,
                                      Operation<Void> original) {
        original.call(self, effects);
        for (MobEffectInstance effect : effects) {
            GameEvents.post(new MobEffectExpiredEvent(self, effect), self.level());
        }
    }

    // ---- random teleport -----------------------------------------------------

    @Inject(method = "randomTeleport(DDDZLjava/util/function/Predicate;)Z", at = @At("HEAD"), cancellable = true)
    private void weaver$randomTeleport(double x, double y, double z, boolean particles,
                                       Predicate<BlockState> allowed, CallbackInfoReturnable<Boolean> callback) {
        if (weaver$reinvoking.get() == this) {
            return;
        }
        LivingEntity self = weaver$self();
        Vec3 target = new Vec3(x, y, z);
        EntityTeleportEvent event = GameEvents.post(
                new EntityTeleportEvent(self, EntityTeleportEvent.Cause.RANDOM, target), self.level());
        if (GameEvents.canceled(event)) {
            callback.setReturnValue(false);
            return;
        }
        Vec3 changed = event.target();
        if (!changed.equals(target)) {
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                callback.setReturnValue(self.randomTeleport(changed.x, changed.y, changed.z, particles, allowed));
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }
}
