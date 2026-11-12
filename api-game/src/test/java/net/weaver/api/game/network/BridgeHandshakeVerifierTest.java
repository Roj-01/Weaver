package net.weaver.api.game.network;

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

class BridgeHandshakeVerifierTest
{
    private static final NetworkPlayer PLAYER =
            new NetworkPlayer(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "TestPlayer");
    private static final List<NetworkModInfo> MODS =
            List.of(NetworkModInfo.of("example", "1.0.0"));
    private static final List<String> CHANNELS = List.of("example:ping/v1");

    @Test
    void acceptsACompatibleRequest()
    {
        HandshakeResponse response = verify(request(NetworkProtocol.VERSION, "26.3",
                "2.1.0", MODS), null);

        assertTrue(response.accepted());
        assertEquals("", response.reason());
        assertEquals(MODS, response.mods());
        assertEquals(CHANNELS, response.channels());
    }

    @Test
    void rejectsAProtocolMismatch()
    {
        HandshakeResponse response = verify(request(2, "26.3", "2.1.0", MODS), null);

        assertFalse(response.accepted());
        assertEquals("protocol mismatch: client=2, server=1", response.reason());
    }

    @Test
    void rejectsAGameVersionMismatch()
    {
        HandshakeResponse response = verify(request(NetworkProtocol.VERSION, "26.2",
                "2.1.0", MODS), null);

        assertFalse(response.accepted());
        assertEquals("game version mismatch: client=26.2, server=26.3",
                response.reason());
    }

    @Test
    void rejectsAnIncompatibleWeaverVersion()
    {
        HandshakeResponse response = verify(request(NetworkProtocol.VERSION, "26.3",
                "1.9.0", MODS), null);

        assertFalse(response.accepted());
        assertTrue(response.reason().startsWith("weaver version mismatch: client=1.9.0"),
                response.reason());
    }

    @Test
    void rejectsAMissingServerMod()
    {
        HandshakeResponse response = verify(request(NetworkProtocol.VERSION, "26.3",
                "2.1.0", List.of()), null);

        assertFalse(response.accepted());
        assertEquals("missing required mod: example (server=1.0.0)", response.reason());
    }

    @Test
    void rejectsAModVersionMismatch()
    {
        HandshakeResponse response = verify(request(NetworkProtocol.VERSION, "26.3",
                "2.1.0", List.of(NetworkModInfo.of("example", "2.0.0"))), null);

        assertFalse(response.accepted());
        assertEquals("mod version mismatch: example (client=2.0.0, server=1.0.0)",
                response.reason());
    }

    @Test
    void policyCanVetoAnOtherwiseValidRequest()
    {
        HandshakePolicy policy = (request, localMods) ->
                HandshakeDecision.reject("policy says no");

        HandshakeResponse response = verify(request(NetworkProtocol.VERSION, "26.3",
                "2.1.0", MODS), policy);

        assertFalse(response.accepted());
        assertEquals("policy says no", response.reason());
    }

    @Test
    void nullPolicyDecisionIsRejected()
    {
        HandshakePolicy policy = (request, localMods) -> null;

        HandshakeResponse response = verify(request(NetworkProtocol.VERSION, "26.3",
                "2.1.0", MODS), policy);

        assertFalse(response.accepted());
        assertEquals("handshake policy returned no decision", response.reason());
    }

    @Test
    void duplicateLocalModsAreALocalConfigurationError()
    {
        assertThrows(IllegalArgumentException.class,
                () -> BridgeHandshakeVerifier.verify(request(NetworkProtocol.VERSION,
                                "26.3", "2.1.0", MODS),
                        List.of(NetworkModInfo.of("example", "1.0.0"),
                                NetworkModInfo.of("example", "1.0.0")),
                        CHANNELS, null, NetworkProtocol.VERSION, WeaverApi.VERSION,
                        WeaverApi.GAME_VERSION));
    }

    @Test
    void checkResponseAcceptsACompatibleResponse()
    {
        HandshakeRequest request = request(NetworkProtocol.VERSION, "26.3", "2.1.0",
                MODS);

        BridgeHandshakeVerifier.checkResponse(HandshakeResponse.accept(
                NetworkProtocol.VERSION, "2.1.0", "26.3", MODS, CHANNELS), request);
    }

    @Test
    void checkResponseSurfacesARejection()
    {
        HandshakeRequest request = request(NetworkProtocol.VERSION, "26.3", "2.1.0",
                MODS);

        NetworkException failure = assertThrows(NetworkException.class,
                () -> BridgeHandshakeVerifier.checkResponse(HandshakeResponse.reject(
                        "nope", NetworkProtocol.VERSION, "2.1.0", "26.3", MODS,
                        CHANNELS), request));
        assertEquals("handshake rejected: nope", failure.getMessage());
    }

    @Test
    void checkResponseRejectsServerOnlyMods()
    {
        HandshakeRequest request = request(NetworkProtocol.VERSION, "26.3", "2.1.0",
                List.of());

        NetworkException failure = assertThrows(NetworkException.class,
                () -> BridgeHandshakeVerifier.checkResponse(HandshakeResponse.accept(
                        NetworkProtocol.VERSION, "2.1.0", "26.3", MODS, CHANNELS),
                        request));
        assertEquals("server requires mod: example (server=1.0.0)", failure.getMessage());
    }

    @Test
    void checkResponseRejectsADifferentProtocol()
    {
        HandshakeRequest request = request(NetworkProtocol.VERSION, "26.3", "2.1.0",
                MODS);

        NetworkException failure = assertThrows(NetworkException.class,
                () -> BridgeHandshakeVerifier.checkResponse(HandshakeResponse.accept(
                        2, "2.1.0", "26.3", MODS, CHANNELS), request));
        assertEquals("protocol mismatch: client=1, server=2", failure.getMessage());
    }

    private static HandshakeResponse verify(HandshakeRequest request,
                                            HandshakePolicy policy)
    {
        return BridgeHandshakeVerifier.verify(request, MODS, CHANNELS, policy,
                NetworkProtocol.VERSION, WeaverApi.VERSION, WeaverApi.GAME_VERSION);
    }

    private static HandshakeRequest request(int protocolVersion, String gameVersion,
                                            String weaverVersion,
                                            List<NetworkModInfo> mods)
    {
        return new HandshakeRequest(protocolVersion, weaverVersion, gameVersion, PLAYER,
                mods, CHANNELS);
    }
}
