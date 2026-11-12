package net.weaver.core.network;

import net.weaver.api.WeaverApi;
import net.weaver.api.network.HandshakeDecision;
import net.weaver.api.network.HandshakePolicy;
import net.weaver.api.network.HandshakeRequest;
import net.weaver.api.network.HandshakeResponse;
import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandshakeVerifierTest
{
    private static final NetworkPlayer PLAYER =
            new NetworkPlayer(UUID.randomUUID(), "VerifierPlayer");
    private static final List<NetworkModInfo> SERVER_MODS = List.of(
            NetworkModInfo.of("testmod", "1.0.0"),
            NetworkModInfo.of("shared", "2.0.0"));
    private static final List<String> SERVER_CHANNELS = List.of(
            "testmod:ping/v1",
            "testmod:pong/v1");

    @Test
    void matchingHandshakeIsAccepted()
    {
        HandshakeResponse response = verify(request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS), SERVER_MODS, SERVER_CHANNELS, null);

        assertTrue(response.accepted());
        assertEquals("", response.reason());
        assertEquals(NetworkProtocol.VERSION, response.protocolVersion());
        assertEquals(WeaverApi.VERSION, response.weaverVersion());
        assertEquals(WeaverApi.GAME_VERSION, response.gameVersion());
        assertEquals(SERVER_MODS, response.mods());
        assertEquals(SERVER_CHANNELS, response.channels());
    }

    @Test
    void protocolMismatchProducesExactMessage()
    {
        HandshakeResponse response = verify(request(2, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS), SERVER_MODS, SERVER_CHANNELS, null);

        assertFalse(response.accepted());
        assertEquals("protocol mismatch: client=2, server=1", response.reason());
        assertEquals(SERVER_MODS, response.mods());
        assertEquals(SERVER_CHANNELS, response.channels());
    }

    @Test
    void gameVersionMismatchProducesExactMessage()
    {
        HandshakeResponse response = verify(request(1, WeaverApi.VERSION, "26.4",
                SERVER_MODS, SERVER_CHANNELS), SERVER_MODS, SERVER_CHANNELS, null);

        assertFalse(response.accepted());
        assertEquals("game version mismatch: client=26.4, server=26.3", response.reason());
    }

    @Test
    void incompatibleWeaverVersionIsRejected()
    {
        HandshakeResponse tooOld = verify(request(1, "2.0.0", WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS), SERVER_MODS, SERVER_CHANNELS, null);

        assertFalse(tooOld.accepted());
        assertEquals("weaver version mismatch: client=2.0.0, server=2.1.0 "
                + "(requires API 2.1.0 or newer within major 2)", tooOld.reason());

        HandshakeResponse wrongMajor = verify(request(1, "3.0.0", WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS), SERVER_MODS, SERVER_CHANNELS, null);

        assertFalse(wrongMajor.accepted());
        assertTrue(wrongMajor.reason().startsWith(
                "weaver version mismatch: client=3.0.0, server=2.1.0"),
                wrongMajor.reason());
    }

    @Test
    void missingServerModIsRejected()
    {
        List<NetworkModInfo> clientMods = List.of(
                NetworkModInfo.of("testmod", "1.0.0"),
                NetworkModInfo.of("clientonly", "9.9.9"));
        HandshakeResponse response = verify(request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                clientMods, SERVER_CHANNELS), SERVER_MODS, SERVER_CHANNELS, null);

        assertFalse(response.accepted());
        assertEquals("missing required mod: shared (server=2.0.0)", response.reason());
    }

    @Test
    void modVersionMismatchIsRejected()
    {
        List<NetworkModInfo> clientMods = List.of(
                NetworkModInfo.of("testmod", "1.0.1"),
                NetworkModInfo.of("shared", "2.0.0"));
        HandshakeResponse response = verify(request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                clientMods, SERVER_CHANNELS), SERVER_MODS, SERVER_CHANNELS, null);

        assertFalse(response.accepted());
        assertEquals("mod version mismatch: testmod (client=1.0.1, server=1.0.0)",
                response.reason());
    }

    @Test
    void extraClientOnlyModsAreAccepted()
    {
        List<NetworkModInfo> clientMods = List.of(
                NetworkModInfo.of("testmod", "1.0.0"),
                NetworkModInfo.of("shared", "2.0.0"),
                NetworkModInfo.of("clientonly", "9.9.9"));
        HandshakeResponse response = verify(request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                clientMods, SERVER_CHANNELS), SERVER_MODS, SERVER_CHANNELS, null);

        assertTrue(response.accepted());
        assertEquals(SERVER_MODS, response.mods());
    }

    @Test
    void policyRejectionReasonIsPropagatedAndSeesLocalMods()
    {
        HandshakePolicy policy = (request, localMods) ->
        {
            assertEquals(SERVER_MODS, localMods);
            assertThrows(UnsupportedOperationException.class, localMods::clear);
            return HandshakeDecision.reject("server policy says no");
        };

        HandshakeResponse response = verify(request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS), SERVER_MODS, SERVER_CHANNELS, policy);

        assertFalse(response.accepted());
        assertEquals("server policy says no", response.reason());
    }

    @Test
    void nullPolicyDecisionIsRejected()
    {
        HandshakePolicy policy = (request, localMods) -> null;

        HandshakeResponse response = verify(request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS), SERVER_MODS, SERVER_CHANNELS, policy);

        assertFalse(response.accepted());
        assertEquals("handshake policy returned no decision", response.reason());
    }

    @Test
    void acceptAllPolicyAccepts()
    {
        HandshakePolicy policy = HandshakeVerifier.acceptAll();

        HandshakeResponse response = verify(request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS), SERVER_MODS, SERVER_CHANNELS, policy);

        assertTrue(response.accepted());
    }

    @Test
    void checkResponsePrefixesServerRejection()
    {
        HandshakeRequest request = request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS);
        HandshakeResponse response = HandshakeResponse.reject("custom veto",
                NetworkProtocol.VERSION, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS);

        NetworkException failure = assertThrows(NetworkException.class,
                () -> HandshakeVerifier.checkResponse(response, request));

        assertEquals("handshake rejected: custom veto", failure.getMessage());
    }

    @Test
    void checkResponseRejectsServerModMissingFromClient()
    {
        HandshakeRequest request = request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                List.of(NetworkModInfo.of("testmod", "1.0.0")), List.of());
        HandshakeResponse response = HandshakeResponse.accept(NetworkProtocol.VERSION,
                WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                List.of(NetworkModInfo.of("serveronly", "3.0.0")), List.of());

        NetworkException failure = assertThrows(NetworkException.class,
                () -> HandshakeVerifier.checkResponse(response, request));

        assertEquals("server requires mod: serveronly (server=3.0.0)", failure.getMessage());
    }

    @Test
    void checkResponseRejectsVersionAndGameMismatch()
    {
        HandshakeRequest request = request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS);

        NetworkException protocol = assertThrows(NetworkException.class,
                () -> HandshakeVerifier.checkResponse(HandshakeResponse.accept(2,
                        WeaverApi.VERSION, WeaverApi.GAME_VERSION, SERVER_MODS,
                        SERVER_CHANNELS), request));
        assertEquals("protocol mismatch: client=1, server=2", protocol.getMessage());

        NetworkException game = assertThrows(NetworkException.class,
                () -> HandshakeVerifier.checkResponse(HandshakeResponse.accept(1,
                        WeaverApi.VERSION, "26.4", SERVER_MODS, SERVER_CHANNELS), request));
        assertEquals("game version mismatch: client=26.3, server=26.4", game.getMessage());
    }

    @Test
    void checkResponseRejectsIncompatibleWeaverVersion()
    {
        HandshakeRequest request = request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS);
        HandshakeResponse response = HandshakeResponse.accept(NetworkProtocol.VERSION,
                "2.0.0", WeaverApi.GAME_VERSION, SERVER_MODS, SERVER_CHANNELS);

        NetworkException failure = assertThrows(NetworkException.class,
                () -> HandshakeVerifier.checkResponse(response, request));

        assertTrue(failure.getMessage().startsWith(
                "weaver version mismatch: client=2.1.0, server=2.0.0"), failure.getMessage());
    }

    @Test
    void checkResponseAcceptsExtraClientModsAndMatchingServerMods()
    {
        HandshakeRequest request = request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                List.of(NetworkModInfo.of("testmod", "1.0.0"),
                        NetworkModInfo.of("shared", "2.0.0"),
                        NetworkModInfo.of("clientonly", "1.2.3")), SERVER_CHANNELS);
        HandshakeResponse response = HandshakeResponse.accept(NetworkProtocol.VERSION,
                WeaverApi.VERSION, WeaverApi.GAME_VERSION, SERVER_MODS, SERVER_CHANNELS);

        HandshakeVerifier.checkResponse(response, request);
    }

    @Test
    void duplicateLocalModsAreIllegalArgument()
    {
        List<NetworkModInfo> duplicates = List.of(
                NetworkModInfo.of("testmod", "1.0.0"),
                NetworkModInfo.of("testmod", "1.0.0"));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> verify(request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                        SERVER_MODS, SERVER_CHANNELS), duplicates, SERVER_CHANNELS, null));

        assertTrue(failure.getMessage().contains("localMods must not contain duplicate id 'testmod'"),
                failure.getMessage());
    }

    @Test
    void invalidLocalConfigurationIsRejected()
    {
        HandshakeRequest request = request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS);

        assertThrows(IllegalArgumentException.class, () -> HandshakeVerifier.verify(request,
                SERVER_MODS, SERVER_CHANNELS, null, 0, WeaverApi.VERSION, WeaverApi.GAME_VERSION));
        assertThrows(IllegalArgumentException.class, () -> HandshakeVerifier.verify(request,
                SERVER_MODS, SERVER_CHANNELS, null, 1, " ", WeaverApi.GAME_VERSION));
        assertThrows(IllegalArgumentException.class, () -> HandshakeVerifier.verify(request,
                SERVER_MODS, List.of(" "), null, 1, WeaverApi.VERSION, WeaverApi.GAME_VERSION));
        assertThrows(IllegalArgumentException.class, () -> HandshakeVerifier.verify(request,
                SERVER_MODS, List.of("a", "a"), null, 1, WeaverApi.VERSION,
                WeaverApi.GAME_VERSION));
        assertThrows(IllegalArgumentException.class, () -> HandshakeVerifier.verify(request,
                java.util.Arrays.asList(SERVER_MODS.get(0), null), SERVER_CHANNELS, null, 1,
                WeaverApi.VERSION, WeaverApi.GAME_VERSION));
        assertThrows(IllegalArgumentException.class, () -> HandshakeVerifier.verify(request,
                SERVER_MODS, SERVER_CHANNELS, null, 1, WeaverApi.VERSION, " "));
    }

    @Test
    void nullArgumentsAreRejected()
    {
        HandshakeRequest request = request(1, WeaverApi.VERSION, WeaverApi.GAME_VERSION,
                SERVER_MODS, SERVER_CHANNELS);

        assertThrows(NullPointerException.class, () -> HandshakeVerifier.verify(null,
                SERVER_MODS, SERVER_CHANNELS, null, 1, WeaverApi.VERSION, WeaverApi.GAME_VERSION));
        assertThrows(NullPointerException.class, () -> HandshakeVerifier.verify(request,
                null, SERVER_CHANNELS, null, 1, WeaverApi.VERSION, WeaverApi.GAME_VERSION));
        assertThrows(NullPointerException.class, () -> HandshakeVerifier.verify(request,
                SERVER_MODS, null, null, 1, WeaverApi.VERSION, WeaverApi.GAME_VERSION));
        assertThrows(NullPointerException.class, () -> HandshakeVerifier.verify(request,
                SERVER_MODS, SERVER_CHANNELS, null, 1, null, WeaverApi.GAME_VERSION));
        assertThrows(NullPointerException.class, () -> HandshakeVerifier.verify(request,
                SERVER_MODS, SERVER_CHANNELS, null, 1, WeaverApi.VERSION, null));
        assertThrows(NullPointerException.class,
                () -> HandshakeVerifier.checkResponse(null, request));
        assertThrows(NullPointerException.class,
                () -> HandshakeVerifier.checkResponse(HandshakeResponse.accept(1,
                        WeaverApi.VERSION, WeaverApi.GAME_VERSION, SERVER_MODS,
                        SERVER_CHANNELS), null));
    }

    private static HandshakeRequest request(int protocolVersion, String weaverVersion,
                                            String gameVersion, List<NetworkModInfo> mods,
                                            List<String> channels)
    {
        return new HandshakeRequest(protocolVersion, weaverVersion, gameVersion, PLAYER,
                mods, channels);
    }

    private static HandshakeResponse verify(HandshakeRequest request,
                                            List<NetworkModInfo> localMods,
                                            List<String> localChannels,
                                            HandshakePolicy policy)
    {
        return HandshakeVerifier.verify(request, localMods, localChannels, policy,
                NetworkProtocol.VERSION, WeaverApi.VERSION, WeaverApi.GAME_VERSION);
    }
}
