package net.weaver.core.network;

import net.weaver.api.event.EventBus;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadContext;
import net.weaver.api.network.PayloadHandler;
import net.weaver.api.network.PayloadType;
import net.weaver.api.network.UnknownChannelPolicy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class NetworkFixtures
{
    static final String MOD_ID = "testmod";
    static final String MOD_VERSION = "1.0.0";
    static final String GAME_VERSION = "26.3";
    static final String WEAVER_VERSION = "2.1.0";
    static final long DEFAULT_TIMEOUT_SECONDS = 5L;

    static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-000000000001");
    static final NetworkPlayer CLIENT_PLAYER = new NetworkPlayer(CLIENT_ID, "FixtureClient");
    static final List<NetworkModInfo> MODS =
            List.of(NetworkModInfo.of(MOD_ID, MOD_VERSION));

    static final PayloadCodec<String> STRING_CODEC = new PayloadCodec<>()
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

    static final PayloadType<String> PING = PayloadType.of(MOD_ID, "ping", STRING_CODEC);
    static final PayloadType<String> PONG = PayloadType.of(MOD_ID, "pong", STRING_CODEC);

    private NetworkFixtures()
    {
        throw new AssertionError("No NetworkFixtures instances");
    }

    static ExecutorService executor(String threadName)
    {
        return Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    static void shutdown(ExecutorService executor)
    {
        if (executor == null)
        {
            return;
        }
        executor.shutdownNow();
        try
        {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException failure)
        {
            Thread.currentThread().interrupt();
        }
    }

    static void registerPingPong(NetworkRegistry registry, PayloadHandler<String> pingHandler,
                                 PayloadHandler<String> pongHandler)
    {
        registry.registerServerbound(MOD_ID, PING, pingHandler);
        registry.registerClientbound(MOD_ID, PONG, pongHandler);
    }

    static NetworkServer startServer(NetworkRegistry registry, ExecutorService gameExecutor,
                                     EventBus eventBus, UnknownChannelPolicy policy,
                                     Consumer<String> logger)
    {
        return NetworkServer.builder(registry)
                .bindAddress("127.0.0.1")
                .port(0)
                .gameExecutor(gameExecutor)
                .eventBus(eventBus)
                .logger(logger)
                .mods(MODS)
                .unknownChannelPolicy(policy)
                .start();
    }

    static NetworkClient startClient(NetworkRegistry registry, ExecutorService gameExecutor,
                                     EventBus eventBus, int port, int protocolVersion,
                                     Consumer<String> logger)
    {
        return NetworkClient.builder(registry)
                .host("127.0.0.1")
                .port(port)
                .gameExecutor(gameExecutor)
                .eventBus(eventBus)
                .logger(logger)
                .player(CLIENT_PLAYER)
                .mods(MODS)
                .protocolVersion(protocolVersion)
                .connect();
    }

    static NetworkClient startClient(NetworkRegistry registry, ExecutorService gameExecutor,
                                     EventBus eventBus, int port)
    {
        return startClient(registry, gameExecutor, eventBus, port, 1, message ->
        {
        });
    }

    static boolean await(CountDownLatch latch) throws InterruptedException
    {
        return latch.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    static boolean waitUntil(BooleanSupplier condition, long timeoutMillis)
            throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline)
        {
            if (condition.getAsBoolean())
            {
                return true;
            }
            Thread.sleep(10L);
        }
        return condition.getAsBoolean();
    }

    static NetworkConnection awaitServerPlay(NetworkServer server, long timeoutMillis)
            throws InterruptedException
    {
        AtomicReference<NetworkConnection> found = new AtomicReference<>();
        boolean reached = waitUntil(() ->
        {
            for (NetworkConnection connection : server.connections())
            {
                if (connection.isOpen() && connection.phase() == NetworkPhase.PLAY)
                {
                    found.set(connection);
                    return true;
                }
            }
            return false;
        }, timeoutMillis);
        return reached ? found.get() : null;
    }

    static final class HandlerProbe implements PayloadHandler<String>
    {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> value = new AtomicReference<>();
        private final AtomicReference<String> threadName = new AtomicReference<>();
        private final AtomicReference<NetworkSide> side = new AtomicReference<>();
        private final AtomicReference<NetworkPhase> phase = new AtomicReference<>();
        private final AtomicReference<NetworkPlayer> player = new AtomicReference<>();
        private final AtomicReference<NetworkConnection> connection = new AtomicReference<>();

        @Override
        public void handle(String payload, PayloadContext context)
        {
            calls.incrementAndGet();
            value.set(payload);
            threadName.set(Thread.currentThread().getName());
            side.set(context.side());
            phase.set(context.phase());
            player.set(context.player());
            connection.set(context.connection());
            latch.countDown();
        }

        CountDownLatch latch()
        {
            return latch;
        }

        int calls()
        {
            return calls.get();
        }

        String value()
        {
            return value.get();
        }

        String threadName()
        {
            return threadName.get();
        }

        NetworkSide side()
        {
            return side.get();
        }

        NetworkPhase phase()
        {
            return phase.get();
        }

        NetworkPlayer player()
        {
            return player.get();
        }

        NetworkConnection connection()
        {
            return connection.get();
        }
    }
}
