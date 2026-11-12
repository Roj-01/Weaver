package net.weaver.core.network;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.network.NetworkConnectedEvent;
import net.weaver.api.event.events.network.NetworkDisconnectedEvent;
import net.weaver.api.event.events.network.NetworkHandshakeEvent;
import net.weaver.api.event.events.network.NetworkPayloadEvent;
import net.weaver.api.network.HandshakePolicy;
import net.weaver.api.network.HandshakeRequest;
import net.weaver.api.network.HandshakeResponse;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadContext;
import net.weaver.api.network.PayloadDirection;
import net.weaver.api.network.PayloadRegistration;
import net.weaver.api.network.PayloadType;
import net.weaver.api.network.UnknownChannelPolicy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * One concrete Weaver connection and its login, configuration, and play state
 * machine.
 *
 * <p>The session owns exactly one socket, one daemon reader thread, and one
 * daemon writer thread. Handshake verification, handshake events, payload
 * decoding, payload events, payload handlers, connected events, and
 * disconnected events all run on the supplied game executor. The reader thread
 * only parses frame metadata and the unknown-channel policy; the writer thread
 * only performs socket output.</p>
 *
 * <p>Outbound frames are queued in a bounded
 * {@link NetworkProtocol#MAX_QUEUE_DEPTH} queue. Sending never writes to the
 * socket from the caller's thread, so gameplay code never blocks on socket
 * I/O. A full queue closes the session with {@code outbound queue overflow}.
 * Closing is idempotent and posts exactly one
 * {@link NetworkDisconnectedEvent}.</p>
 */
final class NetworkSession implements NetworkConnection
{
    private static final NetworkPhase[] PHASES = NetworkPhase.values();
    private static final byte[] STOP = new byte[0];
    private static final AtomicLong SESSION_SEQUENCE = new AtomicLong();
    private static final NetworkPlayer UNKNOWN_PLAYER =
            new NetworkPlayer(new UUID(0L, 0L), "connecting");

    /**
     * The maximum inbound payload frames one session may have submitted to the
     * game executor that have not started running yet.
     */
    static final int MAX_PENDING_INBOUND_FRAMES = 256;

    /**
     * The maximum inbound payload bytes one session may have submitted to the
     * game executor that have not started running yet.
     */
    static final long MAX_PENDING_INBOUND_BYTES = 16L * 1024L * 1024L;

    /**
     * How long a draining close waits for the writer before the socket is
     * closed forcibly.
     */
    static final long DRAIN_GRACE_MILLIS = 2_000L;

    private final long sessionId;
    private final NetworkSide side;
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final NetworkRegistry registry;
    private final Executor gameExecutor;
    private final EventBus eventBus;
    private final Consumer<String> logger;
    private final UnknownChannelPolicy unknownChannelPolicy;
    private final int protocolVersion;
    private final String weaverVersion;
    private final String gameVersion;
    private final HandshakePolicy handshakePolicy;
    private final List<NetworkModInfo> localMods;
    private final List<String> localChannels;
    private final HandshakeRequest clientRequest;
    private final PayloadDirection inboundDirection;
    private final PayloadDirection outboundDirection;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicBoolean disconnectedPosted = new AtomicBoolean();
    private final AtomicBoolean handshakeStarted = new AtomicBoolean();
    private final AtomicBoolean handshakeResponseSeen = new AtomicBoolean();
    private final CountDownLatch playLatch = new CountDownLatch(1);
    private final BlockingQueue<byte[]> outbound =
            new LinkedBlockingQueue<>(NetworkProtocol.MAX_QUEUE_DEPTH);
    private final PayloadContext payloadContext;
    private final long handshakeTimeoutMillis;
    private final AtomicInteger pendingInboundFrames = new AtomicInteger();
    private final AtomicLong pendingInboundBytes = new AtomicLong();

    private volatile Thread readerThread;
    private volatile Thread writerThread;
    private volatile ScheduledFuture<?> handshakeDeadline;
    private volatile NetworkPhase phase = NetworkPhase.LOGIN;
    private volatile boolean verified;
    private volatile NetworkPlayer player;
    private volatile List<NetworkModInfo> remoteMods = List.of();
    private volatile HandshakeResponse handshakeResponse;
    private volatile String failureReason = "";
    private volatile boolean playReached;

    private NetworkSession(NetworkSide side, Socket socket, NetworkRegistry registry,
                           Executor gameExecutor, EventBus eventBus, Consumer<String> logger,
                           NetworkPlayer localPlayer, List<NetworkModInfo> localMods,
                           List<String> localChannels, UnknownChannelPolicy unknownChannelPolicy,
                           int protocolVersion, String weaverVersion, String gameVersion,
                           HandshakePolicy handshakePolicy, long handshakeTimeoutMillis)
            throws IOException
    {
        if (handshakeTimeoutMillis < 0L)
        {
            throw new IllegalArgumentException("handshakeTimeoutMillis must be >= 0 but was "
                    + handshakeTimeoutMillis);
        }
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
        this.sessionId = SESSION_SEQUENCE.incrementAndGet();
        this.side = Objects.requireNonNull(side, "side");
        this.socket = Objects.requireNonNull(socket, "socket");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.gameExecutor = Objects.requireNonNull(gameExecutor, "gameExecutor");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.unknownChannelPolicy = Objects.requireNonNull(unknownChannelPolicy,
                "unknownChannelPolicy");
        this.protocolVersion = protocolVersion;
        this.weaverVersion = Objects.requireNonNull(weaverVersion, "weaverVersion");
        this.gameVersion = Objects.requireNonNull(gameVersion, "gameVersion");
        this.handshakePolicy = handshakePolicy;
        this.localMods = List.copyOf(localMods);
        this.localChannels = List.copyOf(localChannels);
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(socket.getOutputStream());
        this.inboundDirection = side == NetworkSide.SERVER
                ? PayloadDirection.SERVERBOUND
                : PayloadDirection.CLIENTBOUND;
        this.outboundDirection = inboundDirection.opposite();
        this.player = side == NetworkSide.CLIENT ? Objects.requireNonNull(localPlayer, "localPlayer")
                : UNKNOWN_PLAYER;
        this.clientRequest = side == NetworkSide.CLIENT
                ? new HandshakeRequest(protocolVersion, weaverVersion, gameVersion,
                        Objects.requireNonNull(localPlayer, "localPlayer"), localMods,
                        localChannels)
                : null;
        this.payloadContext = new SessionPayloadContext();
    }

    /**
     * Creates the server end of an accepted socket.
     *
     * @param socket             the accepted socket, not {@code null}
     * @param registry           the payload registry, not {@code null}
     * @param gameExecutor       the executor that runs all gameplay callbacks
     * @param eventBus           the event bus used for transport events
     * @param logger             the diagnostic logger
     * @param localMods          the server's mod identities
     * @param localChannels      the server's advertised channel ids
     * @param unknownChannelPolicy the policy for unregistered inbound channels
     * @param protocolVersion    the server's protocol version
     * @param weaverVersion      the server's Weaver version
     * @param gameVersion        the server's game version
     * @param handshakePolicy    the optional handshake policy
     * @param handshakeTimeoutMillis the time the peer has to reach {@code PLAY}
     *                           before the session is closed; {@code 0}
     *                           disables the deadline
     * @return a session that has not started its threads yet
     * @throws IOException when the socket streams cannot be created
     */
    static NetworkSession forServer(Socket socket, NetworkRegistry registry,
                                    Executor gameExecutor, EventBus eventBus,
                                    Consumer<String> logger, List<NetworkModInfo> localMods,
                                    List<String> localChannels,
                                    UnknownChannelPolicy unknownChannelPolicy,
                                    int protocolVersion, String weaverVersion,
                                    String gameVersion, HandshakePolicy handshakePolicy,
                                    long handshakeTimeoutMillis)
            throws IOException
    {
        return new NetworkSession(NetworkSide.SERVER, socket, registry, gameExecutor, eventBus,
                logger, null, localMods, localChannels, unknownChannelPolicy, protocolVersion,
                weaverVersion, gameVersion, handshakePolicy, handshakeTimeoutMillis);
    }

    /**
     * Creates the client end of a connected socket.
     *
     * @param socket             the connected socket, not {@code null}
     * @param registry           the payload registry, not {@code null}
     * @param gameExecutor       the executor that runs all gameplay callbacks
     * @param eventBus           the event bus used for transport events
     * @param logger             the diagnostic logger
     * @param player             the local player identity, not {@code null}
     * @param localMods          the client's mod identities
     * @param localChannels      the client's advertised channel ids
     * @param unknownChannelPolicy the policy for unregistered inbound channels
     * @param protocolVersion    the client's protocol version
     * @param weaverVersion      the client's Weaver version
     * @param gameVersion        the client's game version
     * @return a session that has not started its threads yet
     * @throws IOException when the socket streams cannot be created
     */
    static NetworkSession forClient(Socket socket, NetworkRegistry registry,
                                    Executor gameExecutor, EventBus eventBus,
                                    Consumer<String> logger, NetworkPlayer player,
                                    List<NetworkModInfo> localMods,
                                    List<String> localChannels,
                                    UnknownChannelPolicy unknownChannelPolicy,
                                    int protocolVersion, String weaverVersion,
                                    String gameVersion) throws IOException
    {
        return new NetworkSession(NetworkSide.CLIENT, socket, registry, gameExecutor, eventBus,
                logger, player, localMods, localChannels, unknownChannelPolicy, protocolVersion,
                weaverVersion, gameVersion, null, 0L);
    }

    /**
     * Starts the reader and writer daemon threads and, on a client, queues the
     * opening {@code HELLO} frame.
     */
    void start()
    {
        if (side == NetworkSide.CLIENT)
        {
            enqueue(NetworkWire.encode(NetworkWire.FrameType.HELLO,
                    HandshakeCodec.encodeRequest(clientRequest)));
        }
        writerThread = new Thread(this::writeLoop, "weaver-net-write-" + sessionId);
        readerThread = new Thread(this::readLoop, "weaver-net-read-" + sessionId);
        writerThread.setDaemon(true);
        readerThread.setDaemon(true);
        writerThread.start();
        readerThread.start();
        if (handshakeTimeoutMillis > 0L)
        {
            handshakeDeadline = Timer.INSTANCE.schedule(this::handshakeDeadlineExpired,
                    handshakeTimeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void handshakeDeadlineExpired()
    {
        if (!isOpen() || playReached)
        {
            return;
        }
        String reason = "handshake timed out after " + handshakeTimeoutMillis + " ms";
        logger.accept("handshake deadline expired side=" + side + " phase=" + phase);
        closeWithDisconnect(reason);
    }

    /**
     * Returns the handshake response decoded by this session.
     *
     * @return the response, or {@code null} when none was decoded
     */
    HandshakeResponse handshakeResponse()
    {
        return handshakeResponse;
    }

    /**
     * Returns the precise failure reason, or an empty string.
     *
     * @return the failure reason, never {@code null}
     */
    String failureReason()
    {
        return failureReason;
    }

    /**
     * Returns whether the underlying socket has been closed.
     *
     * @return {@code true} once the socket is closed
     */
    boolean isSocketClosed()
    {
        return socket.isClosed();
    }

    /**
     * Waits for the connection to reach play.
     *
     * @param timeout the maximum wait
     * @param unit    the timeout unit, not {@code null}
     * @return {@code true} when play was reached, {@code false} on rejection,
     *         disconnect before play, or timeout
     * @throws InterruptedException when the waiting thread is interrupted
     */
    boolean awaitPlay(long timeout, TimeUnit unit) throws InterruptedException
    {
        Objects.requireNonNull(unit, "unit");
        playLatch.await(timeout, unit);
        return playReached;
    }

    /**
     * Encodes a disconnect body for a raw rejection on an unstarted socket.
     *
     * @param reason the reason, possibly {@code null}
     * @return the encoded body, never {@code null}
     */
    static byte[] encodeDisconnectBody(String reason)
    {
        PayloadBuffer body = PayloadBuffer.writer();
        body.writeString(clipReason(reason), NetworkProtocol.MAX_REASON_CHARS);
        return body.toByteArray();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NetworkSide side()
    {
        return side;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NetworkPhase phase()
    {
        return phase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NetworkPlayer player()
    {
        return player;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOpen()
    {
        return open.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void send(PayloadType<T> type, T payload)
    {
        sendInDirection(type, payload, outboundDirection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnect(String reason)
    {
        Objects.requireNonNull(reason, "reason");
        if (!isOpen())
        {
            return;
        }
        if (reason.length() > NetworkProtocol.MAX_REASON_CHARS)
        {
            throw new IllegalArgumentException("reason must be at most "
                    + NetworkProtocol.MAX_REASON_CHARS + " chars but was " + reason.length());
        }
        String value = reason.isBlank() ? "disconnected" : reason;
        try
        {
            enqueue(NetworkWire.encode(NetworkWire.FrameType.DISCONNECT,
                    encodeDisconnectBody(value)));
        }
        catch (NetworkException failure)
        {
            logger.accept("failed to queue disconnect reason=" + message(failure));
            closeInternal("failed to send disconnect: " + message(failure), false, false);
            return;
        }
        closeInternal(value, true, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        closeInternal("connection closed", true, false);
    }

    private void readLoop()
    {
        try
        {
            while (isOpen())
            {
                NetworkWire.WireFrame frame = NetworkWire.read(input);
                handleFrame(frame);
            }
        }
        catch (EOFException failure)
        {
            if (isOpen())
            {
                closeInternal("connection closed by peer", false, false);
            }
        }
        catch (NetworkException failure)
        {
            protocolFailure(failure.getMessage());
        }
        catch (IOException failure)
        {
            if (isOpen())
            {
                closeInternal("connection error: " + message(failure), false, false);
            }
        }
        catch (RuntimeException failure)
        {
            if (isOpen())
            {
                closeInternal("reader failure: " + message(failure), false, false);
            }
        }
    }

    private void writeLoop()
    {
        try
        {
            while (true)
            {
                byte[] frame;
                try
                {
                    frame = outbound.poll(250L, TimeUnit.MILLISECONDS);
                }
                catch (InterruptedException failure)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (frame == null)
                {
                    if (!isOpen())
                    {
                        break;
                    }
                    continue;
                }
                if (frame == STOP)
                {
                    break;
                }
                output.write(frame);
                output.flush();
            }
        }
        catch (IOException failure)
        {
            if (isOpen())
            {
                closeInternal("connection write failed: " + message(failure), false, false);
            }
        }
        catch (RuntimeException failure)
        {
            if (isOpen())
            {
                closeInternal("writer failure: " + message(failure), false, false);
            }
        }
        finally
        {
            closeSocketQuietly();
        }
    }

    private void handleFrame(NetworkWire.WireFrame frame)
    {
        if (!isOpen())
        {
            return;
        }
        switch (frame.type())
        {
            case HELLO -> handleHello(frame.body());
            case HANDSHAKE -> handleHandshake(frame.body());
            case CONFIG_READY -> handleConfigReady();
            case CONFIG_ACK -> handleConfigAck();
            case PAYLOAD -> handlePayload(frame.body());
            case DISCONNECT -> handleDisconnect(frame.body());
        }
    }

    private void handleHello(byte[] body)
    {
        if (side != NetworkSide.SERVER)
        {
            protocolFailure("unexpected HELLO frame on a client");
            return;
        }
        if (phase != NetworkPhase.LOGIN || !handshakeStarted.compareAndSet(false, true))
        {
            protocolFailure("unexpected HELLO frame during " + phase);
            return;
        }
        HandshakeRequest request;
        try
        {
            request = HandshakeCodec.decodeRequest(body);
        }
        catch (NetworkException failure)
        {
            closeWithDisconnect("malformed handshake request: " + message(failure));
            return;
        }
        submitGame("handshake verification", () -> verifyHandshake(request));
    }

    private void verifyHandshake(HandshakeRequest request)
    {
        if (!isOpen())
        {
            return;
        }
        HandshakeResponse response;
        try
        {
            response = HandshakeVerifier.verify(request, localMods, localChannels,
                    handshakePolicy, protocolVersion, weaverVersion, gameVersion);
        }
        catch (Throwable failure)
        {
            rejectHandshake("handshake verification failed: " + message(failure));
            return;
        }
        if (response.accepted())
        {
            NetworkHandshakeEvent event = new NetworkHandshakeEvent(request, localMods);
            try
            {
                eventBus.post(event);
            }
            catch (Throwable failure)
            {
                rejectHandshake("handshake event failed: " + message(failure));
                return;
            }
            if (event.isRejected())
            {
                response = HandshakeResponse.reject(clipReason(event.rejectionReason()),
                        response.protocolVersion(), response.weaverVersion(),
                        response.gameVersion(), response.mods(), response.channels());
            }
        }
        if (!response.accepted())
        {
            rejectHandshake(response);
            return;
        }
        if (!isOpen())
        {
            return;
        }
        player = request.player();
        remoteMods = request.mods();
        verified = true;
        phase = NetworkPhase.CONFIGURATION;
        try
        {
            enqueue(NetworkWire.encode(NetworkWire.FrameType.HANDSHAKE,
                    HandshakeCodec.encodeResponse(response)));
        }
        catch (NetworkException failure)
        {
            closeInternal("failed to send handshake response: " + message(failure),
                    false, false);
        }
    }

    private void rejectHandshake(String rawReason)
    {
        String reason = clipReason(rawReason);
        if (reason.isBlank())
        {
            reason = "handshake rejected";
        }
        HandshakeResponse response;
        try
        {
            response = HandshakeResponse.reject(reason, protocolVersion, weaverVersion,
                    gameVersion, localMods, localChannels);
        }
        catch (RuntimeException failure)
        {
            closeInternal("failed to build handshake rejection: " + message(failure),
                    false, false);
            return;
        }
        rejectHandshake(response);
    }

    private void rejectHandshake(HandshakeResponse response)
    {
        if (!isOpen())
        {
            return;
        }
        String reason = clipReason(response.reason());
        try
        {
            enqueue(NetworkWire.encode(NetworkWire.FrameType.HANDSHAKE,
                    HandshakeCodec.encodeResponse(response)));
            enqueue(NetworkWire.encode(NetworkWire.FrameType.DISCONNECT,
                    encodeDisconnectBody(reason)));
        }
        catch (NetworkException failure)
        {
            logger.accept("failed to send handshake rejection reason=" + message(failure));
        }
        closeInternal(reason, true, true);
    }

    private void handleHandshake(byte[] body)
    {
        if (side != NetworkSide.CLIENT)
        {
            protocolFailure("unexpected HANDSHAKE frame on a server");
            return;
        }
        if (phase != NetworkPhase.LOGIN
                || !handshakeResponseSeen.compareAndSet(false, true))
        {
            protocolFailure("unexpected HANDSHAKE frame during " + phase);
            return;
        }
        HandshakeResponse response;
        try
        {
            response = HandshakeCodec.decodeResponse(body);
        }
        catch (NetworkException failure)
        {
            closeWithDisconnect("malformed handshake response: " + message(failure));
            return;
        }
        handshakeResponse = response;
        if (!response.accepted())
        {
            recordFailure("handshake rejected: " + response.reason());
        }
        submitGame("handshake response", () -> checkHandshakeResponse(response));
    }

    private void checkHandshakeResponse(HandshakeResponse response)
    {
        if (!isOpen())
        {
            return;
        }
        try
        {
            HandshakeVerifier.checkResponse(response, clientRequest);
        }
        catch (Throwable failure)
        {
            String reason = failure instanceof NetworkException
                    && failure.getMessage() != null
                    ? failure.getMessage()
                    : "handshake response rejected: " + message(failure);
            recordFailure(reason);
            closeWithDisconnect(reason);
            return;
        }
        if (!isOpen())
        {
            return;
        }
        remoteMods = response.mods();
        verified = true;
        phase = NetworkPhase.CONFIGURATION;
        try
        {
            enqueue(NetworkWire.encode(NetworkWire.FrameType.CONFIG_READY, new byte[0]));
        }
        catch (NetworkException failure)
        {
            closeInternal("failed to send configuration ready: " + message(failure),
                    false, false);
        }
    }

    private void handleConfigReady()
    {
        if (side != NetworkSide.SERVER)
        {
            protocolFailure("unexpected CONFIG_READY frame on a client");
            return;
        }
        if (phase != NetworkPhase.CONFIGURATION)
        {
            protocolFailure("unexpected CONFIG_READY frame during " + phase);
            return;
        }
        phase = NetworkPhase.PLAY;
        try
        {
            enqueue(NetworkWire.encode(NetworkWire.FrameType.CONFIG_ACK, new byte[0]));
        }
        catch (NetworkException failure)
        {
            closeInternal("failed to send configuration acknowledgement: "
                    + message(failure), false, false);
            return;
        }
        markPlay();
    }

    private void handleConfigAck()
    {
        if (side != NetworkSide.CLIENT)
        {
            protocolFailure("unexpected CONFIG_ACK frame on a server");
            return;
        }
        if (phase != NetworkPhase.CONFIGURATION)
        {
            protocolFailure("unexpected CONFIG_ACK frame during " + phase);
            return;
        }
        markPlay();
    }

    private void markPlay()
    {
        if (!isOpen())
        {
            return;
        }
        phase = NetworkPhase.PLAY;
        playReached = true;
        cancelHandshakeDeadline();
        NetworkConnectedEvent event = new NetworkConnectedEvent(this, remoteMods);
        submitGame("connected event", () ->
        {
            try
            {
                eventBus.post(event);
            }
            finally
            {
                playLatch.countDown();
            }
        });
    }

    private void handlePayload(byte[] body)
    {
        if (!verified)
        {
            logger.accept("dropped payload before handshake verification side=" + side
                    + " phase=" + phase);
            closeWithDisconnect("unexpected PAYLOAD frame before handshake verification");
            return;
        }
        String channel;
        NetworkPhase payloadPhase;
        byte[] payloadBytes;
        try
        {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            byte[] channelBytes = readSizedBytes(in, NetworkProtocol.MAX_CHANNEL_CHARS * 4,
                    "channel");
            channel = decodeUtf8(channelBytes);
            if (channel.length() > NetworkProtocol.MAX_CHANNEL_CHARS)
            {
                throw new NetworkException("decoded channel of " + channel.length()
                        + " chars exceeds the limit of " + NetworkProtocol.MAX_CHANNEL_CHARS);
            }
            int ordinal = in.read();
            if (ordinal < 0)
            {
                throw new NetworkException("payload frame underflow: missing phase ordinal");
            }
            if (ordinal >= PHASES.length)
            {
                throw new NetworkException("unknown phase ordinal: " + ordinal);
            }
            payloadPhase = PHASES[ordinal];
            payloadBytes = readSizedBytes(in, NetworkProtocol.MAX_PAYLOAD_BYTES, "payload");
            if (in.available() != 0)
            {
                throw new NetworkException("payload frame has " + in.available()
                        + " unread byte(s)");
            }
        }
        catch (NetworkException failure)
        {
            closeWithDisconnect("malformed payload frame: " + message(failure));
            return;
        }
        NetworkPhase current = phase;
        if (payloadPhase != current)
        {
            closeWithDisconnect("payload phase mismatch: channel " + channel + " carried "
                    + payloadPhase + " but connection is in " + current);
            return;
        }
        Optional<PayloadRegistration<?>> registration =
                registry.find(inboundDirection, current, channel);
        if (registration.isEmpty())
        {
            handleUnknownChannel(channel, current);
            return;
        }
        PayloadRegistration<?> resolved = registration.get();
        int frames = pendingInboundFrames.incrementAndGet();
        long bytes = pendingInboundBytes.addAndGet(payloadBytes.length);
        if (frames > MAX_PENDING_INBOUND_FRAMES || bytes > MAX_PENDING_INBOUND_BYTES)
        {
            pendingInboundFrames.decrementAndGet();
            pendingInboundBytes.addAndGet(-payloadBytes.length);
            logger.accept("inbound backlog exceeded side=" + side + " frames=" + frames
                    + " bytes=" + bytes);
            closeWithDisconnect("inbound payload backlog exceeded: " + frames
                    + " frame(s) / " + bytes + " byte(s) waiting for the game thread (limit "
                    + MAX_PENDING_INBOUND_FRAMES + " frames / " + MAX_PENDING_INBOUND_BYTES
                    + " bytes)");
            return;
        }
        boolean submitted = submitGame("payload " + channel, () ->
        {
            pendingInboundFrames.decrementAndGet();
            pendingInboundBytes.addAndGet(-payloadBytes.length);
            dispatchPayload(resolved, channel, payloadBytes);
        });
        if (!submitted)
        {
            pendingInboundFrames.decrementAndGet();
            pendingInboundBytes.addAndGet(-payloadBytes.length);
        }
    }

    private void handleUnknownChannel(String channel, NetworkPhase current)
    {
        Optional<PayloadRegistration<?>> otherPhase =
                registry.findAnyPhase(inboundDirection, channel);
        if (otherPhase.isPresent())
        {
            closeWithDisconnect("channel " + channel + " is not registered for phase "
                    + current + "; it is registered for phase " + otherPhase.get().phase());
            return;
        }
        if (unknownChannelPolicy == UnknownChannelPolicy.REJECT)
        {
            closeWithDisconnect("unknown channel: " + channel);
            return;
        }
        logger.accept("dropped payload unknown-channel=" + channel + " phase=" + current);
    }

    private static byte[] readSizedBytes(ByteArrayInputStream in, int maxBytes, String name)
    {
        int length = readVarInt(in);
        if (length < 0)
        {
            throw new NetworkException("negative " + name + " length: " + length);
        }
        if (length > maxBytes)
        {
            throw new NetworkException(name + " length " + length
                    + " exceeds the limit of " + maxBytes);
        }
        if (length > NetworkProtocol.MAX_PAYLOAD_BYTES)
        {
            throw new NetworkException(name + " length " + length
                    + " exceeds the maximum payload of "
                    + NetworkProtocol.MAX_PAYLOAD_BYTES);
        }
        if (length > in.available())
        {
            throw new NetworkException(name + " underflow: declared " + length
                    + " byte(s) but only " + in.available() + " remain");
        }
        byte[] value = new byte[length];
        int read = in.readNBytes(value, 0, length);
        if (read != length)
        {
            throw new NetworkException(name + " underflow: read " + read + " of " + length
                    + " byte(s)");
        }
        return value;
    }

    private static int readVarInt(ByteArrayInputStream in)
    {
        int result = 0;
        int shift = 0;
        for (int index = 0; index < 5; index++)
        {
            int current = in.read();
            if (current < 0)
            {
                throw new NetworkException("payload frame underflow while reading a varint");
            }
            result |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0)
            {
                return result;
            }
            shift += 7;
        }
        throw new NetworkException("malformed varint: encoding exceeds 5 bytes");
    }

    private static String decodeUtf8(byte[] value)
    {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try
        {
            return decoder.decode(ByteBuffer.wrap(value)).toString();
        }
        catch (CharacterCodingException failure)
        {
            throw new NetworkException("invalid UTF-8 channel id", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatchPayload(PayloadRegistration<?> registration, String channel,
                                 byte[] payloadBytes)
    {
        deliverPayload((PayloadRegistration<Object>) registration, channel, payloadBytes);
    }

    private <T> void deliverPayload(PayloadRegistration<T> registration, String channel,
                                    byte[] payloadBytes)
    {
        if (!isOpen())
        {
            return;
        }
        T decoded;
        try
        {
            decoded = registration.type().codec().decode(payloadBytes);
        }
        catch (Throwable failure)
        {
            logger.accept("malformed payload channel=" + channel + " reason="
                    + failure.getClass().getName());
            closeWithDisconnect("malformed payload: " + channel);
            return;
        }
        if (!isOpen())
        {
            return;
        }
        if (decoded == null)
        {
            logger.accept("malformed payload channel=" + channel
                    + " reason=codec-returned-null");
            closeWithDisconnect("malformed payload: " + channel);
            return;
        }
        NetworkPayloadEvent event = new NetworkPayloadEvent(inboundDirection, this,
                registration.type(), decoded);
        if (!eventBus.post(event))
        {
            return;
        }
        try
        {
            registration.handler().handle(decoded, payloadContext);
        }
        catch (Throwable failure)
        {
            logger.accept("payload handler failed channel=" + channel + " reason="
                    + failure.getClass().getName());
        }
    }

    private void handleDisconnect(byte[] body)
    {
        String reason;
        try
        {
            PayloadBuffer in = PayloadBuffer.reader(body);
            reason = in.readString(NetworkProtocol.MAX_REASON_CHARS);
            in.ensureFullyRead();
        }
        catch (NetworkException failure)
        {
            reason = "malformed disconnect: " + message(failure);
        }
        if (reason.isBlank())
        {
            reason = "remote disconnected";
        }
        String boundedReason = clipReason(reason);
        recordFailure(boundedReason);
        closeInternal(boundedReason, true, false);
    }

    private <T> void sendInDirection(PayloadType<T> type, T payload,
                                     PayloadDirection direction)
    {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        if (!isOpen())
        {
            String reason = failureReason;
            throw new NetworkException(reason.isEmpty()
                    ? "connection is closed"
                    : "connection is closed: " + reason);
        }
        if (!verified)
        {
            throw new NetworkException("connection is not ready: the handshake has not "
                    + "completed");
        }
        NetworkPhase current = phase;
        Optional<PayloadRegistration<?>> registration =
                registry.find(direction, current, type.channel());
        if (registration.isEmpty())
        {
            throw unregistered(direction, current, type.channel());
        }
        byte[] frame = encodePayloadFrame(registration.get(), type, payload, current);
        if (!isOpen())
        {
            throw new NetworkException("connection closed before payload for channel "
                    + type.channel() + " could be queued");
        }
        enqueue(frame);
    }

    private <T> byte[] encodePayloadFrame(PayloadRegistration<?> registration,
                                          PayloadType<T> type, T payload,
                                          NetworkPhase current)
    {
        @SuppressWarnings("unchecked")
        PayloadRegistration<T> typed = (PayloadRegistration<T>) registration;
        byte[] encoded;
        try
        {
            encoded = typed.type().codec().encode(payload);
        }
        catch (NetworkException failure)
        {
            throw failure;
        }
        catch (RuntimeException failure)
        {
            throw new NetworkException("failed to encode payload for channel "
                    + type.channel() + ": " + message(failure), failure);
        }
        if (encoded.length > NetworkProtocol.MAX_PAYLOAD_BYTES)
        {
            throw new NetworkException("encoded payload for channel " + type.channel()
                    + " is " + encoded.length + " bytes, exceeding the maximum of "
                    + NetworkProtocol.MAX_PAYLOAD_BYTES);
        }
        byte[] channelBytes = type.channel().getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream body = new ByteArrayOutputStream(
                channelBytes.length + encoded.length + 11);
        DataOutputStream out = new DataOutputStream(body);
        try
        {
            writeVarInt(out, channelBytes.length);
            out.write(channelBytes);
            out.writeByte(current.ordinal());
            writeVarInt(out, encoded.length);
            out.write(encoded);
        }
        catch (IOException failure)
        {
            throw new NetworkException("failed to encode payload frame for channel "
                    + type.channel(), failure);
        }
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

    private NetworkException unregistered(PayloadDirection direction, NetworkPhase current,
                                          String channel)
    {
        Optional<PayloadRegistration<?>> otherPhase =
                registry.findAnyPhase(direction, channel);
        if (otherPhase.isPresent())
        {
            return new NetworkException("channel " + channel
                    + " is not registered for phase " + current
                    + "; it is registered for phase " + otherPhase.get().phase());
        }
        Optional<PayloadRegistration<?>> otherDirection =
                registry.findAnyPhase(direction.opposite(), channel);
        if (otherDirection.isPresent())
        {
            return new NetworkException("channel " + channel
                    + " is not registered for direction " + direction
                    + "; it is registered for direction " + direction.opposite());
        }
        return new NetworkException("channel " + channel + " is not registered for direction "
                + direction + " in phase " + current);
    }

    private void enqueue(byte[] frame)
    {
        if (!isOpen())
        {
            throw new NetworkException("connection is closed");
        }
        if (!outbound.offer(frame))
        {
            String reason = "outbound queue overflow";
            logger.accept(reason);
            closeInternal(reason, false, false);
            throw new NetworkException(reason);
        }
    }

    private void protocolFailure(String rawReason)
    {
        String reason = clipReason(rawReason);
        if (reason.isBlank())
        {
            reason = "protocol violation";
        }
        logger.accept("protocol failure side=" + side + " reason=" + reason);
        closeWithDisconnect(reason);
    }

    private void closeWithDisconnect(String rawReason)
    {
        String reason = clipReason(rawReason);
        if (!isOpen())
        {
            return;
        }
        recordFailure(reason);
        try
        {
            enqueue(NetworkWire.encode(NetworkWire.FrameType.DISCONNECT,
                    encodeDisconnectBody(reason)));
        }
        catch (NetworkException failure)
        {
            logger.accept("failed to queue disconnect reason=" + message(failure));
        }
        closeInternal(reason, true, true);
    }

    private void closeInternal(String rawReason, boolean graceful, boolean drain)
    {
        String reason = clipReason(rawReason);
        if (!open.compareAndSet(true, false))
        {
            return;
        }
        if (!graceful || !playReached)
        {
            recordFailure(reason);
        }
        cancelHandshakeDeadline();
        if (drain)
        {
            outbound.offer(STOP);
            if (writerThread != null)
            {
                // a peer that stops reading would block output.write forever
                scheduleHardClose();
            }
        }
        else
        {
            outbound.clear();
            outbound.offer(STOP);
            closeSocketQuietly();
        }
        if (writerThread == null)
        {
            closeSocketQuietly();
        }
        playLatch.countDown();
        postDisconnected(reason, graceful);
        logger.accept("session closed side=" + side + " graceful=" + graceful
                + " reason=" + reason);
    }

    private void postDisconnected(String reason, boolean graceful)
    {
        if (!disconnectedPosted.compareAndSet(false, true))
        {
            return;
        }
        NetworkDisconnectedEvent event = new NetworkDisconnectedEvent(this, reason, graceful);
        submitGame("disconnect event", () -> eventBus.post(event));
    }

    private boolean submitGame(String purpose, Runnable task)
    {
        try
        {
            gameExecutor.execute(task);
            return true;
        }
        catch (RejectedExecutionException failure)
        {
            String reason = "game executor rejected task: " + purpose;
            logger.accept(reason);
            closeInternal(reason, false, false);
        }
        catch (RuntimeException failure)
        {
            String reason = "game executor rejected task: " + purpose;
            logger.accept(reason + " reason=" + message(failure));
            closeInternal(reason, false, false);
        }
        return false;
    }

    private void recordFailure(String reason)
    {
        if (reason == null || reason.isBlank())
        {
            return;
        }
        synchronized (this)
        {
            if (failureReason.isEmpty())
            {
                failureReason = clipReason(reason);
            }
        }
    }

    private void cancelHandshakeDeadline()
    {
        ScheduledFuture<?> deadline = handshakeDeadline;
        if (deadline != null)
        {
            deadline.cancel(false);
        }
    }

    private void scheduleHardClose()
    {
        try
        {
            Timer.INSTANCE.schedule(() ->
            {
                if (!socket.isClosed())
                {
                    logger.accept("drain grace expired side=" + side
                            + " action=force-close-socket");
                    closeSocketQuietly();
                }
            }, DRAIN_GRACE_MILLIS, TimeUnit.MILLISECONDS);
        }
        catch (RejectedExecutionException failure)
        {
            closeSocketQuietly();
        }
    }

    private void closeSocketQuietly()
    {
        try
        {
            socket.close();
        }
        catch (IOException ignored)
        {
        }
    }

    private static String clipReason(String value)
    {
        String reason = value == null ? "" : value;
        if (reason.length() <= NetworkProtocol.MAX_REASON_CHARS)
        {
            return reason;
        }
        int limit = NetworkProtocol.MAX_REASON_CHARS;
        if (Character.isHighSurrogate(reason.charAt(limit - 1)))
        {
            limit--;
        }
        return reason.substring(0, limit);
    }

    private static String message(Throwable failure)
    {
        String value = failure.getMessage();
        return value == null || value.isBlank() ? failure.getClass().getSimpleName() : value;
    }

    /**
     * The shared daemon scheduler for handshake deadlines and forced socket
     * closes. It never runs game code.
     */
    private static final class Timer
    {
        private static final ScheduledThreadPoolExecutor INSTANCE = create();

        private static ScheduledThreadPoolExecutor create()
        {
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                    runnable ->
                    {
                        Thread thread = new Thread(runnable, "weaver-net-timer");
                        thread.setDaemon(true);
                        return thread;
                    });
            executor.setRemoveOnCancelPolicy(true);
            return executor;
        }
    }

    private final class SessionPayloadContext implements PayloadContext
    {
        @Override
        public NetworkConnection connection()
        {
            return NetworkSession.this;
        }

        @Override
        public NetworkSide side()
        {
            return side;
        }

        @Override
        public NetworkPhase phase()
        {
            return phase;
        }

        @Override
        public NetworkPlayer player()
        {
            return player;
        }

        @Override
        public <T> void reply(PayloadType<T> type, T payload)
        {
            sendInDirection(type, payload, inboundDirection.opposite());
        }
    }
}
