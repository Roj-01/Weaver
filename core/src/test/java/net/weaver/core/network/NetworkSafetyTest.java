package net.weaver.core.network;

import net.weaver.api.event.EventBus;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadType;
import net.weaver.api.network.UnknownChannelPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkSafetyTest
{
    private static final String MOD_ID = NetworkFixtures.MOD_ID;
    private static final PayloadCodec<String> STRINGS = NetworkFixtures.STRING_CODEC;
    private static final net.weaver.api.network.PayloadHandler<String> NO_OP =
            (payload, context) ->
            {
            };
    private static final net.weaver.api.network.PayloadHandler<String> PING_REPLY =
            (payload, context) -> context.reply(NetworkFixtures.PONG, payload + "-pong");

    @Test
    void unknownChannelWithRejectPolicyDisconnectsWithPreciseReason() throws Exception
    {
        PayloadType<String> ghost = PayloadType.of(MOD_ID, "ghost", STRINGS);
        try (Harness harness = new Harness(UnknownChannelPolicy.REJECT,
                server -> NetworkFixtures.registerPingPong(server, PING_REPLY, NO_OP),
                client ->
                {
                    NetworkFixtures.registerPingPong(client, NO_OP, NO_OP);
                    client.registerServerbound(MOD_ID, ghost, NO_OP);
                }))
        {
            assertTrue(harness.awaitPlay());

            harness.client().connection().send(ghost, "spooky");

            assertTrue(NetworkFixtures.waitUntil(
                    () -> !harness.client().connection().isOpen(), 5_000L),
                    "connection stayed open after an unknown channel with REJECT");
            assertEquals("unknown channel: " + ghost.channel(),
                    harness.client().failureReason());
        }
    }

    @Test
    void unknownChannelWithIgnorePolicyDropsPayloadAndKeepsConnectionUsable()
            throws Exception
    {
        PayloadType<String> ghost = PayloadType.of(MOD_ID, "ghost", STRINGS);
        NetworkFixtures.HandlerProbe pong = new NetworkFixtures.HandlerProbe();
        try (Harness harness = new Harness(UnknownChannelPolicy.IGNORE,
                server -> NetworkFixtures.registerPingPong(server, PING_REPLY, NO_OP),
                client ->
                {
                    NetworkFixtures.registerPingPong(client, NO_OP, pong);
                    client.registerServerbound(MOD_ID, ghost, NO_OP);
                }))
        {
            assertTrue(harness.awaitPlay());

            NetworkConnection connection = harness.client().connection();
            connection.send(ghost, "spooky");
            connection.send(NetworkFixtures.PING, "still-alive");

            assertTrue(NetworkFixtures.await(pong.latch()),
                    "connection was not usable after an ignored unknown channel");
            assertEquals("still-alive-pong", pong.value());
            assertTrue(connection.isOpen());
            assertTrue(harness.serverLog().stream().anyMatch(line ->
                            line.contains("dropped payload unknown-channel=" + ghost.channel())
                                    && line.contains("phase=PLAY")),
                    "server did not log the dropped payload: " + harness.serverLog());
        }
    }

    @Test
    void wrongPhaseWrongDirectionAndUnregisteredSendsThrowPreciseExceptions()
            throws Exception
    {
        PayloadType<String> phaseOnly = PayloadType.of(MOD_ID, "phase-only", STRINGS);
        PayloadType<String> directionOnly =
                PayloadType.of(MOD_ID, "direction-only", STRINGS);
        PayloadType<String> nowhere = PayloadType.of(MOD_ID, "nowhere", STRINGS);
        NetworkFixtures.HandlerProbe pong = new NetworkFixtures.HandlerProbe();
        try (Harness harness = new Harness(UnknownChannelPolicy.REJECT,
                server -> NetworkFixtures.registerPingPong(server, PING_REPLY, NO_OP),
                client ->
                {
                    NetworkFixtures.registerPingPong(client, NO_OP, pong);
                    client.registerServerbound(MOD_ID, phaseOnly,
                            net.weaver.api.network.NetworkPhase.CONFIGURATION, NO_OP);
                    client.registerClientbound(MOD_ID, directionOnly, NO_OP);
                }))
        {
            assertTrue(harness.awaitPlay());
            NetworkConnection connection = harness.client().connection();

            NetworkException phaseFailure = assertThrows(NetworkException.class,
                    () -> connection.send(phaseOnly, "x"));
            assertEquals("channel " + phaseOnly.channel() + " is not registered for phase "
                    + "PLAY; it is registered for phase CONFIGURATION",
                    phaseFailure.getMessage());

            NetworkException directionFailure = assertThrows(NetworkException.class,
                    () -> connection.send(directionOnly, "x"));
            assertEquals("channel " + directionOnly.channel()
                    + " is not registered for direction SERVERBOUND; "
                    + "it is registered for direction CLIENTBOUND",
                    directionFailure.getMessage());

            NetworkException missingFailure = assertThrows(NetworkException.class,
                    () -> connection.send(nowhere, "x"));
            assertEquals("channel " + nowhere.channel()
                    + " is not registered for direction SERVERBOUND in phase PLAY",
                    missingFailure.getMessage());

            assertTrue(connection.isOpen());
            connection.send(NetworkFixtures.PING, "after-errors");
            assertTrue(NetworkFixtures.await(pong.latch()));
            assertEquals("after-errors-pong", pong.value());
        }
    }

    @Test
    void malformedPayloadDecodeDisconnectsWithPreciseReason() throws Exception
    {
        PayloadCodec<String> brokenDecode = new PayloadCodec<>()
        {
            @Override
            public void encode(String value, PayloadBuffer out)
            {
                out.writeString(value);
            }

            @Override
            public String decode(PayloadBuffer in)
            {
                in.readString();
                throw new NetworkException("synthetic decode failure");
            }
        };
        PayloadType<String> bad = PayloadType.of(MOD_ID, "bad", brokenDecode);
        try (Harness harness = new Harness(UnknownChannelPolicy.REJECT,
                server ->
                {
                    NetworkFixtures.registerPingPong(server, NO_OP, NO_OP);
                    server.registerServerbound(MOD_ID, bad, NO_OP);
                },
                client ->
                {
                    NetworkFixtures.registerPingPong(client, NO_OP, NO_OP);
                    client.registerServerbound(MOD_ID, bad, NO_OP);
                }))
        {
            assertTrue(harness.awaitPlay());

            harness.client().connection().send(bad, "boom");

            assertTrue(NetworkFixtures.waitUntil(
                    () -> !harness.client().connection().isOpen(), 5_000L),
                    "connection stayed open after a malformed payload");
            assertEquals("malformed payload: " + bad.channel(),
                    harness.client().failureReason());
        }
    }

    @Test
    void oversizeSendAndOversizeFrameAreRejected() throws Exception
    {
        PayloadCodec<String> oversize = new PayloadCodec<>()
        {
            @Override
            public void encode(String value, PayloadBuffer out)
            {
                out.writeString(value);
            }

            @Override
            public byte[] encode(String value)
            {
                return new byte[NetworkProtocol.MAX_PAYLOAD_BYTES + 1];
            }

            @Override
            public String decode(PayloadBuffer in)
            {
                return in.readString();
            }
        };
        PayloadType<String> huge = PayloadType.of(MOD_ID, "huge", oversize);
        NetworkFixtures.HandlerProbe pong = new NetworkFixtures.HandlerProbe();
        try (Harness harness = new Harness(UnknownChannelPolicy.REJECT,
                server -> NetworkFixtures.registerPingPong(server, PING_REPLY, NO_OP),
                client ->
                {
                    NetworkFixtures.registerPingPong(client, NO_OP, pong);
                    client.registerServerbound(MOD_ID, huge, NO_OP);
                }))
        {
            assertTrue(harness.awaitPlay());
            NetworkConnection connection = harness.client().connection();

            NetworkException sendFailure = assertThrows(NetworkException.class,
                    () -> connection.send(huge, "x"));
            assertTrue(sendFailure.getMessage().contains("exceeding the maximum of "
                    + NetworkProtocol.MAX_PAYLOAD_BYTES), sendFailure.getMessage());
            assertTrue(connection.isOpen());

            NetworkException frameFailure = assertThrows(NetworkException.class,
                    () -> NetworkWire.encode(NetworkWire.FrameType.PAYLOAD,
                            new byte[NetworkProtocol.MAX_FRAME_BYTES + 1]));
            assertTrue(frameFailure.getMessage().contains("exceeds the maximum of "
                    + NetworkProtocol.MAX_FRAME_BYTES), frameFailure.getMessage());

            connection.send(NetworkFixtures.PING, "still-here");
            assertTrue(NetworkFixtures.await(pong.latch()));
            assertEquals("still-here-pong", pong.value());
        }
    }

    @Test
    void clientProtocolMismatchIsRejectedWithExactReason() throws Exception
    {
        try (Harness harness = new Harness(UnknownChannelPolicy.REJECT,
                server -> NetworkFixtures.registerPingPong(server, PING_REPLY, NO_OP),
                client -> NetworkFixtures.registerPingPong(client, NO_OP, NO_OP),
                2))
        {
            assertFalse(harness.client().awaitPlay(NetworkFixtures.DEFAULT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS), "mismatched client unexpectedly reached PLAY");

            assertEquals("handshake rejected: protocol mismatch: client=2, server=1",
                    harness.client().failureReason());
            assertTrue(harness.client().handshakeResponse().isPresent());
            assertFalse(harness.client().handshakeResponse().orElseThrow().accepted());
            assertEquals("protocol mismatch: client=2, server=1",
                    harness.client().handshakeResponse().orElseThrow().reason());
        }
    }

    private static final class Harness implements AutoCloseable
    {
        private final ExecutorService serverGame;
        private final ExecutorService clientGame;
        private final NetworkServer server;
        private final NetworkClient client;
        private final List<String> serverLog = new CopyOnWriteArrayList<>();

        private Harness(UnknownChannelPolicy policy,
                        Consumer<NetworkRegistry> serverSetup,
                        Consumer<NetworkRegistry> clientSetup)
        {
            this(policy, serverSetup, clientSetup, NetworkProtocol.VERSION);
        }

        private Harness(UnknownChannelPolicy policy,
                        Consumer<NetworkRegistry> serverSetup,
                        Consumer<NetworkRegistry> clientSetup,
                        int clientProtocolVersion)
        {
            serverGame = NetworkFixtures.executor("safety-server-game");
            clientGame = NetworkFixtures.executor("safety-client-game");
            NetworkRegistry serverRegistry = new NetworkRegistry();
            NetworkRegistry clientRegistry = new NetworkRegistry();
            serverSetup.accept(serverRegistry);
            clientSetup.accept(clientRegistry);
            server = NetworkFixtures.startServer(serverRegistry, serverGame,
                    new EventBus("safety-server"), policy, serverLog::add);
            client = NetworkFixtures.startClient(clientRegistry, clientGame,
                    new EventBus("safety-client"), server.port(), clientProtocolVersion,
                    message ->
                    {
                    });
        }

        private NetworkClient client()
        {
            return client;
        }

        private List<String> serverLog()
        {
            return serverLog;
        }

        private boolean awaitPlay() throws InterruptedException
        {
            return client.awaitPlay(NetworkFixtures.DEFAULT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);
        }

        @Override
        public void close()
        {
            client.close();
            server.close();
            NetworkFixtures.shutdown(serverGame);
            NetworkFixtures.shutdown(clientGame);
        }
    }
}
