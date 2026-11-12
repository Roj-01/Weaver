package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: AI goals that grief: breaking doors, removing blocks, raiding carrots, endermen moving blocks, silverfish entering stone, evokers turning sheep. */
@Mixin(targets = {"net.minecraft.world.entity.ai.goal.BreakDoorGoal", "net.minecraft.world.entity.ai.goal.RemoveBlockGoal", "net.minecraft.world.entity.animal.rabbit.Rabbit$RaidGardenGoal", "net.minecraft.world.entity.monster.Enderman$EndermanLeaveBlockGoal", "net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal", "net.minecraft.world.entity.monster.Silverfish$SilverfishMergeWithStoneGoal", "net.minecraft.world.entity.monster.illager.Evoker$EvokerWololoSpellGoal"})
public abstract class GriefGoalUseMixin {
    @WrapOperation(method = "canUse()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, Griefing.goalOwner(this));
    }
}
