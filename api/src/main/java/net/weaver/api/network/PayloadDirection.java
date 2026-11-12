package net.weaver.api.network;

import java.util.Objects;

/**
 * The travel direction of a payload across a connection.
 *
 * <p>Directions are named from the client's point of view: a
 * {@link #CLIENTBOUND} payload travels from the server to a client, while a
 * {@link #SERVERBOUND} payload travels from a client to the server.</p>
 */
public enum PayloadDirection
{
    /** A payload sent by the server and received by a client. */
    CLIENTBOUND,

    /** A payload sent by a client and received by the server. */
    SERVERBOUND;

    /**
     * The side that receives payloads travelling in this direction.
     *
     * @return {@link NetworkSide#CLIENT} for {@link #CLIENTBOUND} and
     *         {@link NetworkSide#SERVER} for {@link #SERVERBOUND}
     */
    public NetworkSide destination()
    {
        return this == CLIENTBOUND ? NetworkSide.CLIENT : NetworkSide.SERVER;
    }

    /**
     * The side that sends payloads travelling in this direction.
     *
     * @return {@link NetworkSide#SERVER} for {@link #CLIENTBOUND} and
     *         {@link NetworkSide#CLIENT} for {@link #SERVERBOUND}
     */
    public NetworkSide source()
    {
        return destination().opposite();
    }

    /**
     * The direction that travels the other way.
     *
     * @return {@link #SERVERBOUND} for a clientbound direction and
     *         {@link #CLIENTBOUND} for a serverbound direction
     */
    public PayloadDirection opposite()
    {
        return this == CLIENTBOUND ? SERVERBOUND : CLIENTBOUND;
    }

    /**
     * Derives the payload direction from its source and destination.
     *
     * @param source      the sending side, not {@code null}
     * @param destination the receiving side, not {@code null}
     * @return the matching direction, never {@code null}
     * @throws NullPointerException     when either argument is {@code null}
     * @throws IllegalArgumentException when both arguments are the same side
     */
    public static PayloadDirection of(NetworkSide source, NetworkSide destination)
    {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (source == destination)
        {
            throw new IllegalArgumentException("Payload source and destination must differ but both were "
                    + source);
        }
        return destination == NetworkSide.CLIENT ? CLIENTBOUND : SERVERBOUND;
    }
}
