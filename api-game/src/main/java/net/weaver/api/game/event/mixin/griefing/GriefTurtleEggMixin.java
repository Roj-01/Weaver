package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: mobs trampling turtle eggs. */
@Mixin(targets = {"net.minecraft.world.level.block.TurtleEggBlock"})
public abstract class GriefTurtleEggMixin {
    // Wraps the whole method so the entity is popped even when it throws.
    @WrapMethod(method = "canDestroyEgg(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)Z")
    private boolean weaver$griefContext(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.Entity entity, Operation<Boolean> original) {
        Griefing.push(entity);
        try {
            return original.call(level, pos, entity);
        } finally {
            Griefing.pop();
        }
    }

    @WrapOperation(method = "canDestroyEgg(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, Griefing.context());
    }
}
