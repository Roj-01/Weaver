package net.weaver.api.game.network;

import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.PayloadBuffer;

import java.util.Objects;

/**
 * One versioned envelope carried inside a {@link GamePayload}.
 *
 * <p>The envelope is the bridge's own wire format. It is independent of the
 * Minecraft packet codec and is encoded with the API
 * {@link PayloadBuffer}, so strings use strict UTF-8, byte arrays have
 * validated varint lengths, and every read is bounded before allocation.</p>
 *
 * <p>Every envelope writes the same fields in the same order:
 * an envelope version byte, the {@link Kind} ordinal, the Weaver protocol
 * version, the connection phase ordinal, the channel id, the channel payload,
 * the handshake body, and a reason string. Kind-specific validation rejects a
 * message that carries a field its kind does not use. A malformed envelope
 * always throws {@link NetworkException}; callers must catch it at the game
 * boundary and treat it as a protocol failure, never let it escape a Mixin.</p>
 *
 * @see GamePayload
 * @see BridgeSession
 */
public final class BridgeEnvelope
{
    /**
     * The envelope wire version.
     */
    public static final int ENVELOPE_VERSION = 1;

    /**
     * The maximum channel-id length, shared with the API protocol.
     */
    public static final int MAX_CHANNEL_CHARS = NetworkProtocol.MAX_CHANNEL_CHARS;

    /**
     * The maximum handshake body length.
     *
     * <p>A handshake request carries up to 128 mods and 512 channel ids. This
     * bound is larger than any valid request or response produced by the API
     * codecs.</p>
     */
    public static final int MAX_HANDSHAKE_BYTES = 131_072;

    /**
     * The maximum channel payload length.
     *
     * <p>This leaves four kilobytes below {@link GamePayload#MAX_BRIDGE_BODY}
     * for the envelope header, the channel id, and the reason string.</p>
     */
    public static final int MAX_PAYLOAD_BYTES = GamePayload.MAX_BRIDGE_BODY - 4_096;

    /**
     * The maximum reason length, shared with the API protocol.
     */
    public static final int MAX_REASON_CHARS = NetworkProtocol.MAX_REASON_CHARS;

    /**
     * The maximum complete envelope length.
     */
    public static final int MAX_ENVELOPE_BYTES = GamePayload.MAX_BRIDGE_BODY;

    private static final Kind[] KINDS = Kind.values();
    private static final NetworkPhase[] PHASES = NetworkPhase.values();

    private final Kind kind;
    private final int protocolVersion;
    private final NetworkPhase phase;
    private final String channel;
    private final byte[] payload;
    private final byte[] handshake;
    private final String reason;

    private BridgeEnvelope(Kind kind, int protocolVersion, NetworkPhase phase, String channel,
                           byte[] payload, byte[] handshake, String reason)
    {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.phase = Objects.requireNonNull(phase, "phase");
        if (protocolVersion < 1)
        {
            throw new NetworkException("bridge protocol version must be at least 1 but was "
                    + protocolVersion);
        }
        this.protocolVersion = protocolVersion;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.payload = Objects.requireNonNull(payload, "payload").clone();
        this.handshake = Objects.requireNonNull(handshake, "handshake").clone();
        this.reason = Objects.requireNonNull(reason, "reason");
        validate();
    }

    /**
     * Creates a handshake envelope.
     *
     * @param kind            {@link Kind#HELLO}, {@link Kind#REQUEST}, or
     *                        {@link Kind#RESPONSE}
     * @param protocolVersion the positive Weaver protocol version
     * @param phase           the phase that produced the handshake
     * @param handshake       the encoded {@code HandshakeRequest} or
     *                        {@code HandshakeResponse}
     * @return the envelope, never {@code null}
     * @throws NullPointerException when an argument is {@code null}
     * @throws NetworkException     when the kind is not a handshake kind or the
     *                              body is empty or too large
     */
    public static BridgeEnvelope handshake(Kind kind, int protocolVersion, NetworkPhase phase,
                                           byte[] handshake)
    {
        Objects.requireNonNull(kind, "kind");
        if (kind != Kind.HELLO && kind != Kind.REQUEST && kind != Kind.RESPONSE)
        {
            throw new NetworkException("kind " + kind + " is not a handshake envelope");
        }
        return new BridgeEnvelope(kind, protocolVersion, phase, "", new byte[0], handshake, "");
    }

    /**
     * Creates a channel payload envelope.
     *
     * @param protocolVersion the positive Weaver protocol version
     * @param phase           the phase in which the payload was sent
     * @param channel         the full versioned channel id, not blank
     * @param payload         the codec-encoded payload bytes, not {@code null}
     * @return the envelope, never {@code null}
     * @throws NullPointerException when an argument is {@code null}
     * @throws NetworkException     when the channel or payload violates a bound
     */
    public static BridgeEnvelope payload(int protocolVersion, NetworkPhase phase, String channel,
                                         byte[] payload)
    {
        return new BridgeEnvelope(Kind.PAYLOAD, protocolVersion, phase, channel, payload,
                new byte[0], "");
    }

    /**
     * Creates a disconnect envelope.
     *
     * @param protocolVersion the positive Weaver protocol version
     * @param phase           the phase in which the disconnect occurred
     * @param reason          the disconnect reason, possibly blank
     * @return the envelope, never {@code null}
     * @throws NullPointerException when an argument is {@code null}
     * @throws NetworkException     when the reason is too long
     */
    public static BridgeEnvelope disconnect(int protocolVersion, NetworkPhase phase,
                                            String reason)
    {
        return new BridgeEnvelope(Kind.DISCONNECT, protocolVersion, phase, "",
                new byte[0], new byte[0], reason);
    }

    /**
     * Decodes one complete envelope.
     *
     * <p>The envelope version, kind, phase, and every length prefix are checked
     * before the corresponding value is read. Trailing bytes are rejected.</p>
     *
     * @param data the encoded envelope, not {@code null}
     * @return the decoded envelope, never {@code null}
     * @throws NullPointerException when {@code data} is {@code null}
     * @throws NetworkException     when the input is truncated, has an unknown
     *                              version or kind, carries an oversized value,
     *                              or contains trailing bytes
     */
    public static BridgeEnvelope decode(byte[] data)
    {
        Objects.requireNonNull(data, "data");
        PayloadBuffer in = PayloadBuffer.reader(data);
        int envelopeVersion = in.readUnsignedByte();
        if (envelopeVersion != ENVELOPE_VERSION)
        {
            throw new NetworkException("unsupported bridge envelope version: "
                    + envelopeVersion);
        }
        int kindOrdinal = in.readUnsignedByte();
        if (kindOrdinal >= KINDS.length)
        {
            throw new NetworkException("unknown bridge envelope kind ordinal: " + kindOrdinal);
        }
        Kind kind = KINDS[kindOrdinal];
        int protocolVersion = in.readVarInt();
        int phaseOrdinal = in.readUnsignedByte();
        if (phaseOrdinal >= PHASES.length)
        {
            throw new NetworkException("unknown bridge phase ordinal: " + phaseOrdinal);
        }
        NetworkPhase phase = PHASES[phaseOrdinal];
        String channel = in.readString(MAX_CHANNEL_CHARS);
        byte[] payload = in.readByteArray(MAX_PAYLOAD_BYTES);
        byte[] handshake = in.readByteArray(MAX_HANDSHAKE_BYTES);
        String reason = in.readString(MAX_REASON_CHARS);
        in.ensureFullyRead();
        return new BridgeEnvelope(kind, protocolVersion, phase, channel, payload, handshake,
                reason);
    }

    /**
     * Encodes this envelope.
     *
     * @return the encoded bytes, never {@code null}
     * @throws NetworkException when the encoded form exceeds
     *                          {@link #MAX_ENVELOPE_BYTES}
     */
    public byte[] encode()
    {
        PayloadBuffer out = PayloadBuffer.writer(64 + payload.length + handshake.length
                + channel.length() + reason.length());
        out.writeByte(ENVELOPE_VERSION);
        out.writeByte(kind.ordinal());
        out.writeVarInt(protocolVersion);
        out.writeByte(phase.ordinal());
        out.writeString(channel, MAX_CHANNEL_CHARS);
        out.writeByteArray(payload, MAX_PAYLOAD_BYTES);
        out.writeByteArray(handshake, MAX_HANDSHAKE_BYTES);
        out.writeString(reason, MAX_REASON_CHARS);
        byte[] encoded = out.toByteArray();
        if (encoded.length > MAX_ENVELOPE_BYTES)
        {
            throw new NetworkException("encoded bridge envelope of " + encoded.length
                    + " bytes exceeds the maximum of " + MAX_ENVELOPE_BYTES);
        }
        return encoded;
    }

    /**
     * The envelope kind.
     *
     * @return the kind, never {@code null}
     */
    public Kind kind()
    {
        return kind;
    }

    /**
     * The Weaver protocol version advertised by the sender.
     *
     * @return the positive protocol version
     */
    public int protocolVersion()
    {
        return protocolVersion;
    }

    /**
     * The connection phase that produced the envelope.
     *
     * @return the phase, never {@code null}
     */
    public NetworkPhase phase()
    {
        return phase;
    }

    /**
     * The full versioned channel id, or an empty string for non-payload kinds.
     *
     * @return the channel id, never {@code null}
     */
    public String channel()
    {
        return channel;
    }

    /**
     * A copy of the codec-encoded channel payload.
     *
     * @return a new byte array, never {@code null}
     */
    public byte[] payload()
    {
        return payload.clone();
    }

    /**
     * A copy of the encoded handshake request or response.
     *
     * @return a new byte array, never {@code null}
     */
    public byte[] handshake()
    {
        return handshake.clone();
    }

    /**
     * The disconnect reason, or an empty string for other kinds.
     *
     * @return the reason, never {@code null}
     */
    public String reason()
    {
        return reason;
    }

    /**
     * An upper-bound estimate of the encoded size, used for queue accounting.
     *
     * @return a positive estimated byte count
     */
    public int estimatedBytes()
    {
        long estimate = 32L + channel.length() + payload.length + handshake.length
                + (long) reason.length() * 3L;
        return estimate > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) estimate;
    }

    private void validate()
    {
        if (channel.length() > MAX_CHANNEL_CHARS)
        {
            throw new NetworkException("bridge channel of " + channel.length()
                    + " chars exceeds the limit of " + MAX_CHANNEL_CHARS);
        }
        if (payload.length > MAX_PAYLOAD_BYTES)
        {
            throw new NetworkException("bridge payload of " + payload.length
                    + " bytes exceeds the limit of " + MAX_PAYLOAD_BYTES);
        }
        if (handshake.length > MAX_HANDSHAKE_BYTES)
        {
            throw new NetworkException("bridge handshake of " + handshake.length
                    + " bytes exceeds the limit of " + MAX_HANDSHAKE_BYTES);
        }
        if (reason.length() > MAX_REASON_CHARS)
        {
            throw new NetworkException("bridge reason of " + reason.length()
                    + " chars exceeds the limit of " + MAX_REASON_CHARS);
        }
        switch (kind)
        {
            case HELLO, REQUEST, RESPONSE ->
            {
                if (handshake.length == 0)
                {
                    throw new NetworkException(kind + " envelope is missing its handshake body");
                }
                if (!channel.isEmpty() || payload.length != 0 || !reason.isEmpty())
                {
                    throw new NetworkException(kind + " envelope carries unused fields");
                }
            }
            case PAYLOAD ->
            {
                if (channel.isBlank())
                {
                    throw new NetworkException("PAYLOAD envelope is missing its channel");
                }
                if (handshake.length != 0 || !reason.isEmpty())
                {
                    throw new NetworkException("PAYLOAD envelope carries unused fields");
                }
            }
            case DISCONNECT ->
            {
                if (!channel.isEmpty() || payload.length != 0 || handshake.length != 0)
                {
                    throw new NetworkException("DISCONNECT envelope carries unused fields");
                }
            }
        }
    }

    /**
     * The bridge envelope kinds.
     */
    public enum Kind
    {
        /** Server to client capability announcement. */
        HELLO,

        /** Client to server handshake request. */
        REQUEST,

        /** Server to client handshake response. */
        RESPONSE,

        /** One channel payload. */
        PAYLOAD,

        /** An orderly bridge disconnect. */
        DISCONNECT
    }
}
