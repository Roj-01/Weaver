package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ClientChatSendEvent;
import net.weaver.api.game.event.client.ClientPlayerNetworkEvent;
import net.weaver.api.game.event.client.ClientTagsUpdatedEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Outgoing chat, joining, respawning, and tag updates. The packet handlers
 * first re-queue themselves onto the client thread (which throws on the
 * network thread), so the {@code RETURN} hooks run once, on the client thread.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketEventsMixin {
    @Unique
    private static final ThreadLocal<Object> weaver$reinvoking = new ThreadLocal<>();

    @Unique
    private LocalPlayer weaver$playerBeforeRespawn;

    @Inject(method = "sendChat(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$sendChat(String message, CallbackInfo callback) {
        if (weaver$reinvoking.get() == this) {
            return;
        }
        ClientChatSendEvent event = GameEvents.post(new ClientChatSendEvent(message), LogicalSide.CLIENT);
        if (GameEvents.canceled(event)) {
            callback.cancel();
            return;
        }
        // A listener can make the message too long (the packet holds 256 characters; a longer
        // one disconnects the player) or add characters the server rejects.
        String changed = event.message() == null ? "" : net.minecraft.util.StringUtil.trimChatMessage(
                net.minecraft.util.StringUtil.filterText(event.message()));
        if (changed.isBlank()) {
            callback.cancel();
            return;
        }
        if (!changed.equals(message)) {
            callback.cancel();
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                ((ClientPacketListener) (Object) this).sendChat(changed);
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }

    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V", at = @At("RETURN"))
    private void weaver$login(ClientboundLoginPacket packet, CallbackInfo callback) {
        GameEvents.post(new ClientPlayerNetworkEvent(ClientPlayerNetworkEvent.Kind.LOGGED_IN, null,
                Minecraft.getInstance().player), LogicalSide.CLIENT);
    }

    @Inject(method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V", at = @At("HEAD"))
    private void weaver$respawnStart(ClientboundRespawnPacket packet, CallbackInfo callback) {
        weaver$playerBeforeRespawn = Minecraft.getInstance().player;
    }

    @Inject(method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V", at = @At("RETURN"))
    private void weaver$respawn(ClientboundRespawnPacket packet, CallbackInfo callback) {
        LocalPlayer before = weaver$playerBeforeRespawn;
        weaver$playerBeforeRespawn = null;
        LocalPlayer after = Minecraft.getInstance().player;
        if (after != null && after != before) {
            GameEvents.post(new ClientPlayerNetworkEvent(ClientPlayerNetworkEvent.Kind.RESPAWNED, before, after),
                    LogicalSide.CLIENT);
        }
    }

    @Inject(method = "handleUpdateTags(Lnet/minecraft/network/protocol/common/ClientboundUpdateTagsPacket;)V", at = @At("RETURN"))
    private void weaver$tags(ClientboundUpdateTagsPacket packet, CallbackInfo callback) {
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        GameEvents.post(new ClientTagsUpdatedEvent(self.registryAccess(), self.getConnection().isMemoryConnection()),
                LogicalSide.CLIENT);
    }
}
