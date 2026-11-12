package net.weaver.api.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadCodecTest
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

    @Test
    void defaultEncodeAndDecodeRoundTrip()
    {
        byte[] encoded = STRINGS.encode("payload text");

        assertEquals("payload text", STRINGS.decode(encoded));
        assertEquals(1 + "payload text".length(), encoded.length);
    }

    @Test
    void defaultDecodeFailsWhenBytesRemainUnread()
    {
        PayloadCodec<Integer> leavingBytes = new PayloadCodec<>()
        {
            @Override
            public void encode(Integer value, PayloadBuffer out)
            {
                out.writeInt(value);
            }

            @Override
            public Integer decode(PayloadBuffer in)
            {
                return in.readUnsignedByte();
            }
        };

        byte[] encoded = leavingBytes.encode(42);

        NetworkException failure = assertThrows(NetworkException.class,
                () -> leavingBytes.decode(encoded));

        assertEquals("payload has 3 unread byte(s)", failure.getMessage());
    }

    @Test
    void defaultDecodeFailsWhenCodecConsumesNothing()
    {
        PayloadCodec<String> consumesNothing = new PayloadCodec<>()
        {
            @Override
            public void encode(String value, PayloadBuffer out)
            {
                out.writeByte(1);
            }

            @Override
            public String decode(PayloadBuffer in)
            {
                return "constant";
            }
        };

        NetworkException failure = assertThrows(NetworkException.class,
                () -> consumesNothing.decode(new byte[] { 1, 2 }));

        assertEquals("payload has 2 unread byte(s)", failure.getMessage());
    }

    @Test
    void defaultDecodeRejectsTruncatedPayloads()
    {
        assertThrows(NetworkException.class, () -> STRINGS.decode(new byte[0]));

        byte[] encoded = STRINGS.encode("abc");
        byte[] truncated = new byte[encoded.length - 1];
        System.arraycopy(encoded, 0, truncated, 0, truncated.length);

        NetworkException failure = assertThrows(NetworkException.class,
                () -> STRINGS.decode(truncated));
        assertTrue(failure.getMessage().contains("payload underflow"), failure.getMessage());
    }

    @Test
    void nullArgumentsAreRejected()
    {
        assertThrows(NullPointerException.class, () -> STRINGS.encode(null));
        assertThrows(NullPointerException.class, () -> STRINGS.decode((byte[]) null));
        assertThrows(NullPointerException.class,
                () -> STRINGS.encode(null, PayloadBuffer.writer()));
        assertThrows(NullPointerException.class,
                () -> STRINGS.decode((PayloadBuffer) null));
    }

    @Test
    void explicitWriterEncodeProducesTheSameBytesAsDefaultEncode()
    {
        PayloadBuffer writer = PayloadBuffer.writer();
        STRINGS.encode("same", writer);

        byte[] explicit = writer.toByteArray();
        byte[] convenience = STRINGS.encode("same");

        assertEquals(convenience.length, explicit.length);
        for (int index = 0; index < convenience.length; index++)
        {
            assertEquals(convenience[index], explicit[index]);
        }
    }
}
