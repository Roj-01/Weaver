package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.weaver.api.game.event.support.CrashShield;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** An entity whose tick throws is suspended instead of crashing the game (see {@link CrashShield}). */
@Mixin(Level.class)
public abstract class CrashShieldTickMixin {
    @WrapOperation(method = "guardEntityTick(Ljava/util/function/Consumer;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private void weaver$guardEntity(Consumer<Object> tick, Object value, Operation<Void> original) {
        if (!CrashShield.ENABLED) {
            original.call(tick, value);
            return;
        }
        if (CrashShield.suspended(value)) {
            return;
        }
        try {
            original.call(tick, value);
        } catch (Throwable failure) {
            if (CrashShield.fatal(failure) || !(value instanceof Entity entity)) {
                throw CrashShield.rethrow(failure);
            }
            if (entity instanceof ServerPlayer player) {
                CrashShield.report("player-tick", player.getGameProfile().name(), "disconnected", failure);
                player.connection.disconnect(Component.literal(
                        "Weaver stopped a crash while ticking you: " + failure + " (see the server log)"));
                return;
            }
            if (entity instanceof Player) {
                throw CrashShield.rethrow(failure);
            }
            CrashShield.suspend(entity, "entity-tick", entity.getType().builtInRegistryHolder().getRegisteredName()
                    + "@" + entity.blockPosition().toShortString(), failure);
        }
    }
}
