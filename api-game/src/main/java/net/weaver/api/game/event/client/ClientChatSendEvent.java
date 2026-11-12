package net.weaver.api.game.event.client;

import java.util.Objects;
import net.weaver.api.event.Event;

/**
 * Fired on the client before a chat message (not a command) is sent to the
 * server ({@code ClientPacketListener#sendChat}). {@link #setMessage(String)}
 * sends different text. Cancelable: a canceled message is not sent.
 */
public final class ClientChatSendEvent extends Event {
    private final String originalMessage;
    private String message;

    public ClientChatSendEvent(String message) {
        super(true);
        this.originalMessage = message;
        this.message = message;
    }

    public String originalMessage() {
        return originalMessage;
    }

    public String message() {
        return message;
    }

    public void setMessage(String message) {
        this.message = Objects.requireNonNull(message, "message");
    }
}
