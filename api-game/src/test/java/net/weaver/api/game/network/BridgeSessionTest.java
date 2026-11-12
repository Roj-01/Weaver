package net.weaver.api.game.network;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.network.NetworkConnectedEvent;
import net.weaver.api.event.events.network.NetworkDisconnectedEvent;
import net.weaver.api.event.events.network.NetworkHandshakeEvent;
import net.weaver.api.event.events.network.NetworkPayloadEvent;
import net.weaver.api.network.HandshakeDecision;
import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadType;
import net.weaver.api.network.UnknownChannelPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeSessionTest
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

    private static final PayloadType<String> PING =
            PayloadType.of("test", "ping", STRINGS);
    private static final PayloadType<String> PONG =
            PayloadType.of("test", "pong", STRINGS);

    private static final NetworkPlayer CLIENT_PLAYER = new NetworkPlayer(
            UUID.fromString("00000000-0000-0000-0000-000000000002"), "Client");

    @Test
    void serverRejectsARequestClaimingAnotherPlayersUuid()
    {
        NetworkPlayer operator = new NetworkPlayer(
                UUID.fromString("00000000-0000-0000-0000-0000000000ff"), "Operator");
        // the game connection authenticated as "Attacker"; the modded client
        // claims the operator's UUID in its bridge request
        Fixture fixture = new Fixture(UnknownChannelPolicy.REJECT, null,
                new NetworkPlayer(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        "Attacker"));
        fixture.client.setLocalPlayer(operator);
        fixture.connect();

        assertFalse(fixture.server.isVerified());
        assertFalse(fixture.client.isVerified());
        assertFalse(fixture.server.isOpen());
        assertEquals(0, fixture.handshakeEvents.get());
        assertTrue(fixture.server.failureReason().contains("player identity mismatch"),
                fixture.server.failureReason());
        assertTrue(fixture.clientLink.disconnects.stream()
                        .anyMatch(reason -> reason.contains("player identity mismatch")),
                fixture.clientLink.disconnects.toString());
        assertEquals(new UUID(0L, 0L), fixture.server.player().id());
    }

    @Test
    void serverRejectsARequestBeforeAnAuthenticatedIdentityIsKnown()
    {
        Fixture fixture = new Fixture(UnknownChannelPolicy.REJECT, null, null);
        fixture.connect();

        assertFalse(fixture.server.isVerified());
        assertFalse(fixture.server.isOpen());
        assertTrue(fixture.server.failureReason().contains("player identity unavailable"),
                fixture.server.failureReason());
    }

    @Test
    void serverReportsTheAuthenticatedIdentityNotTheClaimedName()
    {
        Fixture fixture = new Fixture(UnknownChannelPolicy.REJECT, null, null);
        fixture.server.setAuthenticatedPlayer(
                new NetworkPlayer(CLIENT_PLAYER.id(), "RealName"));
        fixture.connectAndPlay();

        assertTrue(fixture.server.isVerified());
        assertEquals(CLIENT_PLAYER.id(), fixture.server.player().id());
        assertEquals("RealName", fixture.server.player().name());

        fixture.server.setAuthenticatedPlayer(CLIENT_PLAYER);
        assertEquals("RealName", fixture.server.player().name());
    }

    @Test
    void helloRequestResponseAndPayloadRoundTrip()
    {
        Fixture fixture = new Fixture(UnknownChannelPolicy.REJECT, null);
        fixture.connect();

        assertTrue(fixture.server.isVerified());
        assertTrue(fixture.client.isVerified());
        assertEquals("Client", fixture.server.player().name());
        assertEquals(1, fixture.handshakeEvents.get());
        assertEquals(NetworkProtocol.VERSION,
                fixture.client.clientRequest().protocolVersion());
        assertEquals("26.3", fixture.client.clientRequest().gameVersion());

        fixture.server.onPhase(NetworkPhase.PLAY);
        fixture.client.onPhase(NetworkPhase.PLAY);
        fixture.server.onPlayReady();
        fixture.client.onPlayReady();

        assertTrue(fixture.server.isConnectedPosted());
        assertTrue(fixture.client.isConnectedPosted());
        assertEquals(2, fixture.connectedEvents.get());

        fixture.client.send(PING, "hello");

        assertEquals(List.of("hello"), fixture.received);
        assertEquals(List.of("hello-pong"), fixture.pongs);
        assertEquals(2, fixture.payloadEvents.get());

        fixture.server.onTransportClosed("done", true);
        fixture.client.onTransportClosed("done", true);

        assertEquals(2, fixture.disconnectedEvents.get());
        assertFalse(fixture.server.isOpen());
        assertFalse(fixture.client.isOpen());
    }

    @Test
    void payloadsArrivingBeforePlayAreDrainedAfterTheConnectedEvent()
    {
        Fixture fixture = new Fixture(UnknownChannelPolicy.REJECT, null);
        fixture.connect();
        fixture.server.onPhase(NetworkPhase.PLAY);
        fixture.client.onPhase(NetworkPhase.PLAY);

        fixture.client.send(PING, "queued");

        assertTrue(fixture.received.isEmpty());
        assertFalse(fixture.server.isConnectedPosted());

        fixture.server.onPlayReady();
        fixture.client.onPlayReady();

        assertEquals(List.of("queued"), fixture.received);
        assertEquals(List.of("queued-pong"), fixture.pongs);
        assertTrue(fixture.server.isConnectedPosted());
    }

    @Test
    void unknownChannelRejectsUnderTheRejectPolicy()
    {
        Fixture fixture = new Fixture(UnknownChannelPolicy.REJECT, null);
        fixture.connectAndPlay();

        fixture.server.receive(BridgeEnvelope.payload(NetworkProtocol.VERSION,
                NetworkPhase.PLAY, "test:missing/v1", new byte[0]));

        assertFalse(fixture.server.isOpen());
        assertTrue(fixture.serverLink.disconnects.stream()
                        .anyMatch(reason -> reason.contains("unknown channel: test:missing/v1")),
                fixture.serverLink.disconnects.toString());
    }

    @Test
    void unknownChannelIsDroppedUnderTheIgnorePolicy()
    {
        Fixture fixture = new Fixture(UnknownChannelPolicy.IGNORE, null);
        fixture.connectAndPlay();

        fixture.server.receive(BridgeEnvelope.payload(NetworkProtocol.VERSION,
                NetworkPhase.PLAY, "test:missing/v1", new byte[0]));

        assertTrue(fixture.server.isOpen());
        assertTrue(fixture.serverLink.disconnects.isEmpty());
    }

    @Test
    void wrongPhasePayloadIsRejected()
    {
        Fixture fixture = new Fixture(UnknownChannelPolicy.REJECT, null);
        fixture.connectAndPlay();

        fixture.server.receive(BridgeEnvelope.payload(NetworkProtocol.VERSION,
                NetworkPhase.CONFIGURATION, PING.channel(), new byte[0]));

        assertFalse(fixture.server.isOpen());
        assertTrue(fixture.serverLink.disconnects.stream()
                        .anyMatch(reason -> reason.contains("payload phase mismatch")),
                fixture.serverLink.disconnects.toString());
    }

    @Test
    void handshakePolicyVetoRejectsTheClient()
    {
        Fixture fixture = new Fixture(UnknownChannelPolicy.REJECT,
                (request, localMods) -> HandshakeDecision.reject("not allowed"));
        fixture.connect();

        assertFalse(fixture.server.isVerified());
        assertFalse(fixture.client.isVerified());
        assertTrue(fixture.clientLink.disconnects.stream()
                        .anyMatch(reason -> reason.contains("handshake rejected: not allowed")),
                fixture.clientLink.disconnects.toString());
        assertEquals(0, fixture.connectedEvents.get());
    }

    @Test
    void sendBeforeVerificationFailsWithoutSending()
    {
        Fixture fixture = new Fixture(UnknownChannelPolicy.REJECT, null);
        fixture.client.onPhase(NetworkPhase.CONFIGURATION);

        assertThrows(NetworkException.class, () -> fixture.client.send(PING, "early"));
        assertTrue(fixture.received.isEmpty());
    }

    @Test
    void unsupportedPeerIsDiscardedWithoutADisconnect()
    {
        Fixture fixture = new Fixture(UnknownChannelPolicy.REJECT, null);
        fixture.client.onPhase(NetworkPhase.CONFIGURATION);
        fixture.server.onPhase(NetworkPhase.CONFIGURATION);

        fixture.server.onPlayReady();

        assertFalse(fixture.server.isOpen());
        assertTrue(fixture.serverLink.disconnects.isEmpty());
        assertEquals(0, fixture.connectedEvents.get());
        assertEquals(0, fixture.disconnectedEvents.get());
    }

    @Test
    void helloOnAServerIsAProtocolFailure()
    {
        Fixture fixture = new Fixture(UnknownChannelPolicy.REJECT, null);
        byte[] handshake = net.weaver.api.network.HandshakeResponse.CODEC.encode(
                net.weaver.api.network.HandshakeResponse.accept(NetworkProtocol.VERSION,
                        "2.1.0", "26.3", List.of(), List.of()));

        fixture.server.receive(BridgeEnvelope.handshake(BridgeEnvelope.Kind.HELLO,
                NetworkProtocol.VERSION, NetworkPhase.CONFIGURATION, handshake));

        assertFalse(fixture.server.isOpen());
        assertTrue(fixture.serverLink.disconnects.stream()
                        .anyMatch(reason -> reason.contains("unexpected HELLO")),
                fixture.serverLink.disconnects.toString());
    }

    private static final class Fixture
    {
        private final NetworkRegistry serverRegistry = new NetworkRegistry();
        private final NetworkRegistry clientRegistry = new NetworkRegistry();
        private final Link serverLink = new Link();
        private final Link clientLink = new Link();
        private final EventBus eventBus = new EventBus("bridge-session-test");
        private final AtomicInteger handshakeEvents = new AtomicInteger();
        private final AtomicInteger connectedEvents = new AtomicInteger();
        private final AtomicInteger disconnectedEvents = new AtomicInteger();
        private final AtomicInteger payloadEvents = new AtomicInteger();
        private final List<String> received = new CopyOnWriteArrayList<>();
        private final List<String> pongs = new CopyOnWriteArrayList<>();
        private final BridgeSession server;
        private final BridgeSession client;

        private Fixture(UnknownChannelPolicy policy,
                        net.weaver.api.network.HandshakePolicy handshakePolicy)
        {
            this(policy, handshakePolicy, CLIENT_PLAYER);
        }

        private Fixture(UnknownChannelPolicy policy,
                        net.weaver.api.network.HandshakePolicy handshakePolicy,
                        NetworkPlayer authenticated)
        {
            serverRegistry.registerServerbound("test", PING, (payload, context) ->
            {
                received.add(payload);
                context.reply(PONG, payload + "-pong");
            });
            serverRegistry.registerClientbound("test", PONG, (payload, context) -> { });
            clientRegistry.registerServerbound("test", PING, (payload, context) -> { });
            clientRegistry.registerClientbound("test", PONG, (payload, context) ->
                    pongs.add(payload));

            eventBus.addListener(NetworkHandshakeEvent.class,
                    event -> handshakeEvents.incrementAndGet());
            eventBus.addListener(NetworkConnectedEvent.class,
                    event -> connectedEvents.incrementAndGet());
            eventBus.addListener(NetworkDisconnectedEvent.class,
                    event -> disconnectedEvents.incrementAndGet());
            eventBus.addListener(NetworkPayloadEvent.class,
                    event -> payloadEvents.incrementAndGet());

            BridgeSession.Builder serverBuilder =
                    BridgeSession.builder(NetworkSide.SERVER, serverLink)
                            .registry(serverRegistry)
                            .eventBus(eventBus)
                            .logger(line -> { })
                            .unknownChannelPolicy(policy)
                            .handshakePolicy(handshakePolicy);
            if (authenticated != null)
            {
                serverBuilder.authenticatedPlayer(authenticated);
            }
            server = serverBuilder.build();
            client = BridgeSession.builder(NetworkSide.CLIENT, clientLink)
                    .registry(clientRegistry)
                    .eventBus(eventBus)
                    .logger(line -> { })
                    .localPlayer(CLIENT_PLAYER)
                    .unknownChannelPolicy(policy)
                    .build();
            serverLink.target(server, client);
            clientLink.target(client, server);
        }

        private void connect()
        {
            client.onPhase(NetworkPhase.CONFIGURATION);
            server.onPhase(NetworkPhase.CONFIGURATION);
            client.onClientOutboundReady();
        }

        private void connectAndPlay()
        {
            connect();
            server.onPhase(NetworkPhase.PLAY);
            client.onPhase(NetworkPhase.PLAY);
            server.onPlayReady();
            client.onPlayReady();
        }
    }

    private static final class Link implements BridgeSession.Endpoint
    {
        private final List<String> disconnects = new CopyOnWriteArrayList<>();
        private volatile BridgeSession self;
        private volatile BridgeSession peer;

        private void target(BridgeSession self, BridgeSession peer)
        {
            this.self = self;
            this.peer = peer;
        }

        @Override
        public void send(BridgeEnvelope envelope)
        {
            BridgeSession target = peer;
            if (target != null)
            {
                target.receive(BridgeEnvelope.decode(envelope.encode()));
            }
        }

        @Override
        public void disconnect(String reason)
        {
            disconnects.add(reason);
        }
    }
}
