package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: mob explosions breaking blocks (the exploding entity is the griefer). */
@Mixin(targets = {"net.minecraft.server.level.ServerLevel"})
public abstract class GriefLevelExplodeMixin {
    // Wraps the whole method so the entity is popped even when it throws.
    @WrapMethod(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V")
    private void weaver$griefContext(net.minecraft.world.entity.Entity source, net.minecraft.world.damagesource.DamageSource damage, net.minecraft.world.level.ExplosionDamageCalculator calculator, double x, double y, double z, float radius, boolean fire, net.minecraft.world.level.Level.ExplosionInteraction interaction, net.minecraft.core.particles.ParticleOptions small, net.minecraft.core.particles.ParticleOptions large, net.minecraft.util.random.WeightedList<?> particles, net.minecraft.core.Holder<?> sound, Operation<Void> original) {
        Griefing.push(source);
        try {
            original.call(source, damage, calculator, x, y, z, radius, fire, interaction, small, large, particles, sound);
        } finally {
            Griefing.pop();
        }
    }

    @WrapOperation(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, Griefing.context());
    }
}
