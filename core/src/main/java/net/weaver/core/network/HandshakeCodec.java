package net.weaver.core.network;

import net.weaver.api.network.HandshakeRequest;
import net.weaver.api.network.HandshakeResponse;
import net.weaver.api.network.NetworkException;

import java.util.Objects;

/**
 * Encodes and decodes the two handshake messages.
 *
 * <p>The wire format is owned by {@link HandshakeRequest#CODEC} and
 * {@link HandshakeResponse#CODEC}; this class only supplies null handling and a
 * stable entry point for the transport. Every decoded message must consume the
 * complete input, and every encoded message is bounded by
 * {@link net.weaver.api.network.NetworkProtocol#MAX_PAYLOAD_BYTES}.</p>
 */
public final class HandshakeCodec
{
    private HandshakeCodec()
    {
        throw new AssertionError("No HandshakeCodec instances");
    }

    /**
     * Encodes a handshake request into a new byte array.
     *
     * @param request the request to encode, not {@code null}
     * @return a newly allocated payload, never {@code null}
     * @throws NullPointerException when {@code request} is {@code null}
     * @throws NetworkException     when the request cannot be represented or the
     *                              payload would exceed the protocol maximum
     */
    public static byte[] encodeRequest(HandshakeRequest request)
    {
        Objects.requireNonNull(request, "request");
        return HandshakeRequest.CODEC.encode(request);
    }

    /**
     * Decodes a complete handshake request payload.
     *
     * <p>Trailing bytes are rejected. A {@link NetworkException} raised by the
     * codec is propagated unchanged so its precise reason remains available to
     * the transport.</p>
     *
     * @param data the encoded request, not {@code null}
     * @return the decoded request, never {@code null}
     * @throws NullPointerException when {@code data} is {@code null}
     * @throws NetworkException     when the payload is malformed, underflows,
     *                              contains trailing bytes, or exceeds the
     *                              protocol maximum
     */
    public static HandshakeRequest decodeRequest(byte[] data)
    {
        Objects.requireNonNull(data, "data");
        return HandshakeRequest.CODEC.decode(data);
    }

    /**
     * Encodes a handshake response into a new byte array.
     *
     * @param response the response to encode, not {@code null}
     * @return a newly allocated payload, never {@code null}
     * @throws NullPointerException when {@code response} is {@code null}
     * @throws NetworkException     when the response cannot be represented or
     *                              the payload would exceed the protocol
     *                              maximum
     */
    public static byte[] encodeResponse(HandshakeResponse response)
    {
        Objects.requireNonNull(response, "response");
        return HandshakeResponse.CODEC.encode(response);
    }

    /**
     * Decodes a complete handshake response payload.
     *
     * <p>Trailing bytes are rejected. A {@link NetworkException} raised by the
     * codec is propagated unchanged so its precise reason remains available to
     * the transport.</p>
     *
     * @param data the encoded response, not {@code null}
     * @return the decoded response, never {@code null}
     * @throws NullPointerException when {@code data} is {@code null}
     * @throws NetworkException     when the payload is malformed, underflows,
     *                              contains trailing bytes, or exceeds the
     *                              protocol maximum
     */
    public static HandshakeResponse decodeResponse(byte[] data)
    {
        Objects.requireNonNull(data, "data");
        return HandshakeResponse.CODEC.decode(data);
    }
}
