package net.weaver.api.game.network.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.RunningOnDifferentThreadException;
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
 * Dispatches clientbound {@link GamePayload} packets before vanilla handling.
 *
 * <p>Minecraft 26.3's
 * {@code ClientCommonPacketListenerImpl.handleCustomPayload(ClientboundCustomPayloadPacket)}
 * returns early for a {@code DiscardedPayload}, which is what an unregistered
 * id becomes. Because the bridge codec is registered during class
 * initialisation, a bridge payload arrives as a real {@link GamePayload}; this
 * head injection ensures the packet is processed on the game thread, hands the
 * body to the session, and cancels vanilla dispatch so the unknown-payload log
 * path is not used.</p>
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerMixin
{
    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    @Final
    protected Connection connection;

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/"
                    + "ClientboundCustomPayloadPacket;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void weaverBridgeHandleClientbound(ClientboundCustomPayloadPacket packet,
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
                        (ClientCommonPacketListener) (Object) this,
                        minecraft.packetProcessor());
            }
            catch (RunningOnDifferentThreadException reschedule)
            {
                throw reschedule;
            }
            GameNetworkBridge.onInbound(connection, (PacketListener) (Object) this,
                    payload, NetworkSide.CLIENT);
            callback.cancel();
        }
        catch (RunningOnDifferentThreadException reschedule)
        {
            throw reschedule;
        }
        catch (Throwable failure)
        {
            GameNetworkBridge.logFailure("ClientCommonPacketListener.handleCustomPayload",
                    failure);
        }
    }
}
