package net.weaver.core.network;

import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkProtocol;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Objects;

/**
 * Binary framing for Weaver network connections.
 *
 * <p>Every frame starts with an eleven-byte header: a four-byte magic
 * {@code 0x57455632} ({@code WEV2}), a two-byte unsigned wire version, a
 * one-byte frame type, and a four-byte signed big-endian body length. The body
 * follows the header and may be empty. Frame bodies are bounded by
 * {@link NetworkProtocol#MAX_FRAME_BYTES}; a reader validates the length before
 * allocating the body so a hostile peer cannot force an unbounded allocation.
 * All multi-byte integers are big-endian.</p>
 *
 * <p>The class is package-private transport plumbing. It performs no logging,
 * event dispatch, or policy decisions and is safe to use from a single reader
 * thread per stream.</p>
 */
final class NetworkWire
{
    private static final int MAGIC = 0x57455632;
    private static final int WIRE_VERSION = 1;
    private static final int HEADER_BYTES = 11;

    private NetworkWire()
    {
        throw new AssertionError("No NetworkWire instances");
    }

    /**
     * Encodes one complete frame.
     *
     * @param type the frame type, not {@code null}
     * @param body the frame body, not {@code null}; the array is not retained
     * @return a new array containing the header and body, never {@code null}
     * @throws NullPointerException when an argument is {@code null}
     * @throws NetworkException     when the body exceeds
     *                              {@link NetworkProtocol#MAX_FRAME_BYTES}
     */
    static byte[] encode(FrameType type, byte[] body)
    {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(body, "body");
        if (body.length > NetworkProtocol.MAX_FRAME_BYTES)
        {
            throw new NetworkException("frame body of " + body.length
                    + " bytes exceeds the maximum of " + NetworkProtocol.MAX_FRAME_BYTES);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(HEADER_BYTES + body.length);
        DataOutputStream out = new DataOutputStream(bytes);
        try
        {
            out.writeInt(MAGIC);
            out.writeShort(WIRE_VERSION);
            out.writeByte(type.code());
            out.writeInt(body.length);
            out.write(body);
        }
        catch (IOException failure)
        {
            throw new NetworkException("failed to encode network frame", failure);
        }
        return bytes.toByteArray();
    }

    /**
     * Reads one complete frame from the supplied stream.
     *
     * <p>The header is read fully, its magic, wire version, type, and body
     * length are validated, and then exactly the declared number of body bytes
     * is read with {@link DataInputStream#readFully(byte[])}. A clean end of
     * stream before the first header byte propagates as {@link EOFException};
     * a truncated header or body is reported as an {@link IOException} with a
     * clear message.</p>
     *
     * @param in the source stream, not {@code null}
     * @return the decoded frame, never {@code null}
     * @throws NullPointerException when {@code in} is {@code null}
     * @throws NetworkException     when the magic, wire version, frame type, or
     *                              body length is invalid; the message names
     *                              the exact offending value
     * @throws IOException          when the stream ends cleanly before a frame
     *                              starts or truncates an in-progress frame
     */
    static WireFrame read(DataInputStream in) throws IOException
    {
        Objects.requireNonNull(in, "in");
        int magic = in.readInt();
        if (magic != MAGIC)
        {
            throw new NetworkException("invalid frame magic: 0x"
                    + Integer.toHexString(magic) + " (expected 0x57455632)");
        }
        try
        {
            int wireVersion = in.readUnsignedShort();
            if (wireVersion != WIRE_VERSION)
            {
                throw new NetworkException("unsupported wire version: " + wireVersion
                        + " (expected " + WIRE_VERSION + ")");
            }
            int code = in.readUnsignedByte();
            FrameType type = FrameType.fromCode(code);
            int length = in.readInt();
            if (length < 0)
            {
                throw new NetworkException("negative frame body length: " + length);
            }
            if (length > NetworkProtocol.MAX_FRAME_BYTES)
            {
                throw new NetworkException("frame body length " + length
                        + " exceeds the maximum of " + NetworkProtocol.MAX_FRAME_BYTES);
            }
            byte[] body = new byte[length];
            in.readFully(body);
            return new WireFrame(type, body);
        }
        catch (EOFException failure)
        {
            throw new IOException("truncated network frame", failure);
        }
    }

    /**
     * The frame type codes defined by wire version {@code 1}.
     */
    enum FrameType
    {
        /** The client's opening handshake request. */
        HELLO(1),

        /** The server's handshake response. */
        HANDSHAKE(2),

        /** The client's acknowledgement that configuration is ready. */
        CONFIG_READY(3),

        /** The server's acknowledgement that play may begin. */
        CONFIG_ACK(4),

        /** One channel payload. */
        PAYLOAD(5),

        /** An orderly disconnect with a human-readable reason. */
        DISCONNECT(6);

        private final int code;

        FrameType(int code)
        {
            this.code = code;
        }

        /**
         * The one-byte wire code.
         *
         * @return the code, between {@code 1} and {@code 6}
         */
        int code()
        {
            return code;
        }

        /**
         * Resolves a wire code.
         *
         * @param code the unsigned byte read from the wire
         * @return the matching frame type, never {@code null}
         * @throws NetworkException when no frame type uses the code
         */
        static FrameType fromCode(int code)
        {
            for (FrameType type : values())
            {
                if (type.code == code)
                {
                    return type;
                }
            }
            throw new NetworkException("unknown frame type: " + code);
        }
    }

    /**
     * One decoded frame.
     *
     * <p>The constructor copies the body, so later changes to the caller's
     * array do not affect the frame. The array returned by {@link #body()} is
     * owned by the frame and must not be modified by callers.</p>
     *
     * @param type the frame type, not {@code null}
     * @param body the frame body, not {@code null}
     */
    record WireFrame(FrameType type, byte[] body)
    {
        WireFrame
        {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(body, "body");
            body = body.clone();
        }
    }
}
