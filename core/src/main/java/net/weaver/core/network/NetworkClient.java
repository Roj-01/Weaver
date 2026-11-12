package net.weaver.core.network;

import net.weaver.api.WeaverApi;
import net.weaver.api.event.EventBus;
import net.weaver.api.network.HandshakeResponse;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.UnknownChannelPolicy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A Weaver network client that opens one socket and drives the login,
 * configuration, and play handshake.
 *
 * <p>{@link #connect()} opens the socket, queues the {@code HELLO} frame, and
 * starts the daemon reader and writer threads. It deliberately does not wait
 * for the connection to reach play; callers use {@link #awaitPlay(long,
 * TimeUnit)} for that. Handshake verification, events, payload decoding, and
 * payload handlers all run on the supplied game executor.</p>
 *
 * <p>A client has exactly one connection. {@link #close()} is idempotent and
 * closes that connection.</p>
 */
public final class NetworkClient implements AutoCloseable
{
    private static final Consumer<String> DEFAULT_LOGGER =
            message -> System.err.println("[weaver] NETWORK " + message);

    private final String host;
    private final int port;
    private final NetworkRegistry registry;
    private final Executor gameExecutor;
    private final EventBus eventBus;
    private final Consumer<String> logger;
    private final NetworkPlayer player;
    private final List<NetworkModInfo> mods;
    private final UnknownChannelPolicy unknownChannelPolicy;
    private final int protocolVersion;
    private final String weaverVersion;
    private final String gameVersion;
    private final int connectTimeoutMillis;
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile NetworkSession session;

    private NetworkClient(Builder builder)
    {
        this.host = builder.host;
        this.port = builder.port;
        this.registry = builder.registry;
        this.gameExecutor = builder.gameExecutor;
        this.eventBus = builder.eventBus;
        this.logger = builder.logger;
        this.player = builder.player;
        this.mods = List.copyOf(builder.mods);
        this.unknownChannelPolicy = builder.unknownChannelPolicy;
        this.protocolVersion = builder.protocolVersion;
        this.weaverVersion = builder.weaverVersion;
        this.gameVersion = builder.gameVersion;
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
    }

    /**
     * Creates a client builder using the global payload registry.
     *
     * @return a new builder, never {@code null}
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Creates a client builder using an explicit payload registry.
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
     * Returns the client's connection.
     *
     * @return the connection, never {@code null}
     * @throws IllegalStateException when {@link #connect()} has not been called
     */
    public NetworkConnection connection()
    {
        NetworkSession current = session;
        if (current == null)
        {
            throw new IllegalStateException("client is not connected");
        }
        return current;
    }

    /**
     * Opens the socket, sends {@code HELLO}, and starts the reader and writer
     * threads.
     *
     * <p>The method returns the client for chaining and does not wait for
     * {@code PLAY}. Calling it again on a connected client returns the same
     * instance.</p>
     *
     * @return this client, never {@code null}
     * @throws NetworkException      when the socket cannot be opened or
     *                               connected
     * @throws IllegalStateException when required builder settings are missing
     *                               or the client was closed
     */
    public synchronized NetworkClient connect()
    {
        if (closed.get())
        {
            throw new IllegalStateException("client is closed");
        }
        if (session != null)
        {
            return this;
        }
        Socket socket = new Socket();
        try
        {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            socket.setTcpNoDelay(true);
            NetworkSession created = NetworkSession.forClient(socket, registry, gameExecutor,
                    eventBus, logger, player, mods, registry.channels(),
                    unknownChannelPolicy, protocolVersion, weaverVersion, gameVersion);
            session = created;
            created.start();
            return this;
        }
        catch (IOException failure)
        {
            session = null;
            closeQuietly(socket);
            throw new NetworkException("failed to connect to " + host + ":" + port + ": "
                    + message(failure), failure);
        }
        catch (RuntimeException failure)
        {
            session = null;
            closeQuietly(socket);
            throw failure;
        }
    }

    /**
     * Waits at most the supplied timeout for the connection to reach play.
     *
     * @param timeout the maximum wait
     * @param unit    the timeout unit, not {@code null}
     * @return {@code true} when play was reached, {@code false} on rejection,
     *         disconnect before play, timeout, or when not connected
     * @throws InterruptedException when the waiting thread is interrupted
     * @throws NullPointerException when {@code unit} is {@code null}
     */
    public boolean awaitPlay(long timeout, TimeUnit unit) throws InterruptedException
    {
        Objects.requireNonNull(unit, "unit");
        NetworkSession current = session;
        return current != null && current.awaitPlay(timeout, unit);
    }

    /**
     * Returns the decoded server handshake response.
     *
     * @return the response, or an empty optional when none was decoded
     */
    public Optional<HandshakeResponse> handshakeResponse()
    {
        NetworkSession current = session;
        return current == null
                ? Optional.empty()
                : Optional.ofNullable(current.handshakeResponse());
    }

    /**
     * Returns the precise rejection or disconnect reason.
     *
     * @return the failure reason, or an empty string when there is none
     */
    public String failureReason()
    {
        NetworkSession current = session;
        return current == null ? "" : current.failureReason();
    }

    /**
     * Closes the connection.
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
        NetworkSession current = session;
        if (current != null)
        {
            current.close();
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
     * Builds and starts a {@link NetworkClient}.
     *
     * <p>{@link #gameExecutor(Executor)},
     * {@link #player(NetworkPlayer)}, and {@link #port(int)} are required.
     * Every other setting has a documented default.</p>
     */
    public static final class Builder
    {
        private String host = "127.0.0.1";
        private int port = -1;
        private NetworkRegistry registry = NetworkRegistry.global();
        private Executor gameExecutor;
        private EventBus eventBus = EventBus.GAME;
        private Consumer<String> logger = DEFAULT_LOGGER;
        private NetworkPlayer player;
        private List<NetworkModInfo> mods = List.of();
        private UnknownChannelPolicy unknownChannelPolicy = UnknownChannelPolicy.REJECT;
        private int protocolVersion = NetworkProtocol.VERSION;
        private String weaverVersion = WeaverApi.VERSION;
        private String gameVersion = WeaverApi.GAME_VERSION;
        private int connectTimeoutMillis = 5000;

        private Builder()
        {
        }

        /**
         * Sets the server host.
         *
         * @param host the host name or address, not {@code null} or blank
         * @return this builder, for chaining
         * @throws NullPointerException     when {@code host} is {@code null}
         * @throws IllegalArgumentException when {@code host} is blank
         */
        public Builder host(String host)
        {
            Objects.requireNonNull(host, "host");
            if (host.isBlank())
            {
                throw new IllegalArgumentException("host must not be blank");
            }
            this.host = host;
            return this;
        }

        /**
         * Sets the server port.
         *
         * @param port the port from {@code 0} to {@code 65535}
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
         * Sets the local player identity.
         *
         * @param player the local player, not {@code null}
         * @return this builder, for chaining
         * @throws NullPointerException when {@code player} is {@code null}
         */
        public Builder player(NetworkPlayer player)
        {
            this.player = Objects.requireNonNull(player, "player");
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
         * Sets the socket connect timeout.
         *
         * @param connectTimeoutMillis the positive timeout in milliseconds
         * @return this builder, for chaining
         * @throws IllegalArgumentException when the timeout is not positive
         */
        public Builder connectTimeoutMillis(int connectTimeoutMillis)
        {
            if (connectTimeoutMillis < 1)
            {
                throw new IllegalArgumentException("connectTimeoutMillis must be >= 1 but was "
                        + connectTimeoutMillis);
            }
            this.connectTimeoutMillis = connectTimeoutMillis;
            return this;
        }

        /**
         * Builds and connects the client.
         *
         * @return the connected client, never {@code null}
         * @throws IllegalStateException when {@code gameExecutor},
         *                               {@code player}, or {@code port} was not
         *                               set
         * @throws NetworkException      when the socket cannot be opened or
         *                               connected
         */
        public NetworkClient connect()
        {
            if (gameExecutor == null)
            {
                throw new IllegalStateException("gameExecutor is required");
            }
            if (player == null)
            {
                throw new IllegalStateException("player is required");
            }
            if (port < 0)
            {
                throw new IllegalStateException("port is required");
            }
            return new NetworkClient(this).connect();
        }
    }
}
