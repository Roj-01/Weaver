package net.weaver.api.network;

import java.util.Objects;

/**
 * Encodes and decodes one payload type on a {@link PayloadBuffer}.
 *
 * <p>A codec is registered with a {@link PayloadType} and must be stateless and
 * safe for concurrent use: the same instance is used for every connection that
 * carries the channel. Codecs must consume exactly the bytes they write.
 * Implementations should validate their input and throw
 * {@link NetworkException} for malformed wire data.</p>
 *
 * @param <T> the payload type
 */
public interface PayloadCodec<T>
{
    /**
     * Encodes a value into the supplied writer.
     *
     * @param value the value to encode, not {@code null}
     * @param out   the destination buffer, not {@code null}
     * @throws NetworkException when the value cannot be represented or the
     *                          buffer limit is exceeded
     */
    void encode(T value, PayloadBuffer out);

    /**
     * Decodes one value from the supplied reader.
     *
     * <p>The implementation should read exactly the bytes it needs. The
     * convenience overload {@link #decode(byte[])} additionally verifies that
     * the entire input was consumed.</p>
     *
     * @param in the source buffer, not {@code null}
     * @return the decoded value, not {@code null}
     * @throws NetworkException when the payload underflows or is malformed
     */
    T decode(PayloadBuffer in);

    /**
     * Encodes a value into a new byte array.
     *
     * @param value the value to encode, not {@code null}
     * @return the encoded payload, never {@code null}
     * @throws NullPointerException when {@code value} is {@code null}
     * @throws NetworkException     when the value cannot be represented or
     *                              exceeds the payload limit
     */
    default byte[] encode(T value)
    {
        Objects.requireNonNull(value, "value");
        PayloadBuffer out = PayloadBuffer.writer();
        encode(value, out);
        return out.toByteArray();
    }

    /**
     * Decodes a value from a complete byte array and rejects trailing bytes.
     *
     * @param data the encoded payload, not {@code null}
     * @return the decoded value, not {@code null}
     * @throws NullPointerException when {@code data} is {@code null}
     * @throws NetworkException     when the payload is malformed, underflows, or
     *                              contains unconsumed bytes
     */
    default T decode(byte[] data)
    {
        Objects.requireNonNull(data, "data");
        PayloadBuffer in = PayloadBuffer.reader(data);
        T value = decode(in);
        in.ensureFullyRead();
        return value;
    }
}
