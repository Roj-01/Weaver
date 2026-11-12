package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: allays and piglins picking up items. */
@Mixin(targets = {"net.minecraft.world.entity.animal.allay.Allay", "net.minecraft.world.entity.monster.piglin.Piglin"})
public abstract class GriefPickupMixin {
    @WrapOperation(method = "wantsToPickUp(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, (net.minecraft.world.entity.Entity) (Object) this);
    }
}
