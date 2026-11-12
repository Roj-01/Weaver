package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.weaver.api.game.event.living.MobSplitEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Slimes and magma cubes splitting when they die. */
@Mixin(AbstractCubeMob.class)
public abstract class CubeMobEventsMixin {
    @Unique
    private static final ThreadLocal<Object> weaver$splitCanceled = new ThreadLocal<>();

    @Shadow
    protected abstract int getSplitCount();

    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void weaver$splitStart(Entity.RemovalReason reason, CallbackInfo callback) {
        AbstractCubeMob self = (AbstractCubeMob) (Object) this;
        if (self.level().isClientSide() || self.getSize() <= 1 || !self.isDeadOrDying()) {
            return;
        }
        if (GameEvents.canceled(GameEvents.post(new MobSplitEvent(self, getSplitCount()), self.level()))) {
            weaver$splitCanceled.set(this);
        }
    }

    @WrapOperation(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/cubemob/AbstractCubeMob;convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;"))
    private Mob weaver$splitChild(AbstractCubeMob self, EntityType<?> type, ConversionParams params,
                                  EntitySpawnReason reason, ConversionParams.AfterConversion<?> after,
                                  Operation<Mob> original) {
        if (weaver$splitCanceled.get() == this) {
            return null;
        }
        return original.call(self, type, params, reason, after);
    }

    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("TAIL"))
    private void weaver$splitEnd(Entity.RemovalReason reason, CallbackInfo callback) {
        if (weaver$splitCanceled.get() == this) {
            weaver$splitCanceled.remove();
        }
    }
}
