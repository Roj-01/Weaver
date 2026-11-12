package net.weaver.api.game.network.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.weaver.api.game.network.GameNetworkBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors Minecraft 26.3 protocol swaps and disconnects into the Weaver bridge.
 *
 * <p>The verified targets are:</p>
 *
 * <ul>
 *   <li>{@code setupInboundProtocol(ProtocolInfo, PacketListener)V} at
 *       {@code TAIL}: create or update the session and mirror the new phase.</li>
 *   <li>{@code handleDisconnection()V} at {@code RETURN}: remove the session and
 *       post at most one disconnected event.</li>
 * </ul>
 *
 * <p>The listener argument is deliberately typed as the raw
 * {@link PacketListener}; the bridge derives the phase from
 * {@link PacketListener#protocol()} and the side from
 * {@link Connection#getReceiving()}.</p>
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin
{
    @Inject(method = "setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;"
                    + "Lnet/minecraft/network/PacketListener;)V",
            at = @At("TAIL"))
    private void weaverBridgeSetupInbound(ProtocolInfo<?> protocolInfo,
                                          PacketListener listener,
                                          CallbackInfo callback)
    {
        try
        {
            GameNetworkBridge.onPhaseInbound((Connection) (Object) this, listener,
                    GameNetworkBridge.phaseOf(protocolInfo));
        }
        catch (Throwable failure)
        {
            GameNetworkBridge.logFailure("Connection.setupInboundProtocol", failure);
        }
    }

    @Inject(method = "handleDisconnection()V", at = @At("RETURN"))
    private void weaverBridgeHandleDisconnection(CallbackInfo callback)
    {
        try
        {
            Connection connection = (Connection) (Object) this;
            if (connection.isConnected())
            {
                return;
            }
            GameNetworkBridge.onDisconnected(connection);
        }
        catch (Throwable failure)
        {
            GameNetworkBridge.logFailure("Connection.handleDisconnection", failure);
        }
    }
}
