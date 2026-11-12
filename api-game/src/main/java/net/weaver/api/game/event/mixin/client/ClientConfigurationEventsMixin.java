package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ClientTagsUpdatedEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tags received while joining. The configuration phase collects the tags and
 * builds the play-phase listener with the bound registries; the event fires
 * once that listener is installed on the connection.
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ClientConfigurationEventsMixin {
    @WrapOperation(method = "handleConfigurationFinished(Lnet/minecraft/network/protocol/configuration/ClientboundFinishConfigurationPacket;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V"))
    private void weaver$joinTags(Connection connection, ProtocolInfo<?> protocol, PacketListener listener, Operation<Void> original) {
        original.call(connection, protocol, listener);
        if (listener instanceof ClientPacketListener play) {
            GameEvents.post(new ClientTagsUpdatedEvent(play.registryAccess(), connection.isMemoryConnection()),
                    LogicalSide.CLIENT);
        }
    }
}
