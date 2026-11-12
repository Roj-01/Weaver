package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: weaving effect cobwebs when a mob dies. */
@Mixin(targets = {"net.minecraft.world.effect.WeavingMobEffect"})
public abstract class GriefWeavingMixin {
    // Wraps the whole method so the entity is popped even when it throws.
    @WrapMethod(method = "onMobRemoved(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;ILnet/minecraft/world/entity/Entity$RemovalReason;)V")
    private void weaver$griefContext(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.LivingEntity mob, int amplifier, net.minecraft.world.entity.Entity.RemovalReason reason, Operation<Void> original) {
        Griefing.push(mob);
        try {
            original.call(level, mob, amplifier, reason);
        } finally {
            Griefing.pop();
        }
    }

    @WrapOperation(method = "onMobRemoved(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;ILnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, Griefing.context());
    }
}
