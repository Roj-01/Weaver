package net.weaver.core.network;

import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkProtocol;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkWireTest
{
    private static final int MAGIC = 0x57455632;

    @Test
    void everyFrameTypeEncodesAndReadsBack() throws IOException
    {
        for (NetworkWire.FrameType type : NetworkWire.FrameType.values())
        {
            byte[] body = ("body-" + type.name()).getBytes(StandardCharsets.UTF_8);
            byte[] encoded = NetworkWire.encode(type, body);

            NetworkWire.WireFrame frame = read(encoded);

            assertEquals(type, frame.type());
            assertArrayEquals(body, frame.body());
        }
    }

    @Test
    void emptyBodiesRoundTrip() throws IOException
    {
        byte[] encoded = NetworkWire.encode(NetworkWire.FrameType.CONFIG_READY, new byte[0]);

        NetworkWire.WireFrame frame = read(encoded);

        assertEquals(NetworkWire.FrameType.CONFIG_READY, frame.type());
        assertEquals(0, frame.body().length);
    }

    @Test
    void frameBodyIsCopiedByEncodeAndByFrameConstructor()
    {
        byte[] body = { 1, 2, 3 };
        byte[] encoded = NetworkWire.encode(NetworkWire.FrameType.PAYLOAD, body);
        body[0] = 9;

        assertArrayEquals(new byte[] { 1, 2, 3 },
                java.util.Arrays.copyOfRange(encoded, 11, encoded.length));

        byte[] constructedBody = { 4, 5 };
        NetworkWire.WireFrame frame = new NetworkWire.WireFrame(NetworkWire.FrameType.HELLO,
                constructedBody);
        constructedBody[0] = 9;
        assertArrayEquals(new byte[] { 4, 5 }, frame.body());
    }

    @Test
    void badMagicIsRejected()
    {
        NetworkException failure = assertThrows(NetworkException.class,
                () -> read(frame(0, 1, 1, new byte[0])));

        assertEquals("invalid frame magic: 0x0 (expected 0x57455632)", failure.getMessage());
    }

    @Test
    void unsupportedWireVersionIsRejected()
    {
        NetworkException failure = assertThrows(NetworkException.class,
                () -> read(frame(MAGIC, 2, 1, new byte[0])));

        assertEquals("unsupported wire version: 2 (expected 1)", failure.getMessage());
    }

    @Test
    void unknownFrameTypeIsRejected()
    {
        NetworkException failure = assertThrows(NetworkException.class,
                () -> read(frame(MAGIC, 1, 99, new byte[0])));

        assertEquals("unknown frame type: 99", failure.getMessage());
    }

    @Test
    void negativeBodyLengthIsRejected()
    {
        NetworkException failure = assertThrows(NetworkException.class,
                () -> read(header(MAGIC, 1, 1, -1)));

        assertEquals("negative frame body length: -1", failure.getMessage());
    }

    @Test
    void oversizedBodyLengthIsRejectedBeforeAllocation()
    {
        NetworkException failure = assertThrows(NetworkException.class,
                () -> read(header(MAGIC, 1, 1, NetworkProtocol.MAX_FRAME_BYTES + 1)));

        assertEquals("frame body length " + (NetworkProtocol.MAX_FRAME_BYTES + 1)
                + " exceeds the maximum of " + NetworkProtocol.MAX_FRAME_BYTES,
                failure.getMessage());
    }

    @Test
    void oversizedBodyIsRejectedOnEncode()
    {
        NetworkException failure = assertThrows(NetworkException.class,
                () -> NetworkWire.encode(NetworkWire.FrameType.PAYLOAD,
                        new byte[NetworkProtocol.MAX_FRAME_BYTES + 1]));

        assertTrue(failure.getMessage().contains("frame body of "
                + (NetworkProtocol.MAX_FRAME_BYTES + 1) + " bytes exceeds the maximum"),
                failure.getMessage());
    }

    @Test
    void truncatedHeaderIsReportedAsIOException()
    {
        byte[] complete = header(MAGIC, 1, 1, 0);
        for (int length = 0; length < 11; length++)
        {
            byte[] truncated = java.util.Arrays.copyOf(complete, length);
            IOException failure = assertThrows(IOException.class, () -> read(truncated),
                    "length " + length);
            if (length >= 4)
            {
                assertTrue(failure.getMessage().contains("truncated network frame"),
                        failure.getMessage());
            }
        }
    }

    @Test
    void truncatedBodyIsReportedAsIOException()
    {
        byte[] complete = NetworkWire.encode(NetworkWire.FrameType.PAYLOAD,
                new byte[] { 1, 2, 3, 4 });
        byte[] truncated = java.util.Arrays.copyOf(complete, complete.length - 2);

        IOException failure = assertThrows(IOException.class, () -> read(truncated));

        assertTrue(failure.getMessage().contains("truncated network frame"),
                failure.getMessage());
    }

    @Test
    void cleanEndOfStreamIsEofException()
    {
        assertThrows(EOFException.class, () -> read(new byte[0]));
    }

    @Test
    void nullArgumentsAreRejected()
    {
        assertThrows(NullPointerException.class,
                () -> NetworkWire.encode(null, new byte[0]));
        assertThrows(NullPointerException.class,
                () -> NetworkWire.encode(NetworkWire.FrameType.HELLO, null));
        assertThrows(NullPointerException.class, () -> NetworkWire.read(null));
        assertThrows(NullPointerException.class,
                () -> new NetworkWire.WireFrame(null, new byte[0]));
        assertThrows(NullPointerException.class,
                () -> new NetworkWire.WireFrame(NetworkWire.FrameType.HELLO, null));
    }

    private static NetworkWire.WireFrame read(byte[] encoded) throws IOException
    {
        return NetworkWire.read(new DataInputStream(new ByteArrayInputStream(encoded)));
    }

    private static byte[] frame(int magic, int version, int type, byte[] body)
    {
        return header(magic, version, type, body.length, body);
    }

    private static byte[] header(int magic, int version, int type, int length)
    {
        return header(magic, version, type, length, new byte[Math.max(0, Math.min(length, 0))]);
    }

    private static byte[] header(int magic, int version, int type, int length, byte[] body)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes))
        {
            out.writeInt(magic);
            out.writeShort(version);
            out.writeByte(type);
            out.writeInt(length);
            out.write(body);
        }
        catch (IOException failure)
        {
            throw new AssertionError(failure);
        }
        return bytes.toByteArray();
    }
}
