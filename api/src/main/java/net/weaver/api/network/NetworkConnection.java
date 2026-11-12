package net.weaver.api.network;

/**
 * One open Weaver connection between a client and a server.
 *
 * <p>Implementations are provided by the transport, not by mods. A connection
 * object remains readable after it closes so listeners can inspect its side,
 * phase, and player, but sending on a closed connection fails.</p>
 */
public interface NetworkConnection extends AutoCloseable
{
    /**
     * The local side of this connection.
     *
     * @return the local side, never {@code null}
     */
    NetworkSide side();

    /**
     * The current phase of this connection.
     *
     * @return the current phase, never {@code null}
     */
    NetworkPhase phase();

    /**
     * The player associated with the other end of this connection.
     *
     * @return the player, never {@code null}
     */
    NetworkPlayer player();

    /**
     * Returns whether the connection can currently send and receive payloads.
     *
     * @return {@code true} while the connection is open
     */
    boolean isOpen();

    /**
     * Sends one payload to the other end.
     *
     * <p>This method is safe to call from the game thread. It rejects types
     * that are not registered for the outgoing direction and current phase,
     * throws {@link NetworkException} when the connection is closed or the
     * encoded payload exceeds {@link NetworkProtocol#MAX_PAYLOAD_BYTES}, and
     * validates the payload eagerly before it is queued. Implementations do not
     * block; excess traffic is subject to
     * {@link NetworkProtocol#MAX_QUEUE_DEPTH}.</p>
     *
     * @param type    the registered payload type, not {@code null}
     * @param payload the payload to encode, not {@code null}
     * @param <T>     the payload type
     * @throws NullPointerException when an argument is {@code null}
     * @throws NetworkException     when the connection is closed, the channel is
     *                              not registered for this direction and phase,
     *                              or the encoded payload is too large
     */
    <T> void send(PayloadType<T> type, T payload);

    /**
     * Requests an orderly disconnect with a human-readable reason.
     *
     * <p>The reason should be non-blank and at most
     * {@link NetworkProtocol#MAX_REASON_CHARS} chars. Calling this method on an
     * already closed connection has no effect.</p>
     *
     * @param reason the disconnect reason, not {@code null}
     * @throws NullPointerException when {@code reason} is {@code null}
     */
    void disconnect(String reason);

    /**
     * Closes the connection immediately.
     *
     * <p>This override removes the checked exception from
     * {@link AutoCloseable#close()}. The method is idempotent.</p>
     */
    @Override
    void close();
}
