package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: armor stands, item frames, paintings, and dropped items damaged by mob explosions (the attacker is the griefer). */
@Mixin(targets = {"net.minecraft.world.entity.decoration.ArmorStand", "net.minecraft.world.entity.decoration.BlockAttachedEntity", "net.minecraft.world.entity.item.ItemEntity"})
public abstract class GriefHurtByExplosionMixin {
    // Wraps the whole method so the entity is popped even when it throws.
    @WrapMethod(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    private boolean weaver$griefContext(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount, Operation<Boolean> original) {
        Griefing.push(source.getEntity());
        try {
            return original.call(level, source, amount);
        } finally {
            Griefing.pop();
        }
    }

    @WrapOperation(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, Griefing.context());
    }
}
