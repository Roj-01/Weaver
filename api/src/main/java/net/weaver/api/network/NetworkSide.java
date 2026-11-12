package net.weaver.api.network;

/**
 * One end of a Weaver network connection.
 *
 * <p>The side describes the process that owns the connection: a dedicated or
 * integrated server is {@link #SERVER}, and a game client is {@link #CLIENT}.
 * It is deliberately independent of any game's physical or logical side
 * types.</p>
 */
public enum NetworkSide
{
    /** The client end of a connection. */
    CLIENT,

    /** The server end of a connection. */
    SERVER;

    /**
     * Returns the other side.
     *
     * @return {@link #SERVER} for a client and {@link #CLIENT} for a server
     */
    public NetworkSide opposite()
    {
        return this == CLIENT ? SERVER : CLIENT;
    }

    /**
     * Returns whether this side is a client.
     *
     * @return {@code true} for {@link #CLIENT}, {@code false} for
     *         {@link #SERVER}
     */
    public boolean isClient()
    {
        return this == CLIENT;
    }
}
