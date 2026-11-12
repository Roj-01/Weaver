package net.weaver.api.game.event.mixin.client;

import com.mojang.authlib.GameProfile;
import java.time.Instant;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Invoker for the private {@code ChatListener#showMessageToPlayer}, used to show a changed message. */
@Mixin(ChatListener.class)
public interface ChatListenerInvoker {
    @Invoker("showMessageToPlayer")
    boolean weaverShowMessageToPlayer(ChatType.Bound bound, PlayerChatMessage message, Component decorated,
                                      GameProfile sender, boolean onlyShowSecureChat, Instant received);
}
