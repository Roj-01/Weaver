package net.weaver.api.data;

import net.weaver.api.capability.CapabilityCodec;
import net.weaver.api.capability.CapabilityException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataCodecsTest {

    @Test
    void utf8RoundtripsAndRejectsMalformedInput() {
        CapabilityCodec<String> codec = DataCodecs.utf8String(32);

        assertEquals("hello", codec.decode(codec.encode("hello")));
        assertEquals("héllo ✓", codec.decode(codec.encode("héllo ✓")));
        assertEquals("", codec.decode(codec.encode("")));

        assertThrows(CapabilityException.class, () -> codec.encode("x".repeat(33)));
        assertThrows(CapabilityException.class, () -> codec.decode(new byte[33]));
        assertThrows(CapabilityException.class,
                () -> codec.decode(new byte[]{(byte) 0xC3}));
        assertThrows(CapabilityException.class,
                () -> codec.decode(new byte[]{(byte) 0xFF}));
        assertThrows(CapabilityException.class,
                () -> codec.decode(new byte[]{(byte) 0xC0, (byte) 0x80}));
        assertThrows(NullPointerException.class, () -> codec.encode(null));
        assertThrows(NullPointerException.class, () -> codec.decode(null));

        CapabilityCodec<String> emptyOnly = DataCodecs.utf8String(0);

        assertEquals("", emptyOnly.decode(emptyOnly.encode("")));
        assertThrows(CapabilityException.class, () -> emptyOnly.encode("a"));
    }

    @Test
    void boolRoundtripsAndRejectsInvalidPayloads() {
        CapabilityCodec<Boolean> codec = DataCodecs.bool();

        assertArrayEquals(new byte[]{1}, codec.encode(true));
        assertArrayEquals(new byte[]{0}, codec.encode(false));
        assertTrue(codec.decode(new byte[]{1}));
        assertFalse(codec.decode(new byte[]{0}));

        assertThrows(CapabilityException.class, () -> codec.decode(new byte[]{2}));
        assertThrows(CapabilityException.class, () -> codec.decode(new byte[]{(byte) 0xFF}));
        assertThrows(CapabilityException.class, () -> codec.decode(new byte[0]));
        assertThrows(CapabilityException.class, () -> codec.decode(new byte[]{0, 0}));
        assertThrows(NullPointerException.class, () -> codec.encode(null));
        assertThrows(NullPointerException.class, () -> codec.decode(null));
    }

    @Test
    void varIntRoundtripsAndRejectsMalformedInput() {
        CapabilityCodec<Integer> codec = DataCodecs.varInt();
        int[] values = {0, 1, 127, 128, 255, 300, Integer.MAX_VALUE,
                Integer.MIN_VALUE, -1};

        for (int value : values) {
            byte[] encoded = codec.encode(value);

            assertTrue(encoded.length <= 5);
            assertEquals(value, codec.decode(encoded).intValue());
        }

        assertArrayEquals(new byte[]{0x00}, codec.encode(0));
        assertArrayEquals(new byte[]{0x7F}, codec.encode(127));
        assertArrayEquals(new byte[]{(byte) 0x80, 0x01}, codec.encode(128));

        assertThrows(CapabilityException.class, () -> codec.decode(new byte[0]));
        assertThrows(CapabilityException.class,
                () -> codec.decode(new byte[]{(byte) 0x80}));
        assertThrows(CapabilityException.class, () -> codec.decode(new byte[]{
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));
        assertThrows(CapabilityException.class, () -> codec.decode(new byte[]{
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x1F}));
        assertThrows(CapabilityException.class,
                () -> codec.decode(new byte[]{0x01, 0x00}));
        assertThrows(CapabilityException.class, () -> codec.decode(new byte[]{
                (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x00}));
        assertThrows(NullPointerException.class, () -> codec.encode(null));
        assertThrows(NullPointerException.class, () -> codec.decode(null));
    }

    @Test
    void varLongRoundtripsAndRejectsMalformedInput() {
        CapabilityCodec<Long> codec = DataCodecs.varLong();
        long[] values = {0L, 1L, 127L, 128L, 300L, Long.MAX_VALUE, Long.MIN_VALUE, -1L};

        for (long value : values) {
            byte[] encoded = codec.encode(value);

            assertTrue(encoded.length <= 10);
            assertEquals(value, codec.decode(encoded).longValue());
        }

        assertArrayEquals(new byte[]{(byte) 0x80, 0x01}, codec.encode(128L));

        assertThrows(CapabilityException.class, () -> codec.decode(new byte[0]));
        assertThrows(CapabilityException.class,
                () -> codec.decode(new byte[]{(byte) 0x80}));
        assertThrows(CapabilityException.class, () -> codec.decode(new byte[]{
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));
        assertThrows(CapabilityException.class,
                () -> codec.decode(new byte[]{0x01, 0x00}));
        assertThrows(CapabilityException.class, () -> codec.decode(new byte[]{
                (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x00}));
        assertThrows(NullPointerException.class, () -> codec.encode(null));
        assertThrows(NullPointerException.class, () -> codec.decode(null));
    }

    @Test
    void bytesCopyDefensivelyAndRespectTheBound() {
        CapabilityCodec<byte[]> codec = DataCodecs.bytes(4);
        byte[] source = {1, 2, 3};
        byte[] encoded = codec.encode(source);

        source[0] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, encoded);

        byte[] decoded = codec.decode(encoded);

        assertNotSame(encoded, decoded);
        encoded[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, decoded);

        decoded[2] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, codec.decode(new byte[]{1, 2, 3}));
        assertThrows(CapabilityException.class, () -> codec.encode(new byte[5]));
        assertThrows(CapabilityException.class, () -> codec.decode(new byte[5]));
        assertThrows(NullPointerException.class, () -> codec.encode(null));
        assertThrows(NullPointerException.class, () -> codec.decode(null));
    }

    @Test
    void uuidRoundtripsAndRequiresSixteenBytes() {
        CapabilityCodec<UUID> codec = DataCodecs.uuid();
        UUID value = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        byte[] encoded = codec.encode(value);

        assertEquals(16, encoded.length);
        assertEquals(value, codec.decode(encoded));
        assertArrayEquals(new byte[]{
                0x12, 0x34, 0x56, 0x78, 0x12, 0x34, 0x56, 0x78,
                (byte) 0x9A, (byte) 0xBC, (byte) 0xDE, (byte) 0xF0,
                0x12, 0x34, 0x56, 0x78}, encoded);

        assertThrows(CapabilityException.class, () -> codec.decode(new byte[15]));
        assertThrows(CapabilityException.class, () -> codec.decode(new byte[17]));
        assertThrows(NullPointerException.class, () -> codec.encode(null));
        assertThrows(NullPointerException.class, () -> codec.decode(null));
    }

    @Test
    void negativeBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DataCodecs.utf8String(-1));
        assertThrows(IllegalArgumentException.class, () -> DataCodecs.bytes(-1));
    }
}
