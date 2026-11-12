package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.PlayerTurnEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mouse look turning the local player. */
@Mixin(MouseHandler.class)
public abstract class MouseTurnEventsMixin {
    @WrapOperation(method = "turnPlayer(D)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void weaver$turn(LocalPlayer player, double yaw, double pitch, Operation<Void> original) {
        if (!GameEvents.hasListeners(PlayerTurnEvent.class)) {
            original.call(player, yaw, pitch);
            return;
        }
        PlayerTurnEvent event = GameEvents.post(new PlayerTurnEvent(yaw, pitch), LogicalSide.CLIENT);
        original.call(player, event.yawDelta(), event.pitchDelta());
    }
}
