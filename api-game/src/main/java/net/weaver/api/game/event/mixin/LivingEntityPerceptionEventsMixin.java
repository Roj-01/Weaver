package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.game.event.living.EffectParticleModificationEvent;
import net.weaver.api.game.event.living.LivingVisibilityEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** How visible a living entity is, and the particles its effects show. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPerceptionEventsMixin {
    @Shadow
    @Final
    private Map<Holder<MobEffect>, MobEffectInstance> activeEffects;

    @Inject(method = "getVisibilityPercent(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)D",
            at = @At("RETURN"), cancellable = true)
    private void weaver$visibility(ServerLevel level, Entity looking, CallbackInfoReturnable<Double> callback) {
        if (GameEvents.hasListeners(LivingVisibilityEvent.class)) {
            LivingVisibilityEvent event = GameEvents.post(
                    new LivingVisibilityEvent((LivingEntity) (Object) this, looking, callback.getReturnValueD()), level);
            callback.setReturnValue(event.visibility());
        }
    }

    // The particle list is built with stream lambdas; with listeners it is rebuilt
    // here, one event per effect, at the point it is stored in the entity data.
    @WrapOperation(method = "updateSynchronizedMobEffectParticles()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/syncher/SynchedEntityData;set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V",
            ordinal = 0))
    private void weaver$effectParticles(SynchedEntityData data, EntityDataAccessor<Object> accessor, Object value,
            Operation<Void> original) {
        if (!GameEvents.hasListeners(EffectParticleModificationEvent.class)) {
            original.call(data, accessor, value);
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        List<ParticleOptions> particles = new ArrayList<>();
        for (MobEffectInstance effect : List.copyOf(activeEffects.values())) {
            EffectParticleModificationEvent event = GameEvents.post(new EffectParticleModificationEvent(
                    self, effect, effect.getParticleOptions(), effect.isVisible()), self.level());
            if (event.visible() && event.options() != null) {
                particles.add(event.options());
            }
        }
        original.call(data, accessor, List.copyOf(particles));
    }
}
