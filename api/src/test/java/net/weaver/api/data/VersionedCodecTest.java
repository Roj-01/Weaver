package net.weaver.api.data;

import net.weaver.api.capability.CapabilityCodec;
import net.weaver.api.capability.CapabilityException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Old formats are read through their readers; new data carries its version. */
class VersionedCodecTest
{
    private static final CapabilityCodec<String> V2 = DataCodecs.utf8String(64);

    private static VersionedCodec<String> codec()
    {
        return VersionedCodec.builder(2, V2)
                .reader(0, bytes -> "legacy:" + new String(bytes, StandardCharsets.UTF_8))
                .reader(1, bytes -> "v1:" + new String(bytes, StandardCharsets.UTF_8))
                .build();
    }

    @Test
    void roundTripsTheCurrentFormat()
    {
        VersionedCodec<String> codec = codec();
        byte[] encoded = codec.encode("hello");
        assertEquals(VersionedCodec.OVERHEAD + 5, encoded.length);
        assertEquals("hello", codec.decode(encoded));
    }

    @Test
    void readsUnversionedDataAsVersionZero()
    {
        assertEquals("legacy:old", codec().decode("old".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsAnOlderVersionWithItsReader()
    {
        byte[] v1 = VersionedCodec.builder(1, V2).build().encode("abc");
        assertEquals("v1:abc", codec().decode(v1));
    }

    @Test
    void rejectsANewerVersionAndMissingReaders()
    {
        byte[] v3 = VersionedCodec.builder(3, V2).build().encode("future");
        assertThrows(CapabilityException.class, () -> codec().decode(v3));
        VersionedCodec<String> noLegacy = VersionedCodec.builder(2, V2).build();
        assertThrows(CapabilityException.class, () -> noLegacy.decode("old".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> VersionedCodec.builder(2, V2).reader(2, b -> "x"));
    }

    @Test
    void writesAStableHeader()
    {
        byte[] encoded = codec().encode("");
        assertArrayEquals(new byte[] {(byte) 0xF7, 'W', 'V', (byte) 0xC1, 2}, encoded);
    }
}
