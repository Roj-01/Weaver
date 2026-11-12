package net.weaver.api.event.events.network;

import net.weaver.api.event.Event;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.NetworkSide;

import java.util.Objects;

/**
 * Posted when a connection closes, whether gracefully or not.
 *
 * <p>The event is observational and not cancelable. The supplied connection may
 * already be closed and must not be used to send payloads; inspect its side and
 * player, then release any per-connection state. The logical side is derived
 * from {@link NetworkConnection#side()}.</p>
 */
public final class NetworkDisconnectedEvent extends Event
{
    private final NetworkConnection connection;
    private final String reason;
    private final boolean graceful;

    /**
     * Creates a disconnected event.
     *
     * @param connection the closed or closing connection, not {@code null}
     * @param reason     the disconnect reason, not {@code null}, possibly empty
     * @param graceful   whether the disconnect was requested rather than caused
     *                   by a transport failure
     * @throws NullPointerException     when {@code connection} or {@code reason}
     *                                  is {@code null}
     * @throws IllegalArgumentException when {@code reason} exceeds
     *                                  {@link NetworkProtocol#MAX_REASON_CHARS}
     */
    public NetworkDisconnectedEvent(NetworkConnection connection, String reason,
                                    boolean graceful)
    {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.reason = Objects.requireNonNull(reason, "reason");
        if (reason.length() > NetworkProtocol.MAX_REASON_CHARS)
        {
            throw new IllegalArgumentException("reason must be at most "
                    + NetworkProtocol.MAX_REASON_CHARS + " chars but was "
                    + reason.length());
        }
        this.graceful = graceful;
        setLogicalSide(toLogicalSide(connection.side()));
    }

    /**
     * The connection that closed.
     *
     * @return the connection, never {@code null}
     */
    public NetworkConnection connection()
    {
        return connection;
    }

    /**
     * The disconnect reason.
     *
     * @return the reason, never {@code null}, possibly empty
     */
    public String reason()
    {
        return reason;
    }

    /**
     * Whether the disconnect was graceful.
     *
     * @return {@code true} when the peer or transport requested an orderly
     *         close, {@code false} for a failure or timeout
     */
    public boolean graceful()
    {
        return graceful;
    }

    /**
     * The local side of the closed connection.
     *
     * @return the local side, never {@code null}
     */
    public NetworkSide side()
    {
        return connection.side();
    }

    /**
     * The player associated with the other end of the connection.
     *
     * @return the player, never {@code null}
     */
    public NetworkPlayer player()
    {
        return connection.player();
    }

    private static LogicalSide toLogicalSide(NetworkSide side)
    {
        return side == NetworkSide.CLIENT ? LogicalSide.CLIENT : LogicalSide.SERVER;
    }
}
