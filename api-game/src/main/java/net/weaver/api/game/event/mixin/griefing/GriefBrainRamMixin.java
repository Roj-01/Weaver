package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: goats and nautiluses ramming blocks. */
@Mixin(targets = {"net.minecraft.world.entity.animal.goat.GoatAi", "net.minecraft.world.entity.animal.nautilus.NautilusAi"})
public abstract class GriefBrainRamMixin {
    // Wraps the whole method so the entity is popped even when it throws.
    @WrapMethod(method = "lambda$static$0(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerLevel;)Z")
    private static boolean weaver$griefContext(net.minecraft.world.entity.LivingEntity entity, net.minecraft.server.level.ServerLevel level, Operation<Boolean> original) {
        Griefing.push(entity);
        try {
            return original.call(entity, level);
        } finally {
            Griefing.pop();
        }
    }

    @WrapOperation(method = "lambda$static$0(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/server/level/ServerLevel;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private static Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, Griefing.context());
    }
}
