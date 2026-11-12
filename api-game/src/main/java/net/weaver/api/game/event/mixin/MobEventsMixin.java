package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.weaver.api.game.event.living.LivingChangeTargetEvent;
import net.weaver.api.game.event.living.LivingConversionPostEvent;
import net.weaver.api.game.event.living.LivingConversionPreEvent;
import net.weaver.api.game.event.living.FinalizeSpawnEvent;
import net.weaver.api.game.event.living.MobDespawnEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mob targeting, despawning, and conversion into other mobs. */
@Mixin(Mob.class)
public abstract class MobEventsMixin {
    @Unique
    private static final ThreadLocal<Object> weaver$reinvoking = new ThreadLocal<>();

    @Unique
    private Mob weaver$self() {
        return (Mob) (Object) this;
    }

    @Inject(method = "setTarget(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$changeTarget(LivingEntity target, CallbackInfo callback) {
        if (weaver$reinvoking.get() == this) {
            return;
        }
        Mob self = weaver$self();
        LivingEntity current = self.getTarget();
        if (current == target) {
            return;
        }
        LivingChangeTargetEvent event = GameEvents.post(new LivingChangeTargetEvent(self, current, target), self.level());
        if (GameEvents.canceled(event)) {
            callback.cancel();
            return;
        }
        if (event.newTarget() != target) {
            callback.cancel();
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                self.setTarget(event.newTarget());
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }

    @Inject(method = "finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;",
            at = @At("HEAD"), cancellable = true)
    private void weaver$finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                      SpawnGroupData groupData, CallbackInfoReturnable<SpawnGroupData> callback) {
        Mob self = weaver$self();
        FinalizeSpawnEvent event = GameEvents.post(new FinalizeSpawnEvent(self, level, reason), self.level());
        if (event.spawnCancelled()) {
            net.weaver.api.game.event.support.SpawnCancels.cancel(self);
        }
        if (GameEvents.canceled(event)) {
            callback.setReturnValue(groupData);
        }
    }

    @Inject(method = "checkDespawn()V", at = @At("HEAD"), cancellable = true)
    private void weaver$despawn(CallbackInfo callback) {
        if (!GameEvents.hasListeners(MobDespawnEvent.class)) {
            return;
        }
        Mob self = weaver$self();
        MobDespawnEvent event = GameEvents.post(new MobDespawnEvent(self), self.level());
        // No enum switch here: javac would emit a synthetic $SwitchMap class that
        // Mixin cannot carry into the target class.
        MobDespawnEvent.Decision decision = event.decision();
        if (decision == MobDespawnEvent.Decision.DENY) {
            callback.cancel();
        } else if (decision == MobDespawnEvent.Decision.ALLOW) {
            self.discard();
            callback.cancel();
        }
    }

    @Inject(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;",
            at = @At("HEAD"), cancellable = true)
    private <T extends Mob> void weaver$convertPre(EntityType<T> type, ConversionParams params, EntitySpawnReason reason,
                                                   ConversionParams.AfterConversion<T> after,
                                                   CallbackInfoReturnable<T> callback) {
        Mob self = weaver$self();
        if (GameEvents.canceled(GameEvents.post(new LivingConversionPreEvent(self, type, params.type()), self.level()))) {
            callback.setReturnValue(null);
        }
    }

    @Inject(method = "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;",
            at = @At("RETURN"))
    private <T extends Mob> void weaver$convertPost(EntityType<T> type, ConversionParams params, EntitySpawnReason reason,
                                                    ConversionParams.AfterConversion<T> after,
                                                    CallbackInfoReturnable<T> callback) {
        T outcome = callback.getReturnValue();
        if (outcome != null) {
            Mob self = weaver$self();
            GameEvents.post(new LivingConversionPostEvent(self, outcome), self.level());
        }
    }
}
