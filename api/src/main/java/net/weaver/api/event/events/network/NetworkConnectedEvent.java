package net.weaver.api.event.events.network;

import net.weaver.api.event.Event;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkSide;

import java.util.List;
import java.util.Objects;

/**
 * Posted once when a connection reaches {@link NetworkPhase#PLAY}.
 *
 * <p>The event carries the live connection and the mod list advertised by the
 * remote peer. It is not cancelable and has no result; listeners that need to
 * refuse a peer should do so during
 * {@link NetworkHandshakeEvent}. The logical side is derived from
 * {@link NetworkConnection#side()}.</p>
 */
public final class NetworkConnectedEvent extends Event
{
    private final NetworkConnection connection;
    private final List<NetworkModInfo> remoteMods;

    /**
     * Creates a connected event.
     *
     * @param connection the connection that reached play, not {@code null}
     * @param remoteMods the remote peer's mod identities, not {@code null}
     * @throws NullPointerException when an argument is {@code null} or the list
     *                              contains {@code null}
     */
    public NetworkConnectedEvent(NetworkConnection connection, List<NetworkModInfo> remoteMods)
    {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.remoteMods = List.copyOf(remoteMods);
        setLogicalSide(toLogicalSide(connection.side()));
    }

    /**
     * The connection that reached play.
     *
     * @return the connection, never {@code null}
     */
    public NetworkConnection connection()
    {
        return connection;
    }

    /**
     * The local side of the connection.
     *
     * @return the local side, never {@code null}
     */
    public NetworkSide side()
    {
        return connection.side();
    }

    /**
     * The current connection phase.
     *
     * @return the phase, never {@code null}
     */
    public NetworkPhase phase()
    {
        return connection.phase();
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

    /**
     * The remote peer's mod identities, in the order the peer advertised them.
     *
     * @return an immutable list, never {@code null}
     */
    public List<NetworkModInfo> remoteMods()
    {
        return remoteMods;
    }

    private static LogicalSide toLogicalSide(NetworkSide side)
    {
        return side == NetworkSide.CLIENT ? LogicalSide.CLIENT : LogicalSide.SERVER;
    }
}
