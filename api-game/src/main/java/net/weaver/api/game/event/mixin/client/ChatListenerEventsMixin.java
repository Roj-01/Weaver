package net.weaver.api.game.event.mixin.client;

import com.mojang.authlib.GameProfile;
import java.time.Instant;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ClientChatReceivedEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Incoming player and system chat, before it is shown. */
@Mixin(ChatListener.class)
public abstract class ChatListenerEventsMixin {
    @Unique
    private static final ThreadLocal<Object> weaver$reinvoking = new ThreadLocal<>();

    @Inject(method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
    private void weaver$systemChat(Component message, boolean overlay, CallbackInfo callback) {
        if (weaver$reinvoking.get() == this || !GameEvents.hasListeners(ClientChatReceivedEvent.class)) {
            return;
        }
        ClientChatReceivedEvent event = GameEvents.post(new ClientChatReceivedEvent(
                overlay ? ClientChatReceivedEvent.Kind.OVERLAY : ClientChatReceivedEvent.Kind.SYSTEM, message, null),
                LogicalSide.CLIENT);
        if (GameEvents.canceled(event)) {
            callback.cancel();
        } else if (event.message() != message) {
            callback.cancel();
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                ((ChatListener) (Object) this).handleSystemMessage(event.message(), overlay);
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }

    @Inject(method = "showMessageToPlayer(Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/network/chat/Component;Lcom/mojang/authlib/GameProfile;ZLjava/time/Instant;)Z",
            at = @At("HEAD"), cancellable = true)
    private void weaver$playerChat(ChatType.Bound bound, PlayerChatMessage message, Component decorated, GameProfile sender,
                                   boolean onlyShowSecureChat, Instant received, CallbackInfoReturnable<Boolean> callback) {
        if (weaver$reinvoking.get() == this || !GameEvents.hasListeners(ClientChatReceivedEvent.class)) {
            return;
        }
        ClientChatReceivedEvent event = GameEvents.post(new ClientChatReceivedEvent(ClientChatReceivedEvent.Kind.PLAYER,
                decorated, sender == null ? null : sender.id()), LogicalSide.CLIENT);
        if (GameEvents.canceled(event)) {
            callback.setReturnValue(false);
        } else if (event.message() != decorated) {
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                callback.setReturnValue(((ChatListenerInvoker) (Object) this).weaverShowMessageToPlayer(bound, message, event.message(), sender, onlyShowSecureChat, received));
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }
}
