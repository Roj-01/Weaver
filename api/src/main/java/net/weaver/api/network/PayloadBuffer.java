package net.weaver.api.network;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * A bounded, reusable buffer for encoding and decoding network payloads.
 *
 * <p>Writers grow on demand up to {@link NetworkProtocol#MAX_PAYLOAD_BYTES} and
 * throw {@link NetworkException} when a write would exceed that limit. Readers
 * copy their input on construction, refuse to write, and throw
 * {@link NetworkException} on underflow, invalid UTF-8, oversized length
 * prefixes, and oversized values. Length prefixes are encoded as varints
 * before the corresponding bytes, so a reader never allocates based on an
 * untrusted length before validating it.</p>
 *
 * <p>All multi-byte integers are big-endian, matching the usual Java
 * {@link ByteBuffer} convention. Instances are not thread-safe; confine each
 * buffer to one thread at a time.</p>
 */
public final class PayloadBuffer
{
    private static final int DEFAULT_CAPACITY = 256;

    private byte[] data;
    private int position;
    private int limit;
    private final boolean reader;

    private PayloadBuffer(byte[] data, int position, int limit, boolean reader)
    {
        this.data = data;
        this.position = position;
        this.limit = limit;
        this.reader = reader;
    }

    /**
     * Creates an empty writer with a small initial capacity.
     *
     * @return a new writable buffer, never {@code null}
     */
    public static PayloadBuffer writer()
    {
        return writer(DEFAULT_CAPACITY);
    }

    /**
     * Creates an empty writer with the requested initial capacity.
     *
     * @param initialCapacity the initial byte capacity, at least {@code 0} and
     *                        at most {@link NetworkProtocol#MAX_PAYLOAD_BYTES}
     * @return a new writable buffer, never {@code null}
     * @throws IllegalArgumentException when {@code initialCapacity} is negative
     *                                  or larger than
     *                                  {@link NetworkProtocol#MAX_PAYLOAD_BYTES}
     */
    public static PayloadBuffer writer(int initialCapacity)
    {
        if (initialCapacity < 0)
        {
            throw new IllegalArgumentException("initialCapacity must be >= 0 but was "
                    + initialCapacity);
        }
        if (initialCapacity > NetworkProtocol.MAX_PAYLOAD_BYTES)
        {
            throw new IllegalArgumentException("initialCapacity must be at most "
                    + NetworkProtocol.MAX_PAYLOAD_BYTES + " but was " + initialCapacity);
        }
        return new PayloadBuffer(new byte[initialCapacity], 0, 0, false);
    }

    /**
     * Creates a reader over a copy of the supplied bytes.
     *
     * <p>The input is copied, so later changes to the array do not affect the
     * buffer. The whole array is treated as the payload and the read position
     * starts at zero.</p>
     *
     * @param data the encoded payload, not {@code null}
     * @return a new readable buffer, never {@code null}
     * @throws NullPointerException when {@code data} is {@code null}
     * @throws NetworkException     when the array is larger than
     *                              {@link NetworkProtocol#MAX_PAYLOAD_BYTES}
     */
    public static PayloadBuffer reader(byte[] data)
    {
        Objects.requireNonNull(data, "data");
        if (data.length > NetworkProtocol.MAX_PAYLOAD_BYTES)
        {
            throw new NetworkException("payload of " + data.length
                    + " bytes exceeds the maximum of " + NetworkProtocol.MAX_PAYLOAD_BYTES);
        }
        return new PayloadBuffer(data.clone(), 0, data.length, true);
    }

    /**
     * The number of bytes that can still be read.
     *
     * <p>Writers return {@code 0} because their write cursor is also their
     * limit; readers return the bytes between the current position and the end
     * of the payload.</p>
     *
     * @return the remaining readable byte count, never negative
     */
    public int readableBytes()
    {
        return limit - position;
    }

    /**
     * The current read or write cursor.
     *
     * @return the zero-based position
     */
    public int position()
    {
        return position;
    }

    /**
     * The complete buffered payload as a new array.
     *
     * <p>For a writer this is every byte written so far. For a reader this is
     * the complete input payload, including bytes that have already been read;
     * this method does not advance or rewind the cursor.</p>
     *
     * @return a new byte array containing the payload, never {@code null}
     */
    public byte[] toByteArray()
    {
        return Arrays.copyOf(data, limit);
    }

    /**
     * Verifies that every readable byte has been consumed.
     *
     * @throws NetworkException when at least one unread byte remains
     */
    public void ensureFullyRead()
    {
        int remaining = readableBytes();
        if (remaining != 0)
        {
            throw new NetworkException("payload has " + remaining + " unread byte(s)");
        }
    }

    /**
     * Resets a writer to an empty payload so it can be reused.
     *
     * <p>The allocated capacity is retained. Readers cannot be cleared and
     * cannot be reused as writers.</p>
     *
     * @return this buffer, for chaining
     * @throws NetworkException when this buffer was created for reading
     */
    public PayloadBuffer clear()
    {
        requireWriter();
        position = 0;
        limit = 0;
        return this;
    }

    /**
     * Writes one boolean as a single {@code 0} or {@code 1} byte.
     *
     * @param value the value to write
     * @return this buffer, for chaining
     * @throws NetworkException when this is a reader or the payload would
     *                          exceed the maximum size
     */
    public PayloadBuffer writeBoolean(boolean value)
    {
        return writeByte(value ? 1 : 0);
    }

    /**
     * Writes the low eight bits of {@code value}.
     *
     * @param value the value to write; only the low byte is retained
     * @return this buffer, for chaining
     * @throws NetworkException when this is a reader or the payload would
     *                          exceed the maximum size
     */
    public PayloadBuffer writeByte(int value)
    {
        requireWriter();
        ensureWritable(1);
        data[position++] = (byte) value;
        limit = position;
        return this;
    }

    /**
     * Writes a four-byte big-endian integer.
     *
     * @param value the value to write
     * @return this buffer, for chaining
     * @throws NetworkException when this is a reader or the payload would
     *                          exceed the maximum size
     */
    public PayloadBuffer writeInt(int value)
    {
        requireWriter();
        ensureWritable(Integer.BYTES);
        data[position++] = (byte) (value >>> 24);
        data[position++] = (byte) (value >>> 16);
        data[position++] = (byte) (value >>> 8);
        data[position++] = (byte) value;
        limit = position;
        return this;
    }

    /**
     * Writes an eight-byte big-endian integer.
     *
     * @param value the value to write
     * @return this buffer, for chaining
     * @throws NetworkException when this is a reader or the payload would
     *                          exceed the maximum size
     */
    public PayloadBuffer writeLong(long value)
    {
        requireWriter();
        ensureWritable(Long.BYTES);
        data[position++] = (byte) (value >>> 56);
        data[position++] = (byte) (value >>> 48);
        data[position++] = (byte) (value >>> 40);
        data[position++] = (byte) (value >>> 32);
        data[position++] = (byte) (value >>> 24);
        data[position++] = (byte) (value >>> 16);
        data[position++] = (byte) (value >>> 8);
        data[position++] = (byte) value;
        limit = position;
        return this;
    }

    /**
     * Writes an integer using a variable-length 7-bit encoding.
     *
     * <p>Values in {@code 0..127} use one byte. Negative values and large
     * positive values use up to five bytes. The encoding is stable and
     * symmetric with {@link #readVarInt()}.</p>
     *
     * @param value the value to write
     * @return this buffer, for chaining
     * @throws NetworkException when this is a reader or the payload would
     *                          exceed the maximum size
     */
    public PayloadBuffer writeVarInt(int value)
    {
        requireWriter();
        int remaining = value;
        while ((remaining & ~0x7F) != 0)
        {
            writeRawByte((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        writeRawByte(remaining);
        return this;
    }

    /**
     * Writes a UTF-8 string with a varint byte length and the default maximum
     * of {@link NetworkProtocol#MAX_STRING_CHARS} chars.
     *
     * @param value the string to write, not {@code null}
     * @return this buffer, for chaining
     * @throws NullPointerException     when {@code value} is {@code null}
     * @throws NetworkException         when the string is too long, its encoded
     *                                  form is too large, or this is a reader
     */
    public PayloadBuffer writeString(String value)
    {
        return writeString(value, NetworkProtocol.MAX_STRING_CHARS);
    }

    /**
     * Writes a UTF-8 string with a varint byte length and an explicit char
     * limit.
     *
     * <p>The limit applies to Java chars ({@link String#length()}), not encoded
     * bytes. The encoded form is still bounded by
     * {@link NetworkProtocol#MAX_PAYLOAD_BYTES}. Embedded NUL characters are
     * allowed, matching the rest of the Weaver API.</p>
     *
     * @param value    the string to write, not {@code null}
     * @param maxChars the maximum accepted char count, at least {@code 0}
     * @return this buffer, for chaining
     * @throws NullPointerException     when {@code value} is {@code null}
     * @throws IllegalArgumentException when {@code maxChars} is negative
     * @throws NetworkException         when the string exceeds {@code maxChars},
     *                                  its encoded form is too large, or this is
     *                                  a reader
     */
    public PayloadBuffer writeString(String value, int maxChars)
    {
        Objects.requireNonNull(value, "value");
        if (maxChars < 0)
        {
            throw new IllegalArgumentException("maxChars must be >= 0 but was " + maxChars);
        }
        if (value.length() > maxChars)
        {
            throw new NetworkException("string of " + value.length()
                    + " chars exceeds the limit of " + maxChars);
        }
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > NetworkProtocol.MAX_PAYLOAD_BYTES)
        {
            throw new NetworkException("encoded string of " + encoded.length
                    + " bytes exceeds the maximum payload of "
                    + NetworkProtocol.MAX_PAYLOAD_BYTES);
        }
        writeVarInt(encoded.length);
        appendBytes(encoded, 0, encoded.length);
        return this;
    }

    /**
     * Writes a byte array with a varint length and the default maximum of
     * {@link NetworkProtocol#MAX_PAYLOAD_BYTES} bytes.
     *
     * @param value the bytes to write, not {@code null}
     * @return this buffer, for chaining
     * @throws NullPointerException when {@code value} is {@code null}
     * @throws NetworkException     when the array is too large or this is a
     *                              reader
     */
    public PayloadBuffer writeByteArray(byte[] value)
    {
        return writeByteArray(value, NetworkProtocol.MAX_PAYLOAD_BYTES);
    }

    /**
     * Writes a byte array with a varint length and an explicit byte limit.
     *
     * @param value    the bytes to write, not {@code null}
     * @param maxBytes the maximum accepted byte count, at least {@code 0}
     * @return this buffer, for chaining
     * @throws NullPointerException     when {@code value} is {@code null}
     * @throws IllegalArgumentException when {@code maxBytes} is negative
     * @throws NetworkException         when the array exceeds {@code maxBytes},
     *                                  the total payload would exceed the
     *                                  maximum size, or this is a reader
     */
    public PayloadBuffer writeByteArray(byte[] value, int maxBytes)
    {
        Objects.requireNonNull(value, "value");
        if (maxBytes < 0)
        {
            throw new IllegalArgumentException("maxBytes must be >= 0 but was " + maxBytes);
        }
        if (value.length > maxBytes)
        {
            throw new NetworkException("byte array of " + value.length
                    + " bytes exceeds the limit of " + maxBytes);
        }
        if (value.length > NetworkProtocol.MAX_PAYLOAD_BYTES)
        {
            throw new NetworkException("byte array of " + value.length
                    + " bytes exceeds the maximum payload of "
                    + NetworkProtocol.MAX_PAYLOAD_BYTES);
        }
        writeVarInt(value.length);
        appendBytes(value, 0, value.length);
        return this;
    }

    /**
     * Writes a UUID as two big-endian longs: most significant bits first.
     *
     * @param value the UUID to write, not {@code null}
     * @return this buffer, for chaining
     * @throws NullPointerException when {@code value} is {@code null}
     * @throws NetworkException     when this is a reader or the payload would
     *                              exceed the maximum size
     */
    public PayloadBuffer writeUuid(UUID value)
    {
        Objects.requireNonNull(value, "value");
        writeLong(value.getMostSignificantBits());
        writeLong(value.getLeastSignificantBits());
        return this;
    }

    /**
     * Reads one boolean written by {@link #writeBoolean(boolean)}.
     *
     * <p>Any non-zero byte is treated as {@code true}.</p>
     *
     * @return the decoded boolean
     * @throws NetworkException when fewer than one byte remains
     */
    public boolean readBoolean()
    {
        return readRawByte() != 0;
    }

    /**
     * Reads one unsigned byte in the range {@code 0..255}.
     *
     * @return the decoded value
     * @throws NetworkException when fewer than one byte remains
     */
    public int readUnsignedByte()
    {
        return readRawByte();
    }

    /**
     * Reads a four-byte big-endian integer.
     *
     * @return the decoded value
     * @throws NetworkException when fewer than four bytes remain
     */
    public int readInt()
    {
        requireReadable(Integer.BYTES);
        int value = ((data[position] & 0xFF) << 24)
                | ((data[position + 1] & 0xFF) << 16)
                | ((data[position + 2] & 0xFF) << 8)
                | (data[position + 3] & 0xFF);
        position += Integer.BYTES;
        return value;
    }

    /**
     * Reads an eight-byte big-endian integer.
     *
     * @return the decoded value
     * @throws NetworkException when fewer than eight bytes remain
     */
    public long readLong()
    {
        requireReadable(Long.BYTES);
        long value = ((long) (data[position] & 0xFF) << 56)
                | ((long) (data[position + 1] & 0xFF) << 48)
                | ((long) (data[position + 2] & 0xFF) << 40)
                | ((long) (data[position + 3] & 0xFF) << 32)
                | ((long) (data[position + 4] & 0xFF) << 24)
                | ((long) (data[position + 5] & 0xFF) << 16)
                | ((long) (data[position + 6] & 0xFF) << 8)
                | (long) (data[position + 7] & 0xFF);
        position += Long.BYTES;
        return value;
    }

    /**
     * Reads a variable-length integer written by {@link #writeVarInt(int)}.
     *
     * @return the decoded value
     * @throws NetworkException when the buffer underflows or the encoding uses
     *                          more than five bytes
     */
    public int readVarInt()
    {
        int result = 0;
        int shift = 0;
        for (int index = 0; index < 5; index++)
        {
            int current = readRawByte();
            result |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0)
            {
                return result;
            }
            shift += 7;
        }
        throw new NetworkException("malformed varint: encoding exceeds 5 bytes");
    }

    /**
     * Reads a UTF-8 string with the default maximum of
     * {@link NetworkProtocol#MAX_STRING_CHARS} chars.
     *
     * @return the decoded string, never {@code null}
     * @throws NetworkException when the payload underflows, the declared length
     *                          is negative or too large, the char count exceeds
     *                          the limit, or the bytes are not valid UTF-8
     */
    public String readString()
    {
        return readString(NetworkProtocol.MAX_STRING_CHARS);
    }

    /**
     * Reads a UTF-8 string with an explicit char limit.
     *
     * <p>The declared byte length is validated against both the byte capacity
     * of {@code maxChars} and {@link NetworkProtocol#MAX_PAYLOAD_BYTES} before
     * any bytes are consumed. Decoding uses a strict UTF-8 decoder that reports
     * malformed input instead of replacing it, and the decoded Java char count
     * is checked again after decoding.</p>
     *
     * @param maxChars the maximum accepted char count, at least {@code 0}
     * @return the decoded string, never {@code null}
     * @throws IllegalArgumentException when {@code maxChars} is negative
     * @throws NetworkException         when the payload underflows, the declared
     *                                  length is negative or too large, the char
     *                                  count exceeds {@code maxChars}, or the
     *                                  bytes are not valid UTF-8
     */
    public String readString(int maxChars)
    {
        if (maxChars < 0)
        {
            throw new IllegalArgumentException("maxChars must be >= 0 but was " + maxChars);
        }
        int byteLength = readVarInt();
        if (byteLength < 0)
        {
            throw new NetworkException("negative string length: " + byteLength);
        }
        long charCapacity = (long) maxChars * 4L;
        long byteLimit = Math.min(NetworkProtocol.MAX_PAYLOAD_BYTES, charCapacity);
        if (byteLength > byteLimit)
        {
            throw new NetworkException("string byte length " + byteLength
                    + " exceeds the limit of " + byteLimit + " for " + maxChars + " chars");
        }
        requireReadable(byteLength);
        String value = decodeUtf8(data, position, byteLength);
        position += byteLength;
        if (value.length() > maxChars)
        {
            throw new NetworkException("decoded string of " + value.length()
                    + " chars exceeds the limit of " + maxChars);
        }
        return value;
    }

    /**
     * Reads a byte array with the default maximum of
     * {@link NetworkProtocol#MAX_PAYLOAD_BYTES} bytes.
     *
     * @return a new array containing the decoded bytes, never {@code null}
     * @throws NetworkException when the payload underflows or the declared
     *                          length is negative or too large
     */
    public byte[] readByteArray()
    {
        return readByteArray(NetworkProtocol.MAX_PAYLOAD_BYTES);
    }

    /**
     * Reads a byte array with an explicit byte limit.
     *
     * <p>The declared length is checked against {@code maxBytes}, the payload
     * maximum, and the remaining readable bytes before an output array is
     * allocated.</p>
     *
     * @param maxBytes the maximum accepted byte count, at least {@code 0}
     * @return a new array containing the decoded bytes, never {@code null}
     * @throws IllegalArgumentException when {@code maxBytes} is negative
     * @throws NetworkException         when the payload underflows or the
     *                                  declared length is negative or too large
     */
    public byte[] readByteArray(int maxBytes)
    {
        if (maxBytes < 0)
        {
            throw new IllegalArgumentException("maxBytes must be >= 0 but was " + maxBytes);
        }
        int length = readVarInt();
        if (length < 0)
        {
            throw new NetworkException("negative byte array length: " + length);
        }
        if (length > maxBytes)
        {
            throw new NetworkException("byte array length " + length
                    + " exceeds the limit of " + maxBytes);
        }
        if (length > NetworkProtocol.MAX_PAYLOAD_BYTES)
        {
            throw new NetworkException("byte array length " + length
                    + " exceeds the maximum payload of "
                    + NetworkProtocol.MAX_PAYLOAD_BYTES);
        }
        requireReadable(length);
        byte[] value = Arrays.copyOfRange(data, position, position + length);
        position += length;
        return value;
    }

    /**
     * Reads a UUID written by {@link #writeUuid(UUID)}.
     *
     * @return the decoded UUID, never {@code null}
     * @throws NetworkException when fewer than sixteen bytes remain
     */
    public UUID readUuid()
    {
        long mostSignificantBits = readLong();
        long leastSignificantBits = readLong();
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    private void requireWriter()
    {
        if (reader)
        {
            throw new NetworkException("buffer was created for reading and cannot be written");
        }
    }

    private void ensureWritable(int byteCount)
    {
        if (byteCount < 0)
        {
            throw new NetworkException("negative write size: " + byteCount);
        }
        if (byteCount > NetworkProtocol.MAX_PAYLOAD_BYTES - position)
        {
            throw new NetworkException("payload would exceed the maximum of "
                    + NetworkProtocol.MAX_PAYLOAD_BYTES + " bytes");
        }
        int required = position + byteCount;
        if (required > data.length)
        {
            int doubled = data.length == 0 ? DEFAULT_CAPACITY : data.length * 2;
            int newCapacity = Math.max(required, Math.min(NetworkProtocol.MAX_PAYLOAD_BYTES, doubled));
            data = Arrays.copyOf(data, newCapacity);
        }
    }

    private void writeRawByte(int value)
    {
        requireWriter();
        ensureWritable(1);
        data[position++] = (byte) value;
        limit = position;
    }

    private void appendBytes(byte[] source, int offset, int length)
    {
        requireWriter();
        ensureWritable(length);
        System.arraycopy(source, offset, data, position, length);
        position += length;
        limit = position;
    }

    private int readRawByte()
    {
        requireReadable(1);
        return data[position++] & 0xFF;
    }

    private void requireReadable(int byteCount)
    {
        if (byteCount < 0)
        {
            throw new NetworkException("negative read size: " + byteCount);
        }
        int remaining = limit - position;
        if (byteCount > remaining)
        {
            throw new NetworkException("payload underflow: requested " + byteCount
                    + " byte(s) but only " + remaining + " remain");
        }
    }

    private static String decodeUtf8(byte[] source, int offset, int length)
    {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try
        {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(source, offset, length));
            return decoded.toString();
        }
        catch (CharacterCodingException failure)
        {
            throw new NetworkException("invalid UTF-8 string payload", failure);
        }
    }
}
