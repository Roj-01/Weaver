package net.weaver.api.game.network.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.weaver.api.game.network.GameNetworkBridge;
import net.weaver.api.network.NetworkPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the client's configuration outbound protocol as ready.
 *
 * <p>26.3's
 * {@code ClientHandshakePacketListenerImpl.handleLoginFinished} creates the
 * configuration listener, installs the inbound configuration protocol, sends
 * the login acknowledgement, installs the outbound configuration protocol, and
 * then sends the vanilla brand payload and client information. By the tail of
 * this method both protocol directions are configuration, so the bridge may
 * send its handshake request. The tail also supplies the real local player
 * identity from the Minecraft user.</p>
 */
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakePacketListenerMixin
{
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "handleLoginFinished(Lnet/minecraft/network/protocol/login/"
                    + "ClientboundLoginFinishedPacket;)V",
            at = @At("TAIL"))
    private void weaverBridgeLoginFinished(ClientboundLoginFinishedPacket packet,
                                           CallbackInfo callback)
    {
        try
        {
            NetworkPlayer player = null;
            // the server-assigned profile is what the server authenticates the
            // bridge request against (it differs from the local user's id on an
            // offline-mode server)
            GameProfile assigned = packet == null ? null : packet.gameProfile();
            if (assigned != null && assigned.id() != null)
            {
                try
                {
                    player = new NetworkPlayer(assigned.id(), assigned.name());
                }
                catch (RuntimeException invalidIdentity)
                {
                    GameNetworkBridge.logFailure("ClientHandshake.localPlayer",
                            invalidIdentity);
                }
            }
            User user = minecraft.getUser();
            if (player == null && user != null)
            {
                try
                {
                    player = new NetworkPlayer(user.getProfileId(), user.getName());
                }
                catch (RuntimeException invalidIdentity)
                {
                    GameNetworkBridge.logFailure("ClientHandshake.localPlayer",
                            invalidIdentity);
                }
            }
            GameNetworkBridge.onClientConfigurationReady(connection, player);
        }
        catch (Throwable failure)
        {
            GameNetworkBridge.logFailure("ClientHandshakePacketListener.handleLoginFinished",
                    failure);
        }
    }
}
