package net.weaver.api.game.network.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.weaver.api.game.network.GameNetworkBridge;
import net.weaver.api.game.network.GamePayload;
import net.weaver.api.network.NetworkSide;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dispatches serverbound {@link GamePayload} packets received by the common
 * listener.
 *
 * <p>In 26.3
 * {@code ServerCommonPacketListenerImpl.handleCustomPayload(ServerboundCustomPayloadPacket)}
 * has an empty body, so the bridge must own the packet. This head injection
 * schedules the packet onto the server thread when needed, hands the payload to
 * the session, and cancels vanilla handling.</p>
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerMixin
{
    @Shadow
    @Final
    protected MinecraftServer server;

    @Shadow
    @Final
    protected Connection connection;

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/"
                    + "ServerboundCustomPayloadPacket;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void weaverBridgeHandleServerbound(ServerboundCustomPayloadPacket packet,
                                               CallbackInfo callback)
    {
        try
        {
            if (packet == null || !(packet.payload() instanceof GamePayload payload))
            {
                return;
            }
            try
            {
                PacketUtils.ensureRunningOnSameThread(packet,
                        (ServerCommonPacketListener) (Object) this,
                        server.packetProcessor());
            }
            catch (RunningOnDifferentThreadException reschedule)
            {
                throw reschedule;
            }
            GameNetworkBridge.onInbound(connection, (PacketListener) (Object) this,
                    payload, NetworkSide.SERVER);
            callback.cancel();
        }
        catch (RunningOnDifferentThreadException reschedule)
        {
            throw reschedule;
        }
        catch (Throwable failure)
        {
            GameNetworkBridge.logFailure("ServerCommonPacketListener.handleCustomPayload",
                    failure);
        }
    }
}
