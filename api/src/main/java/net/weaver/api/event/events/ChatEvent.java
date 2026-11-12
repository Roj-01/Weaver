package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/** A player chat message is about to be broadcast. */
public class ChatEvent extends Event {

    private final Object playerList;
    private final Object message;
    private final Object sender;

    public ChatEvent(Object playerList, Object message, Object sender) {
        this(playerList, message, sender, false);
    }

    /**
     * Creates a chat event with an explicit cancelable flag.
     *
     * <p>The loader uses the cancelable form for the broadcast hook; the
     * three-argument constructor remains observation-only for binary
     * compatibility.
     *
     * @param playerList the player list broadcasting the message
     * @param message    the chat message
     * @param sender     the sending player
     * @param cancelable whether listeners may cancel the broadcast
     */
    public ChatEvent(Object playerList, Object message, Object sender, boolean cancelable) {
        super(cancelable);
        this.playerList = Objects.requireNonNull(playerList, "playerList");
        this.message = Objects.requireNonNull(message, "message");
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    public Object playerList() {
        return playerList;
    }

    public Object message() {
        return message;
    }

    public Object sender() {
        return sender;
    }
}
