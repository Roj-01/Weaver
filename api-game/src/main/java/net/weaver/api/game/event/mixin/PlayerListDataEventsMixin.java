package net.weaver.api.game.event.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.player.DatapackSyncEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Datapack data sync points: a player joined, or every player after a reload. */
@Mixin(PlayerList.class)
public abstract class PlayerListDataEventsMixin {
    @Inject(method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At("RETURN"))
    private void weaver$joinSync(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo callback) {
        GameEvents.post(new DatapackSyncEvent((PlayerList) (Object) this, player), LogicalSide.SERVER);
    }

    @Inject(method = "reloadResources()V", at = @At("RETURN"))
    private void weaver$reloadSync(CallbackInfo callback) {
        GameEvents.post(new DatapackSyncEvent((PlayerList) (Object) this, null), LogicalSide.SERVER);
    }
}
