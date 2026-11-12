package net.weaver.api.network;

/**
 * Protocol-wide constants shared by every Weaver network participant.
 *
 * <p>These values are part of the wire contract. Changing any of them requires
 * a new {@link #VERSION}, because peers with different limits can produce
 * frames that the other side rejects. Channel identifiers are namespaced by
 * mod id and versioned with a positive integer, for example
 * {@code example:telemetry/v1}.</p>
 */
public final class NetworkProtocol
{
    /**
     * The protocol name advertised during login.
     */
    public static final String NAME = "weaver-network";

    /**
     * The current Weaver network protocol version.
     */
    public static final int VERSION = 1;

    /**
     * The default channel version used by {@link PayloadType#of(String, String, PayloadCodec)}.
     */
    public static final int CHANNEL_VERSION = 1;

    /**
     * The largest encoded payload, in bytes, that a peer may send.
     */
    public static final int MAX_PAYLOAD_BYTES = 1_048_576;

    /**
     * The largest complete frame, in bytes, including the payload and framing
     * overhead.
     */
    public static final int MAX_FRAME_BYTES = MAX_PAYLOAD_BYTES + 4096;

    /**
     * The maximum number of frames a transport may queue for one connection.
     */
    public static final int MAX_QUEUE_DEPTH = 1024;

    /**
     * The default maximum text length, in Java chars, for a payload string.
     */
    public static final int MAX_STRING_CHARS = 256;

    /**
     * The maximum length, in chars, of a versioned channel identifier.
     */
    public static final int MAX_CHANNEL_CHARS = 160;

    /**
     * The maximum number of mods listed in a handshake.
     */
    public static final int MAX_MODS = 1024;

    /**
     * The maximum length, in chars, of a mod id.
     */
    public static final int MAX_MOD_ID_CHARS = 128;

    /**
     * The maximum length, in chars, of a mod version string.
     */
    public static final int MAX_MOD_VERSION_CHARS = 64;

    /**
     * The maximum number of channels listed in a handshake.
     */
    public static final int MAX_CHANNELS = 4096;

    /**
     * The maximum length, in chars, of a disconnect or rejection reason.
     */
    public static final int MAX_REASON_CHARS = 512;

    /**
     * The maximum length, in chars, of a player name.
     */
    public static final int MAX_PLAYER_NAME_CHARS = 64;

    private NetworkProtocol()
    {
        throw new AssertionError("No NetworkProtocol instances");
    }
}
