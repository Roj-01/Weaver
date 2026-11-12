package net.weaver.api.game.event.mixin;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.player.PermissionsChangedEvent;
import net.weaver.api.game.event.player.PlayerRespawnedEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Operator status changes ({@code op(NameAndId)} delegates to the three-argument
 * form) and the finished respawn.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListEventsMixin {
    @Inject(method = "op(Lnet/minecraft/server/players/NameAndId;Ljava/util/Optional;Ljava/util/Optional;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$op(NameAndId player, Optional<LevelBasedPermissionSet> permissions, Optional<Boolean> bypassLimit,
                           CallbackInfo callback) {
        PlayerList self = (PlayerList) (Object) this;
        LevelBasedPermissionSet granted = permissions.orElse(self.getServer().operatorUserPermissions());
        if (GameEvents.canceled(GameEvents.post(new PermissionsChangedEvent(player, granted, true), LogicalSide.SERVER))) {
            callback.cancel();
        }
    }

    @Inject(method = "deop(Lnet/minecraft/server/players/NameAndId;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$deop(NameAndId player, CallbackInfo callback) {
        if (GameEvents.canceled(GameEvents.post(
                new PermissionsChangedEvent(player, LevelBasedPermissionSet.ALL, false), LogicalSide.SERVER))) {
            callback.cancel();
        }
    }

    @Inject(method = "respawn(Lnet/minecraft/server/level/ServerPlayer;ZLnet/minecraft/world/entity/Entity$RemovalReason;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("RETURN"))
    private void weaver$respawned(ServerPlayer old, boolean endConquered, Entity.RemovalReason reason,
                                  CallbackInfoReturnable<ServerPlayer> callback) {
        ServerPlayer player = callback.getReturnValue();
        if (player != null && GameEvents.hasListeners(PlayerRespawnedEvent.class)) {
            GameEvents.post(new PlayerRespawnedEvent(player, endConquered), player.level());
        }
    }
}
