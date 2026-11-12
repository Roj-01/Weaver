package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: explosions triggering blocks (the exploding entity, or a projectile owner, is the griefer). */
@Mixin(targets = {"net.minecraft.world.level.ServerExplosion"})
public abstract class GriefExplosionBlocksMixin {
    @WrapOperation(method = "canTriggerBlocks()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, ((net.minecraft.world.level.ServerExplosion) (Object) this).getDirectSourceEntity());
    }
}
