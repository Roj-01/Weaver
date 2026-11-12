package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.player.PlayerLoadFromFileEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Player data loading. {@code PrepareSpawnTask.Ready#spawn} reads the saved
 * data into the new player and then moves it to its spawn position; the
 * event fires just before that move.
 */
@Mixin(targets = "net.minecraft.server.network.config.PrepareSpawnTask$Ready")
public abstract class PrepareSpawnTaskEventsMixin {
    @WrapOperation(method = "spawn(Lnet/minecraft/network/Connection;Lnet/minecraft/server/network/CommonListenerCookie;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;snapTo(Lnet/minecraft/world/phys/Vec3;FF)V"))
    private void weaver$loadFromFile(ServerPlayer player, Vec3 position, float yRot, float xRot, Operation<Void> original) {
        GameEvents.post(new PlayerLoadFromFileEvent(player), player.level());
        original.call(player, position, yRot, xRot);
    }
}
