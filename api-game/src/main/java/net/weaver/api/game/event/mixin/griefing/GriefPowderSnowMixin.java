package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: burning mobs melting powder snow. */
@Mixin(targets = {"net.minecraft.world.level.block.PowderSnowBlock"})
public abstract class GriefPowderSnowMixin {
    // Wraps the whole method so the entity is popped even when it throws.
    @WrapMethod(method = "lambda$entityInside$0(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V")
    private static void weaver$griefContext(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.Entity entity, Operation<Void> original) {
        Griefing.push(entity);
        try {
            original.call(level, pos, entity);
        } finally {
            Griefing.pop();
        }
    }

    @WrapOperation(method = "lambda$entityInside$0(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private static Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, Griefing.context());
    }
}
