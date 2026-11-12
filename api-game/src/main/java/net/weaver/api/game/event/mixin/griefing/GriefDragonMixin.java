package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: the ender dragon flying through blocks. */
@Mixin(targets = {"net.minecraft.world.entity.boss.enderdragon.EnderDragon"})
public abstract class GriefDragonMixin {
    @WrapOperation(method = "checkWalls(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/AABB;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, (net.minecraft.world.entity.Entity) (Object) this);
    }
}
