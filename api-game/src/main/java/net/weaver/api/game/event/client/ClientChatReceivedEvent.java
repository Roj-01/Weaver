package net.weaver.api.game.event.client;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.weaver.api.event.Event;

/**
 * Fired on the client when a chat message arrives, before it is shown. Check
 * {@link #kind()}: {@code PLAYER} messages come through
 * {@code ChatListener#showMessageToPlayer}, {@code SYSTEM} and
 * {@code OVERLAY} (action bar) messages through
 * {@code ChatListener#handleSystemMessage}. {@link #setMessage(Component)}
 * shows different text. Cancelable: a canceled message is not shown.
 */
public final class ClientChatReceivedEvent extends Event {
    /** Where the message came from. */
    public enum Kind {
        PLAYER,
        SYSTEM,
        OVERLAY
    }

    private final Kind kind;
    private final UUID sender;
    private Component message;

    public ClientChatReceivedEvent(Kind kind, Component message, UUID sender) {
        super(true);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.message = message;
        this.sender = sender;
    }

    public Kind kind() {
        return kind;
    }

    /** The sender's profile id for player messages, otherwise {@code null}. */
    public UUID sender() {
        return sender;
    }

    public Component message() {
        return message;
    }

    public void setMessage(Component message) {
        this.message = Objects.requireNonNull(message, "message");
    }
}
