package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: wither roses placed where a victim of the wither dies (the killer is the griefer). */
@Mixin(targets = {"net.minecraft.world.entity.LivingEntity"})
public abstract class GriefWitherRoseMixin {
    // Wraps the whole method so the entity is popped even when it throws.
    @WrapMethod(method = "createWitherRose(Lnet/minecraft/world/entity/LivingEntity;)V")
    private void weaver$griefContext(net.minecraft.world.entity.LivingEntity killer, Operation<Void> original) {
        Griefing.push(killer);
        try {
            original.call(killer);
        } finally {
            Griefing.pop();
        }
    }

    @WrapOperation(method = "createWitherRose(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, Griefing.context());
    }
}
