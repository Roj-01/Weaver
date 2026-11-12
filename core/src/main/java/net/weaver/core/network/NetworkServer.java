package net.weaver.core.network;

import net.weaver.api.WeaverApi;
import net.weaver.api.event.EventBus;
import net.weaver.api.network.HandshakePolicy;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.UnknownChannelPolicy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A Weaver network server that accepts connections and drives the login,
 * configuration, and play handshake.
 *
 * <p>The server binds a TCP listener on a daemon accept thread and creates one
 * {@link NetworkConnection} per accepted socket. All handshake verification,
 * events, payload decoding, and payload handlers run on the supplied game
 * executor; the network threads never invoke game code. The server supports at
 * most {@link Builder#maxConnections(int)} simultaneous open sessions and
 * rejects excess connections with a {@code server full} disconnect. A session
 * that has not reached {@code PLAY} within
 * {@link Builder#handshakeTimeoutMillis(int)} (default 10 seconds) is
 * disconnected, so idle sockets cannot hold connection slots.</p>
 *
 * <p>{@link #close()} is idempotent and closes the listener and every live
 * session. A server instance cannot be restarted after it is closed.</p>
 */
public final class NetworkServer implements AutoCloseable
{
    private static final Consumer<String> DEFAULT_LOGGER =
            message -> System.err.println("[weaver] NETWORK " + message);
    private static final AtomicLong SERVER_SEQUENCE = new AtomicLong();

    private final String bindAddress;
    private final int requestedPort;
    private final NetworkRegistry registry;
    private final Executor gameExecutor;
    private final EventBus eventBus;
    private final Consumer<String> logger;
    private final List<NetworkModInfo> mods;
    private final HandshakePolicy handshakePolicy;
    private final UnknownChannelPolicy unknownChannelPolicy;
    private final int protocolVersion;
    private final String weaverVersion;
    private final String gameVersion;
    private final int maxConnections;
    private final int handshakeTimeoutMillis;
    private final long serverId;
    private final CopyOnWriteArrayList<NetworkSession> sessions = new CopyOnWriteArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private volatile int boundPort = -1;

    private NetworkServer(Builder builder)
    {
        this.bindAddress = builder.bindAddress;
        this.requestedPort = builder.port;
        this.registry = builder.registry;
        this.gameExecutor = builder.gameExecutor;
        this.eventBus = builder.eventBus;
        this.logger = builder.logger;
        this.mods = List.copyOf(builder.mods);
        this.handshakePolicy = builder.handshakePolicy;
        this.unknownChannelPolicy = builder.unknownChannelPolicy;
        this.protocolVersion = builder.protocolVersion;
        this.weaverVersion = builder.weaverVersion;
        this.gameVersion = builder.gameVersion;
        this.maxConnections = builder.maxConnections;
        this.handshakeTimeoutMillis = builder.handshakeTimeoutMillis;
        this.serverId = SERVER_SEQUENCE.incrementAndGet();
    }

    /**
     * Creates a server builder using the global payload registry.
     *
     * @return a new builder, never {@code null}
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Creates a server builder using an explicit payload registry.
     *
     * @param registry the registry to consult, not {@code null}
     * @return a new builder, never {@code null}
     * @throws NullPointerException when {@code registry} is {@code null}
     */
    public static Builder builder(NetworkRegistry registry)
    {
        return new Builder().registry(registry);
    }

    /**
     * Returns the actual bound TCP port.
     *
     * @return the port the listener is bound to
     * @throws IllegalStateException when the server has not been started
     */
    public int port()
    {
        if (!started.get())
        {
            throw new IllegalStateException("server is not started");
        }
        return boundPort;
    }

    /**
     * Returns whether the listener is running and has not been closed.
     *
     * @return {@code true} while the server accepts connections
     */
    public boolean isRunning()
    {
        return running.get() && !closed.get();
    }

    /**
     * Returns an immutable snapshot of the currently open sessions.
     *
     * @return an immutable list of live connections, never {@code null}
     */
    public List<NetworkConnection> connections()
    {
        sessions.removeIf(session -> !session.isOpen());
        List<NetworkConnection> snapshot = new ArrayList<>(sessions.size());
        snapshot.addAll(sessions);
        return List.copyOf(snapshot);
    }

    /**
     * Binds the listener and starts the accept loop.
     *
     * <p>Calling this method again on a running server returns the same
     * instance. The actual bound port is available from {@link #port()} after
     * this method returns.</p>
     *
     * @return this server, never {@code null}
     * @throws NetworkException      when the listener cannot bind
     * @throws IllegalStateException when the server was already closed
     */
    public synchronized NetworkServer start()
    {
        if (closed.get())
        {
            throw new IllegalStateException("server is closed");
        }
        if (!started.compareAndSet(false, true))
        {
            return this;
        }
        ServerSocket listener = null;
        try
        {
            listener = new ServerSocket();
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(bindAddress, requestedPort));
        }
        catch (IOException failure)
        {
            closeQuietly(listener);
            started.set(false);
            throw new NetworkException("failed to bind " + bindAddress + ":" + requestedPort
                    + ": " + message(failure), failure);
        }
        serverSocket = listener;
        boundPort = listener.getLocalPort();
        running.set(true);
        Thread thread = new Thread(this::acceptLoop, "weaver-net-accept-" + serverId);
        thread.setDaemon(true);
        acceptThread = thread;
        thread.start();
        logger.accept("server listening address=" + bindAddress + " port=" + boundPort);
        return this;
    }

    /**
     * Closes the listener and every live session.
     *
     * <p>The method is idempotent and does not throw.</p>
     */
    @Override
    public synchronized void close()
    {
        if (!closed.compareAndSet(false, true))
        {
            return;
        }
        running.set(false);
        closeQuietly(serverSocket);
        Thread thread = acceptThread;
        if (thread != null)
        {
            thread.interrupt();
        }
        for (NetworkSession session : sessions)
        {
            session.close();
        }
        sessions.clear();
        logger.accept("server closed port=" + boundPort);
    }

    private void acceptLoop()
    {
        ServerSocket listener = serverSocket;
        while (running.get())
        {
            Socket accepted;
            try
            {
                accepted = listener.accept();
            }
            catch (SocketException failure)
            {
                if (running.get())
                {
                    logger.accept("accept failed reason=" + message(failure));
                }
                break;
            }
            catch (IOException failure)
            {
                if (running.get())
                {
                    logger.accept("accept failed reason=" + message(failure));
                }
                break;
            }
            catch (RuntimeException failure)
            {
                if (running.get())
                {
                    logger.accept("accept failed reason=" + message(failure));
                }
                break;
            }
            if (!running.get() || closed.get())
            {
                closeQuietly(accepted);
                break;
            }
            try
            {
                accepted.setTcpNoDelay(true);
            }
            catch (SocketException ignored)
            {
            }
            sessions.removeIf(session -> !session.isOpen());
            if (sessions.size() >= maxConnections)
            {
                String reason = "server full: maximum connections " + maxConnections;
                logger.accept("connection rejected reason=" + reason);
                rejectConnection(accepted, reason);
                continue;
            }
            try
            {
                NetworkSession session = NetworkSession.forServer(accepted, registry,
                        gameExecutor, eventBus, logger, mods, registry.channels(),
                        unknownChannelPolicy, protocolVersion, weaverVersion, gameVersion,
                        handshakePolicy, handshakeTimeoutMillis);
                sessions.add(session);
                session.start();
            }
            catch (Throwable failure)
            {
                logger.accept("session start failed reason=" + message(failure));
                closeQuietly(accepted);
            }
        }
    }

    private void rejectConnection(Socket socket, String reason)
    {
        try
        {
            byte[] frame = NetworkWire.encode(NetworkWire.FrameType.DISCONNECT,
                    NetworkSession.encodeDisconnectBody(reason));
            OutputStream out = socket.getOutputStream();
            out.write(frame);
            out.flush();
            socket.shutdownOutput();
            try
            {
                socket.setSoTimeout(250);
                InputStream in = socket.getInputStream();
                byte[] drain = new byte[512];
                long deadline = System.nanoTime() + 250_000_000L;
                while (System.nanoTime() < deadline && in.read(drain) >= 0)
                {
                }
            }
            catch (IOException ignored)
            {
            }
        }
        catch (IOException failure)
        {
            logger.accept("failed to send rejection reason=" + message(failure));
        }
        finally
        {
            closeQuietly(socket);
        }
    }

    private static void closeQuietly(AutoCloseable closeable)
    {
        if (closeable == null)
        {
            return;
        }
        try
        {
            closeable.close();
        }
        catch (Exception ignored)
        {
        }
    }

    private static String message(Throwable failure)
    {
        String value = failure.getMessage();
        return value == null || value.isBlank() ? failure.getClass().getSimpleName() : value;
    }

    /**
     * Builds and starts a {@link NetworkServer}.
     *
     * <p>{@link #gameExecutor(Executor)} is required. Every other setting has a
     * documented default. Failed setter validation throws immediately, before
     * any socket is opened.</p>
     */
    public static final class Builder
    {
        private String bindAddress = "127.0.0.1";
        private int port;
        private NetworkRegistry registry = NetworkRegistry.global();
        private Executor gameExecutor;
        private EventBus eventBus = EventBus.GAME;
        private Consumer<String> logger = DEFAULT_LOGGER;
        private List<NetworkModInfo> mods = List.of();
        private HandshakePolicy handshakePolicy;
        private UnknownChannelPolicy unknownChannelPolicy = UnknownChannelPolicy.REJECT;
        private int protocolVersion = NetworkProtocol.VERSION;
        private String weaverVersion = WeaverApi.VERSION;
        private String gameVersion = WeaverApi.GAME_VERSION;
        private int maxConnections = 16;
        private int handshakeTimeoutMillis = 10_000;

        private Builder()
        {
        }

        /**
         * Sets the bind address.
         *
         * @param bindAddress the address to bind, not {@code null} or blank
         * @return this builder, for chaining
         * @throws NullPointerException     when {@code bindAddress} is
         *                                  {@code null}
         * @throws IllegalArgumentException when {@code bindAddress} is blank
         */
        public Builder bindAddress(String bindAddress)
        {
            Objects.requireNonNull(bindAddress, "bindAddress");
            if (bindAddress.isBlank())
            {
                throw new IllegalArgumentException("bindAddress must not be blank");
            }
            this.bindAddress = bindAddress;
            return this;
        }

        /**
         * Sets the listen port.
         *
         * @param port the port from {@code 0} to {@code 65535}; {@code 0} picks
         *             an ephemeral port
         * @return this builder, for chaining
         * @throws IllegalArgumentException when the port is outside
         *                                  {@code 0..65535}
         */
        public Builder port(int port)
        {
            if (port < 0 || port > 65535)
            {
                throw new IllegalArgumentException("port must be between 0 and 65535 but was "
                        + port);
            }
            this.port = port;
            return this;
        }

        /**
         * Sets the payload registry.
         *
         * @param registry the registry to consult, not {@code null}
         * @return this builder, for chaining
         * @throws NullPointerException when {@code registry} is {@code null}
         */
        public Builder registry(NetworkRegistry registry)
        {
            this.registry = Objects.requireNonNull(registry, "registry");
            return this;
        }

        /**
         * Sets the executor that runs all gameplay callbacks.
         *
         * @param gameExecutor the game executor, not {@code null}
         * @return this builder, for chaining
         * @throws NullPointerException when {@code gameExecutor} is {@code null}
         */
        public Builder gameExecutor(Executor gameExecutor)
        {
            this.gameExecutor = Objects.requireNonNull(gameExecutor, "gameExecutor");
            return this;
        }

        /**
         * Sets the event bus for transport events.
         *
         * @param eventBus the event bus, not {@code null}
         * @return this builder, for chaining
         * @throws NullPointerException when {@code eventBus} is {@code null}
         */
        public Builder eventBus(EventBus eventBus)
        {
            this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
            return this;
        }

        /**
         * Sets the diagnostic logger.
         *
         * @param logger the logger, not {@code null}
         * @return this builder, for chaining
         * @throws NullPointerException when {@code logger} is {@code null}
         */
        public Builder logger(Consumer<String> logger)
        {
            this.logger = Objects.requireNonNull(logger, "logger");
            return this;
        }

        /**
         * Replaces the advertised local mod list.
         *
         * @param mods the local mod identities, not {@code null}
         * @return this builder, for chaining
         * @throws NullPointerException when {@code mods} is {@code null} or
         *                              contains {@code null}
         */
        public Builder mods(List<NetworkModInfo> mods)
        {
            this.mods = List.copyOf(mods);
            return this;
        }

        /**
         * Appends one advertised local mod.
         *
         * @param id      the mod id, not {@code null}
         * @param version the mod version, not {@code null}
         * @return this builder, for chaining
         * @throws NullPointerException     when an argument is {@code null}
         * @throws IllegalArgumentException when the id or version is invalid
         */
        public Builder addMod(String id, String version)
        {
            List<NetworkModInfo> updated = new ArrayList<>(mods);
            updated.add(NetworkModInfo.of(id, version));
            this.mods = List.copyOf(updated);
            return this;
        }

        /**
         * Sets the optional handshake policy.
         *
         * @param handshakePolicy the policy, or {@code null} for accept-all
         *                        after the built-in compatibility checks
         * @return this builder, for chaining
         */
        public Builder handshakePolicy(HandshakePolicy handshakePolicy)
        {
            this.handshakePolicy = handshakePolicy;
            return this;
        }

        /**
         * Sets the policy for unregistered inbound channels.
         *
         * @param unknownChannelPolicy the policy, not {@code null}
         * @return this builder, for chaining
         * @throws NullPointerException when {@code unknownChannelPolicy} is
         *                              {@code null}
         */
        public Builder unknownChannelPolicy(UnknownChannelPolicy unknownChannelPolicy)
        {
            this.unknownChannelPolicy = Objects.requireNonNull(unknownChannelPolicy,
                    "unknownChannelPolicy");
            return this;
        }

        /**
         * Sets the advertised protocol version.
         *
         * @param protocolVersion the positive protocol version
         * @return this builder, for chaining
         * @throws IllegalArgumentException when the version is not positive
         */
        public Builder protocolVersion(int protocolVersion)
        {
            if (protocolVersion < 1)
            {
                throw new IllegalArgumentException("protocolVersion must be >= 1 but was "
                        + protocolVersion);
            }
            this.protocolVersion = protocolVersion;
            return this;
        }

        /**
         * Sets the advertised Weaver version.
         *
         * @param weaverVersion the non-blank Weaver version, not {@code null}
         * @return this builder, for chaining
         * @throws NullPointerException     when {@code weaverVersion} is
         *                                  {@code null}
         * @throws IllegalArgumentException when {@code weaverVersion} is blank
         */
        public Builder weaverVersion(String weaverVersion)
        {
            Objects.requireNonNull(weaverVersion, "weaverVersion");
            if (weaverVersion.isBlank())
            {
                throw new IllegalArgumentException("weaverVersion must not be blank");
            }
            this.weaverVersion = weaverVersion;
            return this;
        }

        /**
         * Sets the advertised game version.
         *
         * @param gameVersion the non-blank game version, not {@code null}
         * @return this builder, for chaining
         * @throws NullPointerException     when {@code gameVersion} is
         *                                  {@code null}
         * @throws IllegalArgumentException when {@code gameVersion} is blank
         */
        public Builder gameVersion(String gameVersion)
        {
            Objects.requireNonNull(gameVersion, "gameVersion");
            if (gameVersion.isBlank())
            {
                throw new IllegalArgumentException("gameVersion must not be blank");
            }
            this.gameVersion = gameVersion;
            return this;
        }

        /**
         * Sets the maximum number of simultaneous open sessions.
         *
         * @param maxConnections the positive connection limit
         * @return this builder, for chaining
         * @throws IllegalArgumentException when the limit is not positive
         */
        public Builder maxConnections(int maxConnections)
        {
            if (maxConnections < 1)
            {
                throw new IllegalArgumentException("maxConnections must be >= 1 but was "
                        + maxConnections);
            }
            this.maxConnections = maxConnections;
            return this;
        }

        /**
         * Sets how long an accepted connection may take to reach {@code PLAY}.
         *
         * <p>A session still in {@code LOGIN} or {@code CONFIGURATION} when the
         * deadline expires is disconnected with
         * {@code handshake timed out after <n> ms}. The default is 10000.</p>
         *
         * @param handshakeTimeoutMillis the positive deadline in milliseconds
         * @return this builder, for chaining
         * @throws IllegalArgumentException when the deadline is not positive
         */
        public Builder handshakeTimeoutMillis(int handshakeTimeoutMillis)
        {
            if (handshakeTimeoutMillis < 1)
            {
                throw new IllegalArgumentException("handshakeTimeoutMillis must be >= 1 but was "
                        + handshakeTimeoutMillis);
            }
            this.handshakeTimeoutMillis = handshakeTimeoutMillis;
            return this;
        }

        /**
         * Builds and starts the server.
         *
         * @return the started server, never {@code null}
         * @throws IllegalStateException when {@code gameExecutor} was not set
         * @throws NetworkException      when the listener cannot bind
         */
        public synchronized NetworkServer start()
        {
            if (gameExecutor == null)
            {
                throw new IllegalStateException("gameExecutor is required");
            }
            return new NetworkServer(this).start();
        }
    }
}
