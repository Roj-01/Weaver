package net.weaver.api.game.event.mixin;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.weaver.api.game.event.player.PlayerStartTrackingEvent;
import net.weaver.api.game.event.player.PlayerStopTrackingEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's client starting and stopping to track an entity. */
@Mixin(ServerEntity.class)
public abstract class ServerEntityEventsMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "addPairing(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
    private void weaver$startTracking(ServerPlayer player, CallbackInfo callback) {
        if (GameEvents.hasListeners(PlayerStartTrackingEvent.class)) {
            GameEvents.post(new PlayerStartTrackingEvent(player, entity), player.level());
        }
    }

    @Inject(method = "removePairing(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
    private void weaver$stopTracking(ServerPlayer player, CallbackInfo callback) {
        if (GameEvents.hasListeners(PlayerStopTrackingEvent.class)) {
            GameEvents.post(new PlayerStopTrackingEvent(player, entity), player.level());
        }
    }
}
