package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: farmer villagers harvesting and planting. */
@Mixin(targets = {"net.minecraft.world.entity.ai.behavior.HarvestFarmland"})
public abstract class GriefHarvestMixin {
    // Wraps the whole method so the entity is popped even when it throws.
    @WrapMethod(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;)Z")
    private boolean weaver$griefContext(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.npc.villager.Villager villager, Operation<Boolean> original) {
        Griefing.push(villager);
        try {
            return original.call(level, villager);
        } finally {
            Griefing.pop();
        }
    }

    @WrapOperation(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, Griefing.context());
    }
}
