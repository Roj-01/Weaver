package net.weaver.api.game.network;

import net.weaver.api.network.HandshakeRequest;
import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.PayloadBuffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeEnvelopeTest
{
    private static final HandshakeRequest REQUEST = new HandshakeRequest(
            NetworkProtocol.VERSION, "2.1.0", "26.3",
            new NetworkPlayer(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "TestPlayer"),
            List.of(NetworkModInfo.of("example", "1.0.0")),
            List.of("example:ping/v1"));

    @Test
    void requestRoundTripsThroughTheWireFormat()
    {
        byte[] handshake = HandshakeRequest.CODEC.encode(REQUEST);
        BridgeEnvelope original = BridgeEnvelope.handshake(BridgeEnvelope.Kind.REQUEST,
                NetworkProtocol.VERSION, NetworkPhase.CONFIGURATION, handshake);

        BridgeEnvelope decoded = BridgeEnvelope.decode(original.encode());

        assertEquals(BridgeEnvelope.Kind.REQUEST, decoded.kind());
        assertEquals(NetworkProtocol.VERSION, decoded.protocolVersion());
        assertEquals(NetworkPhase.CONFIGURATION, decoded.phase());
        assertEquals("", decoded.channel());
        assertEquals("", decoded.reason());
        assertEquals(0, decoded.payload().length);
        assertArrayEquals(handshake, decoded.handshake());
    }

    @Test
    void payloadRoundTripsAndCopiesBytes()
    {
        byte[] body = new byte[]{1, 2, 3, 4, 5};
        BridgeEnvelope original = BridgeEnvelope.payload(NetworkProtocol.VERSION,
                NetworkPhase.PLAY, "example:ping/v1", body);
        body[0] = 99;

        BridgeEnvelope decoded = BridgeEnvelope.decode(original.encode());

        assertEquals(BridgeEnvelope.Kind.PAYLOAD, decoded.kind());
        assertEquals("example:ping/v1", decoded.channel());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, decoded.payload());
        byte[] accessor = decoded.payload();
        accessor[0] = 42;
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, decoded.payload());
    }

    @Test
    void disconnectRoundTripsWithABoundedReason()
    {
        BridgeEnvelope original = BridgeEnvelope.disconnect(NetworkProtocol.VERSION,
                NetworkPhase.PLAY, "test reason");

        BridgeEnvelope decoded = BridgeEnvelope.decode(original.encode());

        assertEquals(BridgeEnvelope.Kind.DISCONNECT, decoded.kind());
        assertEquals("test reason", decoded.reason());
        assertEquals("", decoded.channel());
        assertEquals(0, decoded.payload().length);
        assertEquals(0, decoded.handshake().length);
    }

    @Test
    void oversizedPayloadIsRejectedBeforeEncoding()
    {
        byte[] tooLarge = new byte[BridgeEnvelope.MAX_PAYLOAD_BYTES + 1];

        assertThrows(NetworkException.class, () -> BridgeEnvelope.payload(
                NetworkProtocol.VERSION, NetworkPhase.PLAY, "example:ping/v1", tooLarge));
    }

    @Test
    void nonHandshakeKindCannotCarryAHandshakeBody()
    {
        assertThrows(NetworkException.class, () -> BridgeEnvelope.handshake(
                BridgeEnvelope.Kind.PAYLOAD, NetworkProtocol.VERSION,
                NetworkPhase.PLAY, new byte[]{1}));
    }

    @Test
    void unknownEnvelopeVersionIsRejected()
    {
        byte[] encoded = BridgeEnvelope.disconnect(NetworkProtocol.VERSION,
                NetworkPhase.PLAY, "bye").encode();
        encoded[0] = 2;

        assertThrows(NetworkException.class, () -> BridgeEnvelope.decode(encoded));
    }

    @Test
    void trailingBytesAreRejected()
    {
        byte[] encoded = BridgeEnvelope.disconnect(NetworkProtocol.VERSION,
                NetworkPhase.PLAY, "bye").encode();
        byte[] longer = new byte[encoded.length + 1];
        System.arraycopy(encoded, 0, longer, 0, encoded.length);

        NetworkException failure = assertThrows(NetworkException.class,
                () -> BridgeEnvelope.decode(longer));
        assertTrue(failure.getMessage().contains("unread"), failure.getMessage());
    }

    @Test
    void invalidUtf8ChannelIsRejectedByTheStrictDecoder()
    {
        PayloadBuffer out = PayloadBuffer.writer();
        out.writeByte(BridgeEnvelope.ENVELOPE_VERSION);
        out.writeByte(BridgeEnvelope.Kind.PAYLOAD.ordinal());
        out.writeVarInt(NetworkProtocol.VERSION);
        out.writeByte(NetworkPhase.PLAY.ordinal());
        out.writeVarInt(1);
        out.writeByte(0xFF);
        out.writeVarInt(0);
        out.writeVarInt(0);
        out.writeVarInt(0);

        assertThrows(NetworkException.class,
                () -> BridgeEnvelope.decode(out.toByteArray()));
    }

    @Test
    void unknownKindOrdinalIsRejected()
    {
        PayloadBuffer out = PayloadBuffer.writer();
        out.writeByte(BridgeEnvelope.ENVELOPE_VERSION);
        out.writeByte(BridgeEnvelope.Kind.values().length);
        out.writeVarInt(NetworkProtocol.VERSION);
        out.writeByte(NetworkPhase.PLAY.ordinal());
        out.writeVarInt(0);
        out.writeVarInt(0);
        out.writeVarInt(0);
        out.writeVarInt(0);

        assertThrows(NetworkException.class,
                () -> BridgeEnvelope.decode(out.toByteArray()));
    }

    @Test
    void channelIsBoundedByTheApiLimit()
    {
        String longChannel = "a".repeat(BridgeEnvelope.MAX_CHANNEL_CHARS + 1);

        assertThrows(NetworkException.class, () -> BridgeEnvelope.payload(
                NetworkProtocol.VERSION, NetworkPhase.PLAY, longChannel,
                "x".getBytes(StandardCharsets.UTF_8)));
    }
}
