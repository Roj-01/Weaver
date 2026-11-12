package net.weaver.api.game.network.mixin;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.weaver.api.game.network.GameNetworkBridge;
import net.weaver.api.game.network.GamePayload;
import net.weaver.api.network.NetworkSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dispatches serverbound {@link GamePayload} packets received by the play
 * listener.
 *
 * <p>26.3 declares an empty
 * {@code ServerGamePacketListenerImpl.handleCustomPayload(ServerboundCustomPayloadPacket)}
 * that overrides the common listener. The bridge needs this second injection
 * because the play-phase override never calls the common method. The injection
 * runs on the server thread, hands the payload to the session, and cancels
 * vanilla handling.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin
{
    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/"
                    + "ServerboundCustomPayloadPacket;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void weaverBridgeHandleGameServerbound(ServerboundCustomPayloadPacket packet,
                                                   CallbackInfo callback)
    {
        try
        {
            if (packet == null || !(packet.payload() instanceof GamePayload payload))
            {
                return;
            }
            ServerCommonPacketListenerAccessor accessor =
                    (ServerCommonPacketListenerAccessor) (Object) this;
            PacketUtils.ensureRunningOnSameThread(packet,
                    (ServerCommonPacketListener) (Object) this,
                    accessor.weaver$server().packetProcessor());
            GameNetworkBridge.onInbound(accessor.weaver$connection(),
                    (PacketListener) (Object) this, payload, NetworkSide.SERVER);
            callback.cancel();
        }
        catch (RunningOnDifferentThreadException reschedule)
        {
            throw reschedule;
        }
        catch (Throwable failure)
        {
            GameNetworkBridge.logFailure("ServerGamePacketListener.handleCustomPayload",
                    failure);
        }
    }
}
