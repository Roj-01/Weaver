package net.weaver.api.network;

/**
 * The connection context passed to a {@link PayloadHandler}.
 *
 * <p>{@link #side()} is the local receiving side, not the sender.
 * {@link #player()} is the remote player on a server and the local player on a
 * client, so a handler always sees the player that owns the other end of the
 * connection.</p>
 */
public interface PayloadContext
{
    /**
     * The connection that received the payload.
     *
     * @return the connection, never {@code null}
     */
    NetworkConnection connection();

    /**
     * The local side that received the payload.
     *
     * @return the receiving side, never {@code null}
     */
    NetworkSide side();

    /**
     * The phase in which the payload was delivered.
     *
     * @return the current phase, never {@code null}
     */
    NetworkPhase phase();

    /**
     * The player associated with the other end of the connection.
     *
     * <p>On a server this is the remote client; on a client this is the local
     * player.</p>
     *
     * @return the player, never {@code null}
     */
    NetworkPlayer player();

    /**
     * Sends a payload back in the direction opposite to the inbound payload.
     *
     * <p>On a server a reply to a serverbound payload is clientbound; on a
     * client a reply to a clientbound payload is serverbound. The reply is
     * subject to the same registration, phase, size, and open-state checks as
     * {@link NetworkConnection#send(PayloadType, Object)}.</p>
     *
     * @param type    the registered payload type, not {@code null}
     * @param payload the payload to send, not {@code null}
     * @param <T>     the payload type
     * @throws NetworkException when the reply cannot be sent
     */
    <T> void reply(PayloadType<T> type, T payload);
}
