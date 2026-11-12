package net.weaver.api.game.network.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.weaver.api.game.network.GameNetworkBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the client side of a connection as ready for play.
 *
 * <p>26.3's {@code ClientConfigurationPacketListenerImpl.handleConfigurationFinished}
 * switches the inbound and outbound protocols to {@code PLAY} and then receives
 * {@code ClientboundLoginPacket}. The tail of
 * {@code ClientPacketListener.handleLogin(ClientboundLoginPacket)} is therefore
 * the client equivalent of {@code PlayerList.placeNewPlayer}: the bridge posts
 * the connected event and drains payloads that arrived between the play switch
 * and the login packet.</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin
{
    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/"
                    + "ClientboundLoginPacket;)V",
            at = @At("TAIL"))
    private void weaverBridgeClientPlayReady(ClientboundLoginPacket packet,
                                             CallbackInfo callback)
    {
        try
        {
            GameNetworkBridge.onPlayReady(
                    ((ClientPacketListener) (Object) this).getConnection());
        }
        catch (Throwable failure)
        {
            GameNetworkBridge.logFailure("ClientPacketListener.handleLogin", failure);
        }
    }
}
