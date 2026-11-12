package net.weaver.api.network;

/**
 * Handles one decoded payload for a registered channel.
 *
 * <p>Handlers are invoked on the game thread. An exception thrown by a handler
 * is logged by the transport and is not rethrown onto the network thread; it
 * never disconnects the peer by itself. A handler must not block: do not wait
 * for I/O, other connections, or the game thread from inside it. If work is
 * expensive, hand it to a scheduler and return promptly.</p>
 *
 * @param <T> the payload type
 */
@FunctionalInterface
public interface PayloadHandler<T>
{
    /**
     * Handles one decoded payload.
     *
     * @param payload the decoded payload, not {@code null}
     * @param context the context of the connection that delivered the payload,
     *                not {@code null}
     */
    void handle(T payload, PayloadContext context);
}
