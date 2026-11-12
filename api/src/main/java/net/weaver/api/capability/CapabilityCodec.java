package net.weaver.api.capability;

/**
 * Encodes and decodes a capability value as explicit bytes.
 *
 * <p>Both the writing and the reading side must agree on the codec and its
 * format. Java serialization is not used. The codec itself does not enforce a
 * size bound; the owning {@link Capability#maxEncodedBytes()} is enforced by
 * the attachment store, and callers that persist bytes directly must apply the
 * same bound.
 *
 * @param <T> the capability value type
 */
public interface CapabilityCodec<T> {

    /**
     * Encodes a value.
     *
     * @param value the non-null value to encode
     * @return the encoded bytes, never {@code null}
     * @throws RuntimeException when the value cannot be encoded
     */
    byte[] encode(T value);

    /**
     * Decodes a value.
     *
     * @param encoded the non-null encoded bytes
     * @return the decoded value
     * @throws RuntimeException when the bytes are malformed or out of bounds
     */
    T decode(byte[] encoded);
}
