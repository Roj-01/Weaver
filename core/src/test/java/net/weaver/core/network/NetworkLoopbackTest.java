package net.weaver.core.network;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.events.network.NetworkConnectedEvent;
import net.weaver.api.event.events.network.NetworkDisconnectedEvent;
import net.weaver.api.event.events.network.NetworkPayloadEvent;
import net.weaver.api.network.HandshakeResponse;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadDirection;
import net.weaver.api.network.UnknownChannelPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkLoopbackTest
{
    @Test
    void realLoopbackHandshakePingPongAndEvents() throws Exception
    {
        ExecutorService serverGame = NetworkFixtures.executor("test-server-game");
        ExecutorService clientGame = NetworkFixtures.executor("test-client-game");
        NetworkRegistry serverRegistry = new NetworkRegistry();
        NetworkRegistry clientRegistry = new NetworkRegistry();
        NetworkFixtures.HandlerProbe serverPing = new NetworkFixtures.HandlerProbe();
        NetworkFixtures.HandlerProbe clientPong = new NetworkFixtures.HandlerProbe();
        NetworkFixtures.HandlerProbe serverPong = new NetworkFixtures.HandlerProbe();
        NetworkFixtures.HandlerProbe clientPing = new NetworkFixtures.HandlerProbe();

        serverRegistry.registerServerbound(NetworkFixtures.MOD_ID, NetworkFixtures.PING,
                (payload, context) ->
                {
                    serverPing.handle(payload, context);
                    context.reply(NetworkFixtures.PONG, payload + "-pong");
                });
        serverRegistry.registerClientbound(NetworkFixtures.MOD_ID, NetworkFixtures.PONG,
                serverPong);
        clientRegistry.registerClientbound(NetworkFixtures.MOD_ID, NetworkFixtures.PONG,
                clientPong);
        clientRegistry.registerServerbound(NetworkFixtures.MOD_ID, NetworkFixtures.PING,
                clientPing);

        EventBus serverBus = new EventBus("loopback-server");
        EventBus clientBus = new EventBus("loopback-client");
        CountDownLatch serverConnectedLatch = new CountDownLatch(1);
        CountDownLatch clientConnectedLatch = new CountDownLatch(1);
        CountDownLatch clientDisconnectedLatch = new CountDownLatch(1);
        CountDownLatch serverDisconnectedLatch = new CountDownLatch(1);
        CountDownLatch payloadEventLatch = new CountDownLatch(1);
        AtomicReference<NetworkConnectedEvent> serverConnected = new AtomicReference<>();
        AtomicReference<NetworkConnectedEvent> clientConnected = new AtomicReference<>();
        AtomicReference<NetworkDisconnectedEvent> clientDisconnected = new AtomicReference<>();
        AtomicReference<NetworkDisconnectedEvent> serverDisconnected = new AtomicReference<>();
        AtomicReference<NetworkPayloadEvent> payloadEvent = new AtomicReference<>();

        serverBus.addListener(NetworkConnectedEvent.class, event ->
        {
            serverConnected.set(event);
            serverConnectedLatch.countDown();
        });
        clientBus.addListener(NetworkConnectedEvent.class, event ->
        {
            clientConnected.set(event);
            clientConnectedLatch.countDown();
        });
        clientBus.addListener(NetworkDisconnectedEvent.class, event ->
        {
            clientDisconnected.set(event);
            clientDisconnectedLatch.countDown();
        });
        serverBus.addListener(NetworkDisconnectedEvent.class, event ->
        {
            serverDisconnected.set(event);
            serverDisconnectedLatch.countDown();
        });
        clientBus.addListener(NetworkPayloadEvent.class, event ->
        {
            payloadEvent.set(event);
            payloadEventLatch.countDown();
        });

        NetworkServer server = null;
        NetworkClient client = null;
        try
        {
            server = NetworkFixtures.startServer(serverRegistry, serverGame, serverBus,
                    UnknownChannelPolicy.REJECT, message ->
                    {
                    });
            client = NetworkFixtures.startClient(clientRegistry, clientGame, clientBus,
                    server.port());

            assertTrue(client.awaitPlay(NetworkFixtures.DEFAULT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS), "client did not reach PLAY");
            assertTrue(NetworkFixtures.await(serverConnectedLatch),
                    "server connected event not posted");
            assertTrue(NetworkFixtures.await(clientConnectedLatch),
                    "client connected event not posted");

            NetworkConnection clientConnection = client.connection();
            assertEquals(NetworkPhase.PLAY, clientConnection.phase());
            assertEquals(NetworkSide.CLIENT, clientConnection.side());
            assertEquals(NetworkFixtures.CLIENT_PLAYER, clientConnection.player());
            assertTrue(clientConnection.isOpen());

            HandshakeResponse handshake = client.handshakeResponse().orElseThrow();
            assertTrue(handshake.accepted());
            assertEquals(NetworkFixtures.MODS, handshake.mods());
            assertEquals(NetworkFixtures.PING.channel() + "," + NetworkFixtures.PONG.channel(),
                    String.join(",", handshake.channels()));

            NetworkConnection serverConnection = NetworkFixtures.awaitServerPlay(server, 5_000L);
            assertNotNull(serverConnection, "server connection did not reach PLAY");
            assertEquals(NetworkPhase.PLAY, serverConnection.phase());
            assertEquals(NetworkSide.SERVER, serverConnection.side());
            assertEquals(NetworkFixtures.CLIENT_PLAYER, serverConnection.player());
            assertTrue(serverConnection.isOpen());

            assertEquals(NetworkFixtures.MODS, clientConnected.get().remoteMods());
            assertEquals(NetworkFixtures.MODS, serverConnected.get().remoteMods());
            assertEquals(NetworkSide.CLIENT, clientConnected.get().side());
            assertEquals(NetworkSide.SERVER, serverConnected.get().side());
            assertEquals(NetworkFixtures.CLIENT_PLAYER, serverConnected.get().player());
            assertEquals(LogicalSide.SERVER, serverConnected.get().logicalSide());
            assertEquals(LogicalSide.CLIENT, clientConnected.get().logicalSide());

            clientConnection.send(NetworkFixtures.PING, "hello");

            assertTrue(NetworkFixtures.await(serverPing.latch()),
                    "server PING handler did not run");
            assertEquals("hello", serverPing.value());
            assertEquals("test-server-game", serverPing.threadName());
            assertFalse(serverPing.threadName().startsWith("weaver-net-"));
            assertEquals(NetworkSide.SERVER, serverPing.side());
            assertEquals(NetworkPhase.PLAY, serverPing.phase());
            assertEquals(NetworkFixtures.CLIENT_PLAYER, serverPing.player());
            assertSame(serverConnection, serverPing.connection());

            assertTrue(NetworkFixtures.await(clientPong.latch()),
                    "client PONG handler did not run");
            assertEquals("hello-pong", clientPong.value());
            assertEquals("test-client-game", clientPong.threadName());
            assertFalse(clientPong.threadName().startsWith("weaver-net-"));
            assertEquals(NetworkSide.CLIENT, clientPong.side());
            assertEquals(NetworkPhase.PLAY, clientPong.phase());
            assertEquals(NetworkFixtures.CLIENT_PLAYER, clientPong.player());
            assertSame(clientConnection, clientPong.connection());

            assertTrue(NetworkFixtures.await(payloadEventLatch),
                    "payload event was not posted");
            assertEquals(NetworkFixtures.PONG.channel(), payloadEvent.get().channel());
            assertEquals("hello-pong", payloadEvent.get().payload());
            assertEquals(NetworkSide.CLIENT, payloadEvent.get().side());
            assertEquals(NetworkPhase.PLAY, payloadEvent.get().phase());
            assertEquals(PayloadDirection.CLIENTBOUND, payloadEvent.get().direction());
            assertEquals(NetworkFixtures.CLIENT_PLAYER, payloadEvent.get().player());
            assertSame(clientConnection, payloadEvent.get().connection());

            client.close();
            client = null;

            assertTrue(NetworkFixtures.await(clientDisconnectedLatch),
                    "client disconnected event not posted");
            assertEquals(NetworkSide.CLIENT, clientDisconnected.get().side());
            assertEquals(NetworkFixtures.CLIENT_PLAYER, clientDisconnected.get().player());
            assertTrue(clientDisconnected.get().graceful());
            assertEquals("connection closed", clientDisconnected.get().reason());

            assertTrue(NetworkFixtures.await(serverDisconnectedLatch),
                    "server disconnected event not posted");
            assertEquals(NetworkSide.SERVER, serverDisconnected.get().side());
            assertEquals(NetworkFixtures.CLIENT_PLAYER, serverDisconnected.get().player());
            assertFalse(serverDisconnected.get().reason().isEmpty());
        }
        finally
        {
            if (client != null)
            {
                client.close();
            }
            if (server != null)
            {
                server.close();
            }
            NetworkFixtures.shutdown(serverGame);
            NetworkFixtures.shutdown(clientGame);
        }
    }

    @Test
    void serverConnectionListTracksLiveSessions() throws Exception
    {
        ExecutorService serverGame = NetworkFixtures.executor("track-server-game");
        ExecutorService clientGame = NetworkFixtures.executor("track-client-game");
        NetworkRegistry serverRegistry = new NetworkRegistry();
        NetworkRegistry clientRegistry = new NetworkRegistry();
        NetworkFixtures.registerPingPong(serverRegistry, (payload, context) ->
        {
        }, (payload, context) ->
        {
        });
        NetworkFixtures.registerPingPong(clientRegistry, (payload, context) ->
        {
        }, (payload, context) ->
        {
        });

        NetworkServer server = null;
        NetworkClient client = null;
        try
        {
            server = NetworkFixtures.startServer(serverRegistry, serverGame,
                    new EventBus("track-server"), UnknownChannelPolicy.REJECT, message ->
                    {
                    });
            client = NetworkFixtures.startClient(clientRegistry, clientGame,
                    new EventBus("track-client"), server.port());

            assertTrue(client.awaitPlay(5, TimeUnit.SECONDS));
            assertNotNull(NetworkFixtures.awaitServerPlay(server, 5_000L));
            assertEquals(1, server.connections().size());

            client.close();
            client = null;

            NetworkServer liveServer = server;
            assertTrue(NetworkFixtures.waitUntil(() -> liveServer.connections().isEmpty(), 5_000L),
                    "server did not remove the closed session");
        }
        finally
        {
            if (client != null)
            {
                client.close();
            }
            if (server != null)
            {
                server.close();
            }
            NetworkFixtures.shutdown(serverGame);
            NetworkFixtures.shutdown(clientGame);
        }
    }
}
