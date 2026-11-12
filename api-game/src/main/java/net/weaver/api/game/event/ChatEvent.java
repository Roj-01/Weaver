package net.weaver.api.game.event;

import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Typed variant of {@link net.weaver.api.event.events.ChatEvent}.
 *
 * <p>The payloads are narrowed to the game types. The event is cancelable so a
 * listener can stop the broadcast. The inherited accessors stay available with
 * their {@link Object} descriptors for binary compatibility.
 */
public final class ChatEvent extends net.weaver.api.event.events.ChatEvent {

    /**
     * Creates a cancelable typed chat event.
     *
     * @param playerList the player list broadcasting the message
     * @param message    the chat message
     * @param sender     the sending player
     */
    public ChatEvent(Object playerList, Object message, Object sender) {
        super(playerList, message, sender, true);
    }

    @Override
    public PlayerList playerList() {
        return (PlayerList) super.playerList();
    }

    @Override
    public PlayerChatMessage message() {
        return (PlayerChatMessage) super.message();
    }

    @Override
    public ServerPlayer sender() {
        return (ServerPlayer) super.sender();
    }
}
