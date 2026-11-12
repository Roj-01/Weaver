package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: boats and minecarts hit by mob explosions. */
@Mixin(targets = {"net.minecraft.world.entity.vehicle.VehicleEntity"})
public abstract class GriefVehicleMixin {
    // Wraps the whole method so the entity is popped even when it throws.
    @WrapMethod(method = "ignoreExplosion(Lnet/minecraft/world/level/Explosion;)Z")
    private boolean weaver$griefContext(net.minecraft.world.level.Explosion explosion, Operation<Boolean> original) {
        Griefing.push(explosion.getDirectSourceEntity());
        try {
            return original.call(explosion);
        } finally {
            Griefing.pop();
        }
    }

    @WrapOperation(method = "ignoreExplosion(Lnet/minecraft/world/level/Explosion;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, Griefing.context());
    }
}
