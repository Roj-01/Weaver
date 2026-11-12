package net.weaver.api.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadTypeTest
{
    private static final PayloadCodec<String> STRINGS = new PayloadCodec<>()
    {
        @Override
        public void encode(String value, PayloadBuffer out)
        {
            out.writeString(value);
        }

        @Override
        public String decode(PayloadBuffer in)
        {
            return in.readString();
        }
    };

    private static final PayloadCodec<Integer> INTS = new PayloadCodec<>()
    {
        @Override
        public void encode(Integer value, PayloadBuffer out)
        {
            out.writeInt(value);
        }

        @Override
        public Integer decode(PayloadBuffer in)
        {
            return in.readInt();
        }
    };

    @Test
    void defaultVersionProducesExpectedChannelFormat()
    {
        PayloadType<String> type = PayloadType.of("example", "telemetry", STRINGS);

        assertEquals("example", type.modId());
        assertEquals("example", type.namespace());
        assertEquals("telemetry", type.path());
        assertEquals(NetworkProtocol.CHANNEL_VERSION, type.version());
        assertEquals("example:telemetry/v1", type.channel());
        assertEquals("example:telemetry/v1", type.toString());
        assertSame(STRINGS, type.codec());
    }

    @Test
    void explicitVersionProducesExpectedChannelFormat()
    {
        PayloadType<String> type = PayloadType.of("example", "telemetry", 7, STRINGS);

        assertEquals(7, type.version());
        assertEquals("example:telemetry/v7", type.channel());
        assertEquals("telemetry", type.path());
        assertEquals("example", type.modId());
    }

    @Test
    void nestedPathsAreAllowed()
    {
        PayloadType<String> type = PayloadType.of("example", "deep/nested.path/v2_name",
                STRINGS);

        assertEquals("deep/nested.path/v2_name", type.path());
        assertEquals("example:deep/nested.path/v2_name/v1", type.channel());
    }

    @Test
    void versionMustBePositive()
    {
        assertThrows(IllegalArgumentException.class,
                () -> PayloadType.of("example", "telemetry", 0, STRINGS));
        assertThrows(IllegalArgumentException.class,
                () -> PayloadType.of("example", "telemetry", -1, STRINGS));
    }

    @Test
    void modIdIsValidated()
    {
        assertThrows(NullPointerException.class,
                () -> PayloadType.of(null, "telemetry", STRINGS));
        assertThrows(IllegalArgumentException.class,
                () -> PayloadType.of("Example", "telemetry", STRINGS));
        assertThrows(IllegalArgumentException.class,
                () -> PayloadType.of("example mod", "telemetry", STRINGS));
        assertThrows(IllegalArgumentException.class,
                () -> PayloadType.of("a".repeat(NetworkProtocol.MAX_MOD_ID_CHARS + 1),
                        "telemetry", STRINGS));
    }

    @Test
    void pathIsValidated()
    {
        assertThrows(NullPointerException.class,
                () -> PayloadType.of("example", null, STRINGS));
        assertThrows(IllegalArgumentException.class,
                () -> PayloadType.of("example", "", STRINGS));
        assertThrows(IllegalArgumentException.class,
                () -> PayloadType.of("example", "/telemetry", STRINGS));
        assertThrows(IllegalArgumentException.class,
                () -> PayloadType.of("example", "telemetry/", STRINGS));
        assertThrows(IllegalArgumentException.class,
                () -> PayloadType.of("example", "Telemetry", STRINGS));
        assertThrows(IllegalArgumentException.class,
                () -> PayloadType.of("example",
                        "a".repeat(NetworkProtocol.MAX_CHANNEL_CHARS + 1), STRINGS));
    }

    @Test
    void oversizedCombinedChannelIsRejected()
    {
        String modId = "m".repeat(NetworkProtocol.MAX_MOD_ID_CHARS);
        String path = "p".repeat(NetworkProtocol.MAX_CHANNEL_CHARS - 10);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PayloadType.of(modId, path, STRINGS));

        assertTrue(failure.getMessage().contains("channel id must be at most "
                + NetworkProtocol.MAX_CHANNEL_CHARS + " chars"), failure.getMessage());
    }

    @Test
    void codecMustNotBeNull()
    {
        assertThrows(NullPointerException.class,
                () -> PayloadType.of("example", "telemetry", null));
        assertThrows(NullPointerException.class,
                () -> PayloadType.of("example", "telemetry", 1, null));
    }

    @Test
    void equalityAndHashAreBasedOnChannel()
    {
        PayloadType<String> first = PayloadType.of("example", "telemetry", STRINGS);
        PayloadType<Integer> second = PayloadType.of("example", "telemetry", INTS);
        PayloadType<String> otherVersion = PayloadType.of("example", "telemetry", 2, STRINGS);
        PayloadType<String> otherPath = PayloadType.of("example", "other", STRINGS);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, otherVersion);
        assertNotEquals(first, otherPath);
        assertNotEquals(first, null);
        assertNotEquals(first, "example:telemetry/v1");
    }
}
