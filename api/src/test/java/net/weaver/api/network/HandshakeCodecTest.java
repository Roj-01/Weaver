package net.weaver.api.network;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandshakeCodecTest
{
    private static final UUID PLAYER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final NetworkPlayer PLAYER = new NetworkPlayer(PLAYER_ID, "CodecPlayer");
    private static final List<NetworkModInfo> MODS = List.of(
            NetworkModInfo.of("alpha", "1.0.0"),
            NetworkModInfo.of("beta", "2.1.3"));
    private static final List<String> CHANNELS = List.of(
            "alpha:telemetry/v1",
            "alpha:config/v2",
            "beta:events/v3");

    @Test
    void requestRoundTripPreservesEveryField()
    {
        HandshakeRequest request = new HandshakeRequest(NetworkProtocol.VERSION,
                "2.1.0", "26.3", PLAYER, MODS, CHANNELS);

        byte[] encoded = HandshakeRequest.CODEC.encode(request);
        HandshakeRequest decoded = HandshakeRequest.CODEC.decode(encoded);

        assertEquals(request, decoded);
        assertEquals(NetworkProtocol.VERSION, decoded.protocolVersion());
        assertEquals("2.1.0", decoded.weaverVersion());
        assertEquals("26.3", decoded.gameVersion());
        assertEquals(PLAYER, decoded.player());
        assertEquals(MODS, decoded.mods());
        assertEquals(CHANNELS, decoded.channels());
    }

    @Test
    void acceptedResponseRoundTripPreservesEveryField()
    {
        HandshakeResponse response = HandshakeResponse.accept(NetworkProtocol.VERSION,
                "2.1.0", "26.3", MODS, CHANNELS);

        HandshakeResponse decoded = HandshakeResponse.CODEC.decode(
                HandshakeResponse.CODEC.encode(response));

        assertEquals(response, decoded);
        assertTrue(decoded.accepted());
        assertEquals("", decoded.reason());
        assertEquals(MODS, decoded.mods());
        assertEquals(CHANNELS, decoded.channels());
    }

    @Test
    void rejectedResponseRoundTripPreservesReason()
    {
        HandshakeResponse response = HandshakeResponse.reject("mod version mismatch: alpha",
                NetworkProtocol.VERSION, "2.1.0", "26.3", MODS, CHANNELS);

        HandshakeResponse decoded = HandshakeResponse.CODEC.decode(
                HandshakeResponse.CODEC.encode(response));

        assertEquals(response, decoded);
        assertFalse(decoded.accepted());
        assertEquals("mod version mismatch: alpha", decoded.reason());
    }

    @Test
    void emptyModAndChannelListsRoundTrip()
    {
        HandshakeRequest request = new HandshakeRequest(1, "2.1.0", "26.3", PLAYER,
                List.of(), List.of());
        HandshakeRequest decoded = HandshakeRequest.CODEC.decode(
                HandshakeRequest.CODEC.encode(request));

        assertEquals(request, decoded);
        assertTrue(decoded.mods().isEmpty());
        assertTrue(decoded.channels().isEmpty());
    }

    @Test
    void trailingBytesAreRejectedForRequestsAndResponses()
    {
        byte[] requestBytes = HandshakeRequest.CODEC.encode(new HandshakeRequest(1,
                "2.1.0", "26.3", PLAYER, MODS, CHANNELS));
        byte[] withTrailingRequest = Arrays.copyOf(requestBytes, requestBytes.length + 1);
        NetworkException requestFailure = assertThrows(NetworkException.class,
                () -> HandshakeRequest.CODEC.decode(withTrailingRequest));
        assertTrue(requestFailure.getMessage().contains("unread byte"), requestFailure.getMessage());

        byte[] responseBytes = HandshakeResponse.CODEC.encode(HandshakeResponse.accept(1,
                "2.1.0", "26.3", MODS, CHANNELS));
        byte[] withTrailingResponse = Arrays.copyOf(responseBytes, responseBytes.length + 1);
        NetworkException responseFailure = assertThrows(NetworkException.class,
                () -> HandshakeResponse.CODEC.decode(withTrailingResponse));
        assertTrue(responseFailure.getMessage().contains("unread byte"),
                responseFailure.getMessage());
    }

    @Test
    void truncatedPayloadsAreRejected()
    {
        byte[] requestBytes = HandshakeRequest.CODEC.encode(new HandshakeRequest(1,
                "2.1.0", "26.3", PLAYER, MODS, CHANNELS));
        byte[] truncatedRequest = Arrays.copyOf(requestBytes, requestBytes.length - 1);
        assertThrows(NetworkException.class, () -> HandshakeRequest.CODEC.decode(truncatedRequest));

        byte[] responseBytes = HandshakeResponse.CODEC.encode(HandshakeResponse.accept(1,
                "2.1.0", "26.3", MODS, CHANNELS));
        byte[] truncatedResponse = Arrays.copyOf(responseBytes, responseBytes.length - 1);
        assertThrows(NetworkException.class,
                () -> HandshakeResponse.CODEC.decode(truncatedResponse));

        assertThrows(NetworkException.class, () -> HandshakeRequest.CODEC.decode(new byte[0]));
        assertThrows(NetworkException.class, () -> HandshakeResponse.CODEC.decode(new byte[0]));
    }

    @Test
    void requestDecodeRejectsOversizedModAndChannelCounts()
    {
        PayloadBuffer tooManyMods = PayloadBuffer.writer();
        writeRequestPrefix(tooManyMods);
        tooManyMods.writeVarInt(NetworkProtocol.MAX_MODS + 1);

        NetworkException modFailure = assertThrows(NetworkException.class,
                () -> HandshakeRequest.CODEC.decode(tooManyMods.toByteArray()));
        assertTrue(modFailure.getMessage().contains("handshake mod count "
                + (NetworkProtocol.MAX_MODS + 1) + " is outside 0.."
                + NetworkProtocol.MAX_MODS), modFailure.getMessage());

        PayloadBuffer tooManyChannels = PayloadBuffer.writer();
        writeRequestPrefix(tooManyChannels);
        tooManyChannels.writeVarInt(0);
        tooManyChannels.writeVarInt(NetworkProtocol.MAX_CHANNELS + 1);

        NetworkException channelFailure = assertThrows(NetworkException.class,
                () -> HandshakeRequest.CODEC.decode(tooManyChannels.toByteArray()));
        assertTrue(channelFailure.getMessage().contains("handshake channel count "
                + (NetworkProtocol.MAX_CHANNELS + 1) + " is outside 0.."
                + NetworkProtocol.MAX_CHANNELS), channelFailure.getMessage());
    }

    @Test
    void requestDecodeRejectsNegativeCounts()
    {
        PayloadBuffer negativeMods = PayloadBuffer.writer();
        writeRequestPrefix(negativeMods);
        negativeMods.writeVarInt(-1);

        NetworkException failure = assertThrows(NetworkException.class,
                () -> HandshakeRequest.CODEC.decode(negativeMods.toByteArray()));

        assertTrue(failure.getMessage().contains("handshake mod count -1 is outside"),
                failure.getMessage());
    }

    private static void writeRequestPrefix(PayloadBuffer out)
    {
        out.writeVarInt(1);
        out.writeString("2.1.0", NetworkProtocol.MAX_STRING_CHARS);
        out.writeString("26.3", NetworkProtocol.MAX_STRING_CHARS);
        out.writeUuid(PLAYER_ID);
        out.writeString(PLAYER.name(), NetworkProtocol.MAX_PLAYER_NAME_CHARS);
    }

    @Test
    void constructorsRejectDuplicateModsAndChannels()
    {
        IllegalArgumentException duplicateMods = assertThrows(IllegalArgumentException.class,
                () -> new HandshakeRequest(1, "2.1.0", "26.3", PLAYER,
                        List.of(NetworkModInfo.of("alpha", "1.0.0"),
                                NetworkModInfo.of("alpha", "1.0.1")),
                        List.of()));
        assertTrue(duplicateMods.getMessage().contains("duplicate id 'alpha'"),
                duplicateMods.getMessage());

        IllegalArgumentException duplicateChannels = assertThrows(IllegalArgumentException.class,
                () -> new HandshakeRequest(1, "2.1.0", "26.3", PLAYER, List.of(),
                        List.of("alpha:telemetry/v1", "alpha:telemetry/v1")));
        assertTrue(duplicateChannels.getMessage().contains("duplicate id 'alpha:telemetry/v1'"),
                duplicateChannels.getMessage());

        IllegalArgumentException responseChannels = assertThrows(IllegalArgumentException.class,
                () -> HandshakeResponse.accept(1, "2.1.0", "26.3", List.of(),
                        List.of("alpha:telemetry/v1", "alpha:telemetry/v1")));
        assertTrue(responseChannels.getMessage().contains("duplicate id"),
                responseChannels.getMessage());
    }

    @Test
    void constructorsRejectMissingBoundaryValues()
    {
        assertThrows(IllegalArgumentException.class,
                () -> new HandshakeRequest(0, "2.1.0", "26.3", PLAYER, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new HandshakeRequest(1, " ", "26.3", PLAYER, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new HandshakeRequest(1, "2.1.0", "", PLAYER, List.of(), List.of()));
        assertThrows(NullPointerException.class,
                () -> new HandshakeRequest(1, "2.1.0", "26.3", null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new HandshakeResponse(true, "", 0, "2.1.0", "26.3", List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new HandshakeResponse(false, "  ", 1, "2.1.0", "26.3", List.of(),
                        List.of()));
        assertThrows(NullPointerException.class,
                () -> new HandshakeResponse(true, "", 1, "2.1.0", "26.3", null, List.of()));
    }

    @Test
    void codecRejectsNullInputs()
    {
        assertThrows(NullPointerException.class,
                () -> HandshakeRequest.CODEC.encode(null));
        assertThrows(NullPointerException.class,
                () -> HandshakeRequest.CODEC.decode((byte[]) null));
        assertThrows(NullPointerException.class,
                () -> HandshakeResponse.CODEC.decode((byte[]) null));
    }
}
