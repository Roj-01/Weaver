package net.weaver.networkproof;

import net.weaver.api.event.EventBus;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.UnknownChannelPolicy;
import net.weaver.core.network.NetworkServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ProofServer
{
    private static final long PING_TIMEOUT_SECONDS = 15L;
    private static final long ACCEPT_LINGER_MILLIS = 20_000L;

    private ProofServer()
    {
        throw new AssertionError("No ProofServer instances");
    }

    public static void main(String[] args) throws Exception
    {
        ExecutorService game = Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, "network-proof-server-game");
            thread.setDaemon(true);
            return thread;
        });
        NetworkRegistry registry = new NetworkRegistry();
        CountDownLatch pingLatch = new CountDownLatch(1);
        CountDownLatch twoSessionsClosed = new CountDownLatch(1);
        AtomicReference<String> pingValue = new AtomicReference<>();
        AtomicReference<String> pingThread = new AtomicReference<>();
        AtomicInteger closedSessions = new AtomicInteger();

        registry.registerServerbound(ProofProtocol.MOD_ID, ProofProtocol.PING,
                (payload, context) ->
                {
                    pingValue.set(payload);
                    pingThread.set(Thread.currentThread().getName());
                    context.reply(ProofProtocol.PONG, payload + "-pong");
                    pingLatch.countDown();
                });
        registry.registerClientbound(ProofProtocol.MOD_ID, ProofProtocol.PONG,
                (payload, context) ->
                {
                });

        NetworkServer server = null;
        boolean ok = false;
        try
        {
            server = NetworkServer.builder(registry)
                    .bindAddress("127.0.0.1")
                    .port(0)
                    .gameExecutor(game)
                    .eventBus(new EventBus("network-proof-server"))
                    .mods(ProofProtocol.mods())
                    .unknownChannelPolicy(UnknownChannelPolicy.REJECT)
                    .logger(message ->
                    {
                        System.out.println("[network-proof-server] " + message);
                        System.out.flush();
                        if (message.startsWith("session closed")
                                && closedSessions.incrementAndGet() >= 2)
                        {
                            twoSessionsClosed.countDown();
                        }
                    })
                    .start();

            System.out.println("NETWORK SERVER READY port=" + server.port());
            System.out.flush();

            if (!pingLatch.await(PING_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            {
                System.out.println("NETWORK SERVER CHECK ok=false reason=ping-timeout");
                System.out.flush();
                return;
            }

            String thread = pingThread.get();
            boolean gameThread = "network-proof-server-game".equals(thread);
            System.out.println("NETWORK SERVER CHECK ok=" + gameThread
                    + " serverbound payload=" + pingValue.get()
                    + " thread=" + thread);
            System.out.println("NETWORK SERVER RESULT ok=" + gameThread + " checks=1");
            System.out.flush();
            ok = gameThread;

            if (ok)
            {
                boolean sawMismatchClient = twoSessionsClosed.await(
                        ACCEPT_LINGER_MILLIS, TimeUnit.MILLISECONDS);
                System.out.println("NETWORK SERVER SHUTDOWN ok=true closedSessions="
                        + closedSessions.get() + " mismatchSeen=" + sawMismatchClient);
                System.out.flush();
            }
        }
        finally
        {
            if (server != null)
            {
                server.close();
            }
            game.shutdownNow();
            game.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (!ok)
        {
            System.exit(1);
        }
    }
}
