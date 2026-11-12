package net.weaver.core.network;

import net.weaver.api.WeaverApi;
import net.weaver.api.event.EventBus;
import net.weaver.api.network.HandshakeRequest;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadType;
import net.weaver.api.network.UnknownChannelPolicy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for transport resource bounds: the handshake deadline,
 * pre-verification payloads, the inbound game-executor backlog, and forced
 * closes of draining sessions whose peer stopped reading.
 */
class NetworkHardeningTest
{
    private static final net.weaver.api.network.PayloadHandler<String> NO_OP =
            (payload, context) ->
            {
            };

    @Test
    void idleSocketIsDisconnectedAtTheHandshakeDeadlineAndFreesItsSlot() throws Exception
    {
        ExecutorService serverGame = NetworkFixtures.executor("hardening-server-game");
        ExecutorService clientGame = NetworkFixtures.executor("hardening-client-game");
        NetworkRegistry serverRegistry = new NetworkRegistry();
        NetworkRegistry clientRegistry = new NetworkRegistry();
        NetworkFixtures.registerPingPong(serverRegistry, NO_OP, NO_OP);
        NetworkFixtures.registerPingPong(clientRegistry, NO_OP, NO_OP);
        NetworkServer server = NetworkServer.builder(serverRegistry)
                .port(0)
                .gameExecutor(serverGame)
                .eventBus(new EventBus("hardening-server"))
                .logger(line ->
                {
                })
                .mods(NetworkFixtures.MODS)
                .maxConnections(1)
                .handshakeTimeoutMillis(300)
                .start();
        NetworkClient client = null;
        try (Socket idle = connectRaw(server.port()))
        {
            // the idle socket never sends HELLO
            NetworkWire.WireFrame frame = readFrame(idle);
            assertEquals(NetworkWire.FrameType.DISCONNECT, frame.type());
            assertEquals("handshake timed out after 300 ms", decodeReason(frame.body()));
            assertTrue(NetworkFixtures.waitUntil(() -> server.connections().isEmpty(),
                    5_000L), "timed-out session still holds a slot");

            client = NetworkFixtures.startClient(clientRegistry, clientGame,
                    new EventBus("hardening-client"), server.port());
            assertTrue(client.awaitPlay(NetworkFixtures.DEFAULT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS), "a real client was locked out: " + client.failureReason());
            Thread.sleep(500L);
            assertTrue(client.connection().isOpen(),
                    "the handshake deadline closed a session that reached PLAY");
        }
        finally
        {
            if (client != null)
            {
                client.close();
            }
            server.close();
            NetworkFixtures.shutdown(serverGame);
            NetworkFixtures.shutdown(clientGame);
        }
    }

    @Test
    void payloadBeforeHandshakeVerificationIsDroppedAndDisconnects() throws Exception
    {
        PayloadType<String> early = PayloadType.of(NetworkFixtures.MOD_ID, "early",
                NetworkFixtures.STRING_CODEC);
        NetworkFixtures.HandlerProbe probe = new NetworkFixtures.HandlerProbe();
        ExecutorService serverGame = NetworkFixtures.executor("hardening-server-game");
        NetworkRegistry serverRegistry = new NetworkRegistry();
        serverRegistry.registerServerbound(NetworkFixtures.MOD_ID, early, NetworkPhase.LOGIN,
                probe);
        NetworkServer server = NetworkFixtures.startServer(serverRegistry, serverGame,
                new EventBus("hardening-server"), UnknownChannelPolicy.REJECT, line ->
                {
                });
        try (Socket raw = connectRaw(server.port()))
        {
            OutputStream out = raw.getOutputStream();
            out.write(payloadFrame(early.channel(), NetworkPhase.LOGIN,
                    NetworkFixtures.STRING_CODEC.encode("spoofed")));
            out.flush();

            NetworkWire.WireFrame frame = readFrame(raw);
            assertEquals(NetworkWire.FrameType.DISCONNECT, frame.type());
            assertEquals("unexpected PAYLOAD frame before handshake verification",
                    decodeReason(frame.body()));
            Thread.sleep(200L);
            assertEquals(0, probe.calls(), "a payload was dispatched before verification");
        }
        finally
        {
            server.close();
            NetworkFixtures.shutdown(serverGame);
        }
    }

    @Test
    void inboundBacklogAboveTheLimitDisconnectsWithAClearReason() throws Exception
    {
        ExecutorService serverGame = NetworkFixtures.executor("hardening-server-game");
        ExecutorService clientGame = NetworkFixtures.executor("hardening-client-game");
        NetworkRegistry serverRegistry = new NetworkRegistry();
        NetworkRegistry clientRegistry = new NetworkRegistry();
        NetworkFixtures.registerPingPong(serverRegistry, NO_OP, NO_OP);
        NetworkFixtures.registerPingPong(clientRegistry, NO_OP, NO_OP);
        NetworkServer server = NetworkFixtures.startServer(serverRegistry, serverGame,
                new EventBus("hardening-server"), UnknownChannelPolicy.REJECT, line ->
                {
                });
        NetworkClient client = NetworkFixtures.startClient(clientRegistry, clientGame,
                new EventBus("hardening-client"), server.port());
        CountDownLatch release = new CountDownLatch(1);
        try
        {
            assertTrue(client.awaitPlay(NetworkFixtures.DEFAULT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS));
            assertNotNull(NetworkFixtures.awaitServerPlay(server, 5_000L));
            // stall the server game thread so inbound payloads pile up
            serverGame.execute(() ->
            {
                try
                {
                    release.await(30, TimeUnit.SECONDS);
                }
                catch (InterruptedException failure)
                {
                    Thread.currentThread().interrupt();
                }
            });
            NetworkConnection connection = client.connection();
            int frames = NetworkSession.MAX_PENDING_INBOUND_FRAMES + 50;
            for (int index = 0; index < frames && connection.isOpen(); index++)
            {
                try
                {
                    connection.send(NetworkFixtures.PING, "flood-" + index);
                }
                catch (net.weaver.api.network.NetworkException closed)
                {
                    break;
                }
            }

            assertTrue(NetworkFixtures.waitUntil(() -> !connection.isOpen(), 5_000L),
                    "server kept accepting an unbounded inbound backlog");
            assertTrue(client.failureReason().startsWith("inbound payload backlog exceeded"),
                    client.failureReason());
        }
        finally
        {
            release.countDown();
            client.close();
            server.close();
            NetworkFixtures.shutdown(serverGame);
            NetworkFixtures.shutdown(clientGame);
        }
    }

    @Test
    void drainingCloseForceClosesTheSocketWhenThePeerStopsReading() throws Exception
    {
        PayloadCodec<byte[]> rawBytes = new PayloadCodec<>()
        {
            @Override
            public void encode(byte[] value, PayloadBuffer out)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public byte[] encode(byte[] value)
            {
                return value;
            }

            @Override
            public byte[] decode(PayloadBuffer in)
            {
                throw new UnsupportedOperationException();
            }
        };
        PayloadType<byte[]> bulk = PayloadType.of(NetworkFixtures.MOD_ID, "bulk", rawBytes);
        ExecutorService serverGame = NetworkFixtures.executor("hardening-server-game");
        NetworkRegistry serverRegistry = new NetworkRegistry();
        serverRegistry.registerClientbound(NetworkFixtures.MOD_ID, bulk,
                NetworkPhase.CONFIGURATION, (payload, context) ->
                {
                });
        NetworkServer server = NetworkServer.builder(serverRegistry)
                .port(0)
                .gameExecutor(serverGame)
                .eventBus(new EventBus("hardening-server"))
                .logger(line ->
                {
                })
                .mods(NetworkFixtures.MODS)
                .handshakeTimeoutMillis(60_000)
                .start();
        try (Socket stalled = new Socket())
        {
            stalled.setReceiveBufferSize(4096);
            stalled.connect(new InetSocketAddress("127.0.0.1", server.port()), 5_000);
            HandshakeRequest request = new HandshakeRequest(NetworkProtocol.VERSION,
                    WeaverApi.VERSION, WeaverApi.GAME_VERSION, NetworkFixtures.CLIENT_PLAYER,
                    NetworkFixtures.MODS, serverRegistry.channels());
            OutputStream out = stalled.getOutputStream();
            out.write(NetworkWire.encode(NetworkWire.FrameType.HELLO,
                    HandshakeCodec.encodeRequest(request)));
            out.flush();
            // the peer never reads again

            AtomicReference<NetworkConnection> found = new AtomicReference<>();
            assertTrue(NetworkFixtures.waitUntil(() ->
            {
                for (NetworkConnection connection : server.connections())
                {
                    if (connection.phase() == NetworkPhase.CONFIGURATION)
                    {
                        found.set(connection);
                        return true;
                    }
                }
                return false;
            }, 5_000L), "server never verified the raw handshake");
            NetworkSession session = (NetworkSession) found.get();
            byte[] chunk = new byte[512 * 1024];
            for (int index = 0; index < 64; index++)
            {
                session.send(bulk, chunk);
            }
            Thread.sleep(200L);

            session.disconnect("bye");

            assertTrue(NetworkFixtures.waitUntil(session::isSocketClosed,
                    NetworkSession.DRAIN_GRACE_MILLIS + 3_000L),
                    "draining session kept its socket open for a peer that stopped reading");
        }
        finally
        {
            server.close();
            NetworkFixtures.shutdown(serverGame);
        }
    }

    private static Socket connectRaw(int port) throws IOException
    {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
        socket.setSoTimeout(5_000);
        return socket;
    }

    private static NetworkWire.WireFrame readFrame(Socket socket) throws IOException
    {
        return NetworkWire.read(new DataInputStream(socket.getInputStream()));
    }

    private static String decodeReason(byte[] body)
    {
        return PayloadBuffer.reader(body).readString(NetworkProtocol.MAX_REASON_CHARS);
    }

    private static byte[] payloadFrame(String channel, NetworkPhase phase, byte[] payload)
            throws IOException
    {
        byte[] channelBytes = channel.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(body);
        writeVarInt(out, channelBytes.length);
        out.write(channelBytes);
        out.writeByte(phase.ordinal());
        writeVarInt(out, payload.length);
        out.write(payload);
        return NetworkWire.encode(NetworkWire.FrameType.PAYLOAD, body.toByteArray());
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException
    {
        int remaining = value;
        while ((remaining & ~0x7F) != 0)
        {
            out.writeByte((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.writeByte(remaining);
    }
}
