package net.weaver.core.network;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.EventPriority;
import net.weaver.api.event.events.network.NetworkHandshakeEvent;
import net.weaver.api.event.events.network.NetworkPayloadEvent;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.UnknownChannelPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkEventTest
{
    @Test
    void handshakeEventRejectVetoesTheHandshake() throws Exception
    {
        ExecutorService serverGame = NetworkFixtures.executor("event-veto-server-game");
        ExecutorService clientGame = NetworkFixtures.executor("event-veto-client-game");
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
        EventBus serverBus = new EventBus("event-veto-server");
        NetworkServer server = null;
        NetworkClient client = null;
        try
        {
            serverBus.addListener(NetworkHandshakeEvent.class, event ->
            {
                assertEquals(NetworkFixtures.MODS, event.localMods());
                event.reject("fixture veto");
            });
            server = NetworkFixtures.startServer(serverRegistry, serverGame, serverBus,
                    UnknownChannelPolicy.REJECT, message ->
                    {
                    });
            client = NetworkFixtures.startClient(clientRegistry, clientGame,
                    new EventBus("event-veto-client"), server.port());

            assertFalse(client.awaitPlay(5, TimeUnit.SECONDS));
            assertEquals("handshake rejected: fixture veto", client.failureReason());
            assertTrue(client.handshakeResponse().isPresent());
            assertFalse(client.handshakeResponse().orElseThrow().accepted());
            assertEquals("fixture veto",
                    client.handshakeResponse().orElseThrow().reason());
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
    void handshakeEventFirstVetoAndBlankRejectionAreValidated()
    {
        net.weaver.api.network.NetworkPlayer player =
                new net.weaver.api.network.NetworkPlayer(
                        java.util.UUID.randomUUID(), "VetoPlayer");
        net.weaver.api.network.HandshakeRequest request =
                new net.weaver.api.network.HandshakeRequest(1, "2.1.0", "26.3", player,
                        NetworkFixtures.MODS, java.util.List.of());
        NetworkHandshakeEvent event = new NetworkHandshakeEvent(request,
                NetworkFixtures.MODS);

        event.reject("first");
        event.reject("second");

        assertEquals("first", event.rejectionReason());
        assertTrue(event.isRejected());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> event.reject("  "));
        assertEquals("first", event.rejectionReason());
    }

    @Test
    void payloadEventCancellationSuppressesRegisteredHandler() throws Exception
    {
        ExecutorService serverGame = NetworkFixtures.executor("event-cancel-server-game");
        ExecutorService clientGame = NetworkFixtures.executor("event-cancel-client-game");
        NetworkRegistry serverRegistry = new NetworkRegistry();
        NetworkRegistry clientRegistry = new NetworkRegistry();
        NetworkFixtures.HandlerProbe pong = new NetworkFixtures.HandlerProbe();
        NetworkFixtures.registerPingPong(serverRegistry,
                (payload, context) -> context.reply(NetworkFixtures.PONG, payload + "-pong"),
                (payload, context) ->
                {
                });
        NetworkFixtures.registerPingPong(clientRegistry, (payload, context) ->
        {
        }, pong);
        EventBus clientBus = new EventBus("event-cancel-client");
        CountDownLatch canceledObserved = new CountDownLatch(1);
        AtomicBoolean sawPongEvent = new AtomicBoolean();
        NetworkServer server = null;
        NetworkClient client = null;
        try
        {
            clientBus.addListener(NetworkPayloadEvent.class, event ->
            {
                if (NetworkFixtures.PONG.channel().equals(event.channel()))
                {
                    event.setCanceled(true);
                }
            }, EventPriority.HIGHEST);
            clientBus.addListener(NetworkPayloadEvent.class, event ->
            {
                if (NetworkFixtures.PONG.channel().equals(event.channel()))
                {
                    sawPongEvent.set(true);
                    canceledObserved.countDown();
                }
            }, EventPriority.LOWEST, true);

            server = NetworkFixtures.startServer(serverRegistry, serverGame,
                    new EventBus("event-cancel-server"), UnknownChannelPolicy.REJECT,
                    message ->
                    {
                    });
            client = NetworkFixtures.startClient(clientRegistry, clientGame, clientBus,
                    server.port());
            assertTrue(client.awaitPlay(5, TimeUnit.SECONDS));

            NetworkConnection connection = client.connection();
            connection.send(NetworkFixtures.PING, "cancel-me");

            assertTrue(canceledObserved.await(5, TimeUnit.SECONDS),
                    "canceled payload event was not observed");
            assertTrue(sawPongEvent.get());

            CountDownLatch drained = new CountDownLatch(1);
            clientGame.execute(drained::countDown);
            assertTrue(drained.await(5, TimeUnit.SECONDS));

            assertEquals(0, pong.calls(),
                    "registered handler ran after the payload event was canceled");
            assertEquals(1L, pong.latch().getCount());
            assertTrue(connection.isOpen());
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
