package net.weaver.api.game.network.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.weaver.api.game.network.GameNetworkBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the server side of a connection as ready for play.
 *
 * <p>26.3 sets the inbound protocol to {@code PLAY} inside
 * {@code PlayerList.placeNewPlayer} and resumes flushing before returning. A
 * tail injection therefore runs after the play listener is installed and after
 * the server has reached play, which is the point where a verified bridge
 * session can post {@code NetworkConnectedEvent} and drain its queue.</p>
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin
{
    @Inject(method = "placeNewPlayer(Lnet/minecraft/network/Connection;"
                    + "Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At("TAIL"))
    private void weaverBridgePlaceNewPlayer(Connection connection, ServerPlayer player,
                                            CommonListenerCookie cookie,
                                            CallbackInfo callback)
    {
        try
        {
            GameNetworkBridge.onPlayReady(connection);
        }
        catch (Throwable failure)
        {
            GameNetworkBridge.logFailure("PlayerList.placeNewPlayer", failure);
        }
    }
}
