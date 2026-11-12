package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: mobs trampling farmland. */
@Mixin(targets = {"net.minecraft.world.level.block.FarmlandBlock"})
public abstract class GriefFarmlandMixin {
    // Wraps the whole method so the entity is popped even when it throws.
    @WrapMethod(method = "fallOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;D)V")
    private void weaver$griefContext(net.minecraft.world.level.Level level, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.Entity entity, double distance, Operation<Void> original) {
        Griefing.push(entity);
        try {
            original.call(level, state, pos, entity, distance);
        } finally {
            Griefing.pop();
        }
    }

    @WrapOperation(method = "fallOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;D)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, Griefing.context());
    }
}
