package net.weaver.api.data;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import net.weaver.api.capability.CapabilityCodec;
import net.weaver.api.capability.CapabilityException;

/**
 * Strict, bounded codec factories for common capability value types.
 *
 * <p>Every decoder rejects malformed, truncated, oversized, and trailing data
 * with {@link CapabilityException}. Encoders never return {@code null} and
 * enforce their declared bound. Null values and null byte arrays are rejected
 * with {@link NullPointerException}.
 */
public final class DataCodecs {

    private DataCodecs() {
    }

    /**
     * Creates a strict UTF-8 string codec.
     *
     * <p>Encoding uses UTF-8 and rejects a null value or an encoded length
     * greater than {@code maxBytes}. Decoding rejects malformed and unmappable
     * input with {@link CodingErrorAction#REPORT} and also rejects encoded input
     * longer than {@code maxBytes}.
     *
     * @param maxBytes the maximum encoded length in bytes
     * @return the string codec
     * @throws IllegalArgumentException when {@code maxBytes} is negative
     */
    public static CapabilityCodec<String> utf8String(int maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be >= 0");
        }
        return new CapabilityCodec<>() {
            @Override
            public byte[] encode(String value) {
                Objects.requireNonNull(value, "value");
                byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                if (encoded.length > maxBytes) {
                    throw new CapabilityException(
                            "Encoded string exceeds " + maxBytes + " bytes");
                }
                return encoded;
            }

            @Override
            public String decode(byte[] encoded) {
                Objects.requireNonNull(encoded, "encoded");
                if (encoded.length > maxBytes) {
                    throw new CapabilityException(
                            "Encoded string exceeds " + maxBytes + " bytes");
                }
                CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                try {
                    return decoder.decode(ByteBuffer.wrap(encoded)).toString();
                } catch (CharacterCodingException failure) {
                    throw new CapabilityException("Malformed UTF-8 string", failure);
                }
            }
        };
    }

    /**
     * Creates a boolean codec.
     *
     * <p>The encoding is exactly one byte: {@code 0} for {@code false} and
     * {@code 1} for {@code true}. Every other byte value, an empty payload, or
     * a payload with trailing bytes is rejected.
     *
     * @return the boolean codec
     */
    public static CapabilityCodec<Boolean> bool() {
        return new CapabilityCodec<>() {
            @Override
            public byte[] encode(Boolean value) {
                Objects.requireNonNull(value, "value");
                return new byte[]{(byte) (value ? 1 : 0)};
            }

            @Override
            public Boolean decode(byte[] encoded) {
                Objects.requireNonNull(encoded, "encoded");
                if (encoded.length != 1) {
                    throw new CapabilityException(
                            "Boolean payload must be exactly 1 byte");
                }
                if (encoded[0] == 0) {
                    return Boolean.FALSE;
                }
                if (encoded[0] == 1) {
                    return Boolean.TRUE;
                }
                throw new CapabilityException("Boolean payload must be 0 or 1");
            }
        };
    }

    /**
     * Creates an unsigned LEB128 integer codec.
     *
     * <p>The encoding uses at most five bytes. Decoding rejects truncated input,
     * values that do not fit in 32 bits, and trailing bytes.
     *
     * @return the integer codec
     */
    public static CapabilityCodec<Integer> varInt() {
        return new CapabilityCodec<>() {
            @Override
            public byte[] encode(Integer value) {
                Objects.requireNonNull(value, "value");
                byte[] buffer = new byte[5];
                int remaining = value;
                int length = 0;
                while ((remaining & ~0x7F) != 0) {
                    buffer[length++] = (byte) ((remaining & 0x7F) | 0x80);
                    remaining >>>= 7;
                }
                buffer[length++] = (byte) remaining;
                return Arrays.copyOf(buffer, length);
            }

            @Override
            public Integer decode(byte[] encoded) {
                Objects.requireNonNull(encoded, "encoded");
                int result = 0;
                int shift = 0;
                int index = 0;
                while (true) {
                    if (index >= encoded.length) {
                        throw new CapabilityException("Truncated varint");
                    }
                    if (index >= 5) {
                        throw new CapabilityException("Varint overflow");
                    }
                    int current = encoded[index] & 0xFF;
                    if (index == 4) {
                        if ((current & 0x80) != 0) {
                            throw new CapabilityException("Varint overflow");
                        }
                        if ((current & 0x70) != 0) {
                            throw new CapabilityException("Varint overflow");
                        }
                    }
                    result |= (current & 0x7F) << shift;
                    index++;
                    if ((current & 0x80) == 0) {
                        break;
                    }
                    shift += 7;
                }
                if (index != encoded.length) {
                    throw new CapabilityException("Trailing bytes after varint");
                }
                return result;
            }
        };
    }

    /**
     * Creates an unsigned LEB128 long codec.
     *
     * <p>The encoding uses at most ten bytes. Decoding rejects truncated input,
     * values that do not fit in 64 bits, and trailing bytes.
     *
     * @return the long codec
     */
    public static CapabilityCodec<Long> varLong() {
        return new CapabilityCodec<>() {
            @Override
            public byte[] encode(Long value) {
                Objects.requireNonNull(value, "value");
                byte[] buffer = new byte[10];
                long remaining = value;
                int length = 0;
                while ((remaining & ~0x7FL) != 0) {
                    buffer[length++] = (byte) ((remaining & 0x7F) | 0x80);
                    remaining >>>= 7;
                }
                buffer[length++] = (byte) remaining;
                return Arrays.copyOf(buffer, length);
            }

            @Override
            public Long decode(byte[] encoded) {
                Objects.requireNonNull(encoded, "encoded");
                long result = 0;
                int shift = 0;
                int index = 0;
                while (true) {
                    if (index >= encoded.length) {
                        throw new CapabilityException("Truncated varlong");
                    }
                    if (index >= 10) {
                        throw new CapabilityException("Varlong overflow");
                    }
                    int current = encoded[index] & 0xFF;
                    if (index == 9) {
                        if ((current & 0x80) != 0) {
                            throw new CapabilityException("Varlong overflow");
                        }
                        if ((current & 0x7E) != 0) {
                            throw new CapabilityException("Varlong overflow");
                        }
                    }
                    result |= (long) (current & 0x7F) << shift;
                    index++;
                    if ((current & 0x80) == 0) {
                        break;
                    }
                    shift += 7;
                }
                if (index != encoded.length) {
                    throw new CapabilityException("Trailing bytes after varlong");
                }
                return result;
            }
        };
    }

    /**
     * Creates a byte-array codec with defensive copies.
     *
     * <p>Both encoding and decoding copy the array. A null array or a length
     * greater than {@code maxBytes} is rejected.
     *
     * @param maxBytes the maximum array length in bytes
     * @return the byte-array codec
     * @throws IllegalArgumentException when {@code maxBytes} is negative
     */
    public static CapabilityCodec<byte[]> bytes(int maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be >= 0");
        }
        return new CapabilityCodec<>() {
            @Override
            public byte[] encode(byte[] value) {
                Objects.requireNonNull(value, "value");
                if (value.length > maxBytes) {
                    throw new CapabilityException(
                            "Byte payload exceeds " + maxBytes + " bytes");
                }
                return value.clone();
            }

            @Override
            public byte[] decode(byte[] encoded) {
                Objects.requireNonNull(encoded, "encoded");
                if (encoded.length > maxBytes) {
                    throw new CapabilityException(
                            "Byte payload exceeds " + maxBytes + " bytes");
                }
                return encoded.clone();
            }
        };
    }

    /**
     * Creates a UUID codec.
     *
     * <p>The encoding is exactly 16 bytes in big-endian order: the most
     * significant bits followed by the least significant bits. Any other
     * payload length is rejected.
     *
     * @return the UUID codec
     */
    public static CapabilityCodec<UUID> uuid() {
        return new CapabilityCodec<>() {
            @Override
            public byte[] encode(UUID value) {
                Objects.requireNonNull(value, "value");
                return ByteBuffer.allocate(16)
                        .putLong(value.getMostSignificantBits())
                        .putLong(value.getLeastSignificantBits())
                        .array();
            }

            @Override
            public UUID decode(byte[] encoded) {
                Objects.requireNonNull(encoded, "encoded");
                if (encoded.length != 16) {
                    throw new CapabilityException(
                            "UUID payload must be 16 bytes, got " + encoded.length);
                }
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                return new UUID(buffer.getLong(), buffer.getLong());
            }
        };
    }
}
