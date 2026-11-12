package net.weaver.api.network;

/**
 * How a transport treats an incoming payload for an unregistered channel.
 *
 * <p>The policy applies after the handshake, when a peer sends a channel id
 * that has no registration for the inbound direction and current phase.</p>
 */
public enum UnknownChannelPolicy
{
    /**
     * Rejects the payload and disconnects the peer with the message
     * {@code unknown channel: <channel>}.
     */
    REJECT,

    /**
     * Logs the unknown channel and drops the payload without disconnecting.
     */
    IGNORE
}
