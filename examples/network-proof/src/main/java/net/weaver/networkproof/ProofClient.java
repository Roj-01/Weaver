package net.weaver.networkproof;

import net.weaver.api.event.EventBus;
import net.weaver.api.network.HandshakeResponse;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.core.network.NetworkClient;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ProofClient
{
    private static final String THREAD_NAME = "network-proof-client-game";

    private ProofClient()
    {
        throw new AssertionError("No ProofClient instances");
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length != 1)
        {
            System.out.println("NETWORK PROOF RESULT ok=false reason=usage:ProofClient <port>");
            System.exit(1);
            return;
        }
        int port;
        try
        {
            port = Integer.parseInt(args[0]);
        }
        catch (NumberFormatException failure)
        {
            System.out.println("NETWORK PROOF RESULT ok=false reason=invalid-port");
            System.exit(1);
            return;
        }

        ExecutorService game = Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        NetworkRegistry registry = new NetworkRegistry();
        CountDownLatch pongLatch = new CountDownLatch(1);
        AtomicReference<String> pongValue = new AtomicReference<>();
        AtomicReference<String> pongThread = new AtomicReference<>();
        registry.registerClientbound(ProofProtocol.MOD_ID, ProofProtocol.PONG,
                (payload, context) ->
                {
                    pongValue.set(payload);
                    pongThread.set(Thread.currentThread().getName());
                    pongLatch.countDown();
                });
        registry.registerServerbound(ProofProtocol.MOD_ID, ProofProtocol.PING,
                (payload, context) ->
                {
                });

        NetworkClient client = null;
        boolean ok = false;
        try
        {
            client = NetworkClient.builder(registry)
                    .host("127.0.0.1")
                    .port(port)
                    .gameExecutor(game)
                    .eventBus(new EventBus("network-proof-client"))
                    .player(new NetworkPlayer(UUID.randomUUID(), "NetworkProofClient"))
                    .mods(ProofProtocol.mods())
                    .connect();

            boolean play = client.awaitPlay(5, TimeUnit.SECONDS);
            NetworkConnection connection = client.connection();
            HandshakeResponse response = client.handshakeResponse().orElse(null);

            boolean phaseCheck = play && connection.phase() == NetworkPhase.PLAY
                    && connection.isOpen();
            boolean sideCheck = connection.side() == NetworkSide.CLIENT;
            boolean playerCheck = "NetworkProofClient".equals(connection.player().name());
            boolean handshakeCheck = response != null && response.accepted();
            boolean modsCheck = response != null
                    && response.mods().contains(
                    net.weaver.api.network.NetworkModInfo.of(ProofProtocol.MOD_ID,
                            ProofProtocol.MOD_VERSION));

            boolean pongCheck = false;
            if (play)
            {
                connection.send(ProofProtocol.PING, "hello");
                pongCheck = pongLatch.await(5, TimeUnit.SECONDS)
                        && "hello-pong".equals(pongValue.get())
                        && THREAD_NAME.equals(pongThread.get());
            }

            check("phase", phaseCheck);
            check("side", sideCheck);
            check("player", playerCheck);
            check("handshake", handshakeCheck);
            check("server-mods", modsCheck);
            check("pong", pongCheck);

            ok = phaseCheck && sideCheck && playerCheck && handshakeCheck && modsCheck
                    && pongCheck;
            System.out.println("NETWORK PROOF RESULT ok=" + ok + " checks=6");
            System.out.flush();
        }
        finally
        {
            if (client != null)
            {
                client.close();
            }
            game.shutdownNow();
            game.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (!ok)
        {
            System.exit(1);
        }
    }

    private static void check(String name, boolean passed)
    {
        System.out.println("NETWORK PROOF CHECK ok=" + passed + " " + name);
        System.out.flush();
    }
}
