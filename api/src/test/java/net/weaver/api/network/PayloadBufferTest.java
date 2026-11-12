package net.weaver.api.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadBufferTest
{
    @Test
    void primitiveValuesRoundTrip()
    {
        UUID uuid = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        PayloadBuffer writer = PayloadBuffer.writer();
        writer.writeBoolean(true);
        writer.writeBoolean(false);
        writer.writeByte(0xAB);
        writer.writeInt(Integer.MIN_VALUE);
        writer.writeInt(Integer.MAX_VALUE);
        writer.writeLong(Long.MIN_VALUE);
        writer.writeLong(Long.MAX_VALUE);
        writer.writeUuid(uuid);

        PayloadBuffer reader = PayloadBuffer.reader(writer.toByteArray());

        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertEquals(0xAB, reader.readUnsignedByte());
        assertEquals(Integer.MIN_VALUE, reader.readInt());
        assertEquals(Integer.MAX_VALUE, reader.readInt());
        assertEquals(Long.MIN_VALUE, reader.readLong());
        assertEquals(Long.MAX_VALUE, reader.readLong());
        assertEquals(uuid, reader.readUuid());
        reader.ensureFullyRead();
    }

    @Test
    void nonZeroBooleanByteIsTrue()
    {
        PayloadBuffer reader = PayloadBuffer.reader(new byte[] { 7, 0 });

        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean());
    }

    @Test
    void stringsAndByteArraysRoundTrip()
    {
        byte[] bytes = { 0, 1, 2, (byte) 0xFF, 42 };
        String text = "h\u00e9llo w\u00f6rld \uD83D\uDE00";
        PayloadBuffer writer = PayloadBuffer.writer(1);
        writer.writeString(text);
        writer.writeString("");
        writer.writeString("nul\u0000inside");
        writer.writeByteArray(bytes);

        PayloadBuffer reader = PayloadBuffer.reader(writer.toByteArray());

        assertEquals(text, reader.readString());
        assertEquals("", reader.readString());
        assertEquals("nul\u0000inside", reader.readString());
        assertArrayEquals(bytes, reader.readByteArray());
        reader.ensureFullyRead();
    }

    @Test
    void varIntBoundaryValuesRoundTrip()
    {
        int[] values = {
                0, 1, 2, 127, 128, 255, 16_383, 16_384,
                Integer.MAX_VALUE, Integer.MIN_VALUE,
                -1, -2, -128, -129, 1_000_000
        };
        PayloadBuffer writer = PayloadBuffer.writer();
        for (int value : values)
        {
            writer.writeVarInt(value);
        }

        PayloadBuffer reader = PayloadBuffer.reader(writer.toByteArray());
        for (int value : values)
        {
            assertEquals(value, reader.readVarInt());
        }
        reader.ensureFullyRead();
    }

    @Test
    void malformedVarIntWithTooManyContinuationBytesIsRejected()
    {
        byte[] encoded = {
                (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80
        };

        NetworkException failure = assertThrows(NetworkException.class,
                () -> PayloadBuffer.reader(encoded).readVarInt());

        assertTrue(failure.getMessage().contains("malformed varint"), failure.getMessage());
    }

    @Test
    void strictUtf8RejectsMalformedBytes()
    {
        assertInvalidUtf8(new byte[] { (byte) 0xC0, (byte) 0x80 });
        assertInvalidUtf8(new byte[] { (byte) 0x80 });
        assertInvalidUtf8(new byte[] { (byte) 0xE2, (byte) 0x82 });
        assertInvalidUtf8(new byte[] { (byte) 0xFF });
    }

    private static void assertInvalidUtf8(byte[] encoded)
    {
        PayloadBuffer writer = PayloadBuffer.writer();
        writer.writeVarInt(encoded.length);
        for (byte value : encoded)
        {
            writer.writeByte(value);
        }

        NetworkException failure = assertThrows(NetworkException.class,
                () -> PayloadBuffer.reader(writer.toByteArray()).readString());

        assertTrue(failure.getMessage().contains("invalid UTF-8"), failure.getMessage());
    }

    @Test
    void truncatedPrimitiveReadsUnderflow()
    {
        PayloadBuffer empty = PayloadBuffer.reader(new byte[0]);
        assertUnderflow(() -> empty.readUnsignedByte(), 1, 0);
        assertUnderflow(() -> empty.readInt(), 4, 0);
        assertUnderflow(() -> empty.readLong(), 8, 0);
        assertUnderflow(() -> empty.readUuid(), 8, 0);
        assertUnderflow(() -> empty.readVarInt(), 1, 0);

        PayloadBuffer shortBuffer = PayloadBuffer.reader(new byte[] { 1, 2, 3 });
        assertUnderflow(() -> shortBuffer.readInt(), 4, 3);
    }

    private static void assertUnderflow(Runnable action, int requested, int remaining)
    {
        NetworkException failure = assertThrows(NetworkException.class, action::run);
        String message = failure.getMessage();
        assertTrue(message.contains("payload underflow"), message);
        assertTrue(message.contains("requested " + requested), message);
        assertTrue(message.contains("only " + remaining + " remain"), message);
    }

    @Test
    void truncatedLengthPrefixedValuesUnderflow()
    {
        PayloadBuffer arrayReader = PayloadBuffer.reader(
                PayloadBuffer.writer().writeVarInt(4).toByteArray());
        assertThrows(NetworkException.class, arrayReader::readByteArray);

        PayloadBuffer stringReader = PayloadBuffer.reader(
                PayloadBuffer.writer().writeVarInt(4).toByteArray());
        assertThrows(NetworkException.class, stringReader::readString);
    }

    @Test
    void truncatedStringBodyUnderflow()
    {
        PayloadBuffer writer = PayloadBuffer.writer();
        writer.writeVarInt(10);
        writer.writeByte('a');
        writer.writeByte('b');
        writer.writeByte('c');

        PayloadBuffer reader = PayloadBuffer.reader(writer.toByteArray());

        NetworkException failure = assertThrows(NetworkException.class, reader::readString);
        assertTrue(failure.getMessage().contains("payload underflow"), failure.getMessage());
    }

    @Test
    void stringWriteLimitsAreEnforced()
    {
        String tooLong = "a".repeat(NetworkProtocol.MAX_STRING_CHARS + 1);
        NetworkException defaultFailure = assertThrows(NetworkException.class,
                () -> PayloadBuffer.writer().writeString(tooLong));
        assertTrue(defaultFailure.getMessage().contains(
                "string of " + tooLong.length() + " chars exceeds the limit of "
                        + NetworkProtocol.MAX_STRING_CHARS), defaultFailure.getMessage());

        NetworkException explicitFailure = assertThrows(NetworkException.class,
                () -> PayloadBuffer.writer().writeString("abc", 2));
        assertTrue(explicitFailure.getMessage().contains("string of 3 chars exceeds the limit of 2"),
                explicitFailure.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> PayloadBuffer.writer().writeString("abc", -1));
        assertThrows(NullPointerException.class,
                () -> PayloadBuffer.writer().writeString(null));
    }

    @Test
    void stringReadLimitsAreEnforced()
    {
        PayloadBuffer twoChars = PayloadBuffer.reader(
                PayloadBuffer.writer().writeString("ab").toByteArray());
        NetworkException decodedFailure = assertThrows(NetworkException.class,
                () -> twoChars.readString(1));
        assertTrue(decodedFailure.getMessage().contains(
                "decoded string of 2 chars exceeds the limit of 1"), decodedFailure.getMessage());

        PayloadBuffer twoBytes = PayloadBuffer.reader(
                PayloadBuffer.writer().writeString("ab").toByteArray());
        NetworkException byteFailure = assertThrows(NetworkException.class,
                () -> twoBytes.readString(0));
        assertTrue(byteFailure.getMessage().contains(
                "string byte length 2 exceeds the limit of 0 for 0 chars"),
                byteFailure.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> PayloadBuffer.reader(new byte[0]).readString(-1));

        PayloadBuffer emptyString = PayloadBuffer.reader(
                PayloadBuffer.writer().writeString("").toByteArray());
        assertEquals("", emptyString.readString(0));
    }

    @Test
    void byteArrayLimitsAreEnforced()
    {
        NetworkException writeFailure = assertThrows(NetworkException.class,
                () -> PayloadBuffer.writer().writeByteArray(new byte[] { 1, 2, 3, 4 }, 3));
        assertTrue(writeFailure.getMessage().contains(
                "byte array of 4 bytes exceeds the limit of 3"), writeFailure.getMessage());

        byte[] encoded = PayloadBuffer.writer()
                .writeByteArray(new byte[] { 1, 2, 3, 4 })
                .toByteArray();
        NetworkException readFailure = assertThrows(NetworkException.class,
                () -> PayloadBuffer.reader(encoded).readByteArray(2));
        assertTrue(readFailure.getMessage().contains(
                "byte array length 4 exceeds the limit of 2"), readFailure.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> PayloadBuffer.writer().writeByteArray(new byte[0], -1));
        assertThrows(IllegalArgumentException.class,
                () -> PayloadBuffer.reader(new byte[0]).readByteArray(-1));
        assertThrows(NullPointerException.class,
                () -> PayloadBuffer.writer().writeByteArray(null));
    }

    @Test
    void oversizedPayloadValuesAreRejected()
    {
        NetworkException arrayFailure = assertThrows(NetworkException.class,
                () -> PayloadBuffer.writer().writeByteArray(
                        new byte[NetworkProtocol.MAX_PAYLOAD_BYTES]));
        assertTrue(arrayFailure.getMessage().contains("maximum of "
                + NetworkProtocol.MAX_PAYLOAD_BYTES), arrayFailure.getMessage());

        NetworkException readerFailure = assertThrows(NetworkException.class,
                () -> PayloadBuffer.reader(new byte[NetworkProtocol.MAX_PAYLOAD_BYTES + 1]));
        assertTrue(readerFailure.getMessage().contains(
                "payload of " + (NetworkProtocol.MAX_PAYLOAD_BYTES + 1) + " bytes exceeds"),
                readerFailure.getMessage());
    }

    @Test
    void invalidInitialCapacityIsRejected()
    {
        assertThrows(IllegalArgumentException.class, () -> PayloadBuffer.writer(-1));
        assertThrows(IllegalArgumentException.class,
                () -> PayloadBuffer.writer(NetworkProtocol.MAX_PAYLOAD_BYTES + 1));
    }

    @Test
    void ensureFullyReadRejectsUnreadBytes()
    {
        PayloadBuffer reader = PayloadBuffer.reader(
                PayloadBuffer.writer().writeInt(7).toByteArray());
        assertEquals(7, reader.readInt());
        reader.ensureFullyRead();

        PayloadBuffer partial = PayloadBuffer.reader(
                PayloadBuffer.writer().writeInt(7).toByteArray());
        partial.readUnsignedByte();
        NetworkException failure = assertThrows(NetworkException.class,
                partial::ensureFullyRead);
        assertEquals("payload has 3 unread byte(s)", failure.getMessage());
    }

    @Test
    void readersCannotWrite()
    {
        PayloadBuffer reader = PayloadBuffer.reader(new byte[] { 1, 2 });

        assertReadOnlyFailure(() -> reader.writeByte(1));
        assertReadOnlyFailure(() -> reader.writeInt(1));
        assertReadOnlyFailure(() -> reader.writeLong(1));
        assertReadOnlyFailure(() -> reader.writeVarInt(1));
        assertReadOnlyFailure(() -> reader.writeString("x"));
        assertReadOnlyFailure(() -> reader.writeByteArray(new byte[] { 1 }));
        assertReadOnlyFailure(() -> reader.writeUuid(UUID.randomUUID()));
        assertReadOnlyFailure(reader::clear);
    }

    private static void assertReadOnlyFailure(Runnable action)
    {
        NetworkException failure = assertThrows(NetworkException.class, action::run);
        assertEquals("buffer was created for reading and cannot be written",
                failure.getMessage());
    }

    @Test
    void writersCanClearAndBeReused()
    {
        PayloadBuffer writer = PayloadBuffer.writer();
        writer.writeInt(1234);
        assertEquals(4, writer.position());
        assertEquals(0, writer.readableBytes());

        writer.clear();

        assertEquals(0, writer.position());
        assertEquals(0, writer.readableBytes());
        assertArrayEquals(new byte[0], writer.toByteArray());

        writer.writeString("reused");
        PayloadBuffer reader = PayloadBuffer.reader(writer.toByteArray());
        assertEquals("reused", reader.readString());
        reader.ensureFullyRead();
    }

    @Test
    void readerCopiesInputAndKeepsFullPayload()
    {
        byte[] source = { 9, 8 };
        PayloadBuffer reader = PayloadBuffer.reader(source);
        source[0] = 1;

        assertArrayEquals(new byte[] { 9, 8 }, reader.toByteArray());
        assertEquals(9, reader.readUnsignedByte());
        assertArrayEquals(new byte[] { 9, 8 }, reader.toByteArray());
        assertEquals(1, reader.readableBytes());
    }
}
