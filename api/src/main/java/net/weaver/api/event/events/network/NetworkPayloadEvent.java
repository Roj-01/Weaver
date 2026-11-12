package net.weaver.api.event.events.network;

import net.weaver.api.event.Event;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadDirection;
import net.weaver.api.network.PayloadType;

import java.util.Objects;

/**
 * Posted on the game thread before a registered payload handler runs.
 *
 * <p>Listeners can observe every decoded payload on the connection or cancel
 * the event to suppress the registered handler. Canceling is local to the
 * receiving side: it does not disconnect the peer and does not prevent other
 * listeners from inspecting the payload. The logical side is derived from
 * {@link NetworkConnection#side()}.</p>
 */
public final class NetworkPayloadEvent extends Event
{
    private final PayloadDirection direction;
    private final NetworkConnection connection;
    private final PayloadType<?> type;
    private final Object payload;

    /**
     * Creates a payload event.
     *
     * @param direction  the direction in which the payload travelled, not
     *                   {@code null}
     * @param connection the receiving connection, not {@code null}
     * @param type       the decoded payload type, not {@code null}
     * @param payload    the decoded payload, not {@code null}
     * @throws NullPointerException when an argument is {@code null}
     */
    public NetworkPayloadEvent(PayloadDirection direction, NetworkConnection connection,
                               PayloadType<?> type, Object payload)
    {
        super(true);
        this.direction = Objects.requireNonNull(direction, "direction");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.type = Objects.requireNonNull(type, "type");
        this.payload = Objects.requireNonNull(payload, "payload");
        setLogicalSide(toLogicalSide(connection.side()));
    }

    /**
     * The direction in which the payload travelled.
     *
     * @return the payload direction, never {@code null}
     */
    public PayloadDirection direction()
    {
        return direction;
    }

    /**
     * The connection that received the payload.
     *
     * @return the connection, never {@code null}
     */
    public NetworkConnection connection()
    {
        return connection;
    }

    /**
     * The decoded payload type.
     *
     * @return the payload type, never {@code null}
     */
    public PayloadType<?> type()
    {
        return type;
    }

    /**
     * The versioned channel id of the payload type.
     *
     * @return the channel id, never {@code null}
     */
    public String channel()
    {
        return type.channel();
    }

    /**
     * The decoded payload object.
     *
     * @return the payload, never {@code null}
     */
    public Object payload()
    {
        return payload;
    }

    /**
     * The local side that received the payload.
     *
     * @return the receiving side, never {@code null}
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

    /**
     * The phase in which the payload was received.
     *
     * @return the connection phase, never {@code null}
     */
    public NetworkPhase phase()
    {
        return connection.phase();
    }

    private static LogicalSide toLogicalSide(NetworkSide side)
    {
        return side == NetworkSide.CLIENT ? LogicalSide.CLIENT : LogicalSide.SERVER;
    }
}
