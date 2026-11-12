package net.weaver.api.game.network;

import net.weaver.api.WeaverApi;
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
import net.weaver.api.network.PayloadContext;
import net.weaver.api.network.PayloadDirection;
import net.weaver.api.network.PayloadRegistration;
import net.weaver.api.network.PayloadType;
import net.weaver.api.network.UnknownChannelPolicy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The game-free state machine for one Minecraft network connection.
 *
 * <p>A session owns the bridge handshake, the phase mirror, payload dispatch,
 * the bounded inbound queue, and the Weaver events for one
 * {@code net.minecraft.network.Connection}. It does not import any Minecraft
 * class: the transport is supplied as an {@link Endpoint}, which lets the game
 * bridge, unit tests, and the standalone proof share exactly the same state
 * machine.</p>
 *
 * <p>The session is server-authoritative. A server sends a {@link
 * BridgeEnvelope.Kind#HELLO HELLO} when it enters the configuration phase. A
 * client answers with a {@link BridgeEnvelope.Kind#REQUEST REQUEST}; the server
 * verifies it with {@link BridgeHandshakeVerifier} and answers with a {@link
 * BridgeEnvelope.Kind#RESPONSE RESPONSE}. The server takes the remote player
 * identity from the game connection's authenticated profile
 * ({@link #setAuthenticatedPlayer(NetworkPlayer)}), never from the request, and
 * rejects a request whose UUID differs. Payloads are delivered only after
 * verification. A peer that never completes the exchange is discarded without
 * being disconnected, so vanilla clients and servers are unaffected.</p>
 *
 * <p>{@link NetworkConnectedEvent} is posted once, after verification and after
 * the local side reaches {@code PLAY}. Inbound channel payloads received
 * before that point are queued (up to {@link #MAX_QUEUED_ENVELOPES} envelopes
 * or {@link #MAX_QUEUED_BYTES} bytes) and drained after the connected event. A
 * per-session inbound budget bounds the work one peer can cause, independently
 * of the queue: it holds at most {@link #MAX_INBOUND_BUDGET_BYTES} bytes and
 * refills at {@link #INBOUND_REFILL_BYTES_PER_SECOND} bytes a second, so an
 * honest long session never runs out while a flood still does.</p>
 *
 * <p>All methods are expected to run on the game thread. {@link #send} is the
 * only method that may be called from another thread; it performs no game
 * access and forwards to a thread-safe endpoint.</p>
 */
public final class BridgeSession implements NetworkConnection
{
    /** Hooks run on a server right after a handshake verifies, still in configuration. */
    private static final java.util.concurrent.CopyOnWriteArrayList<java.util.function.Consumer<BridgeSession>>
            SERVER_VERIFIED = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Runs {@code hook} on the server right after a client's handshake
     * verifies, while the connection is still in the configuration phase and
     * before any world data. Configuration-phase payloads can be sent from it.
     * Internal to the game bridge.
     */
    static void onServerVerified(java.util.function.Consumer<BridgeSession> hook)
    {
        SERVER_VERIFIED.addIfAbsent(Objects.requireNonNull(hook, "hook"));
    }

    /**
     * The maximum number of envelopes held before the session reaches
     * {@code PLAY}.
     */
    public static final int MAX_QUEUED_ENVELOPES = 64;

    /**
     * The maximum estimated bytes held before the session reaches
     * {@code PLAY}.
     */
    public static final int MAX_QUEUED_BYTES = 1_048_576;

    /**
     * The maximum bytes one peer may send into this session, including
     * handshake and payload envelopes.
     */
    public static final long MAX_INBOUND_BUDGET_BYTES = 8L * 1024L * 1024L;

    /** Bytes a second the inbound budget refills. */
    public static final long INBOUND_REFILL_BYTES_PER_SECOND = 1024L * 1024L;

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final NetworkPlayer PLACEHOLDER =
            new NetworkPlayer(new UUID(0L, 0L), "connecting");

    private final long sessionId;
    private final NetworkSide side;
    private final Endpoint endpoint;
    private final NetworkRegistry registry;
    private final EventBus eventBus;
    private final Consumer<String> logger;
    private final UnknownChannelPolicy unknownChannelPolicy;
    private final HandshakePolicy handshakePolicy;
    private final int protocolVersion;
    private final String weaverVersion;
    private final String gameVersion;
    private final List<NetworkModInfo> localMods;
    private final List<String> localChannels;
    private final PayloadDirection inboundDirection;
    private final PayloadDirection outboundDirection;
    private final PayloadContext payloadContext = new SessionPayloadContext();
    private final Object queueLock = new Object();
    private final ArrayDeque<BridgeEnvelope> inboundQueue = new ArrayDeque<>();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicBoolean connectedPosted = new AtomicBoolean();
    private final AtomicBoolean disconnectedPosted = new AtomicBoolean();
    private final AtomicBoolean disconnectSent = new AtomicBoolean();
    private final AtomicBoolean transportClosed = new AtomicBoolean();
    private long inboundBudget = MAX_INBOUND_BUDGET_BYTES;
    private long inboundRefillNanos = System.nanoTime();

    private volatile NetworkPhase phase = NetworkPhase.LOGIN;
    private volatile NetworkPlayer player;
    private volatile NetworkPlayer authenticatedPlayer;
    private volatile List<NetworkModInfo> remoteMods = List.of();
    private volatile HandshakeRequest clientRequest;
    private volatile NetworkPhase handshakePhase = NetworkPhase.CONFIGURATION;
    private volatile String failureReason = "";
    private volatile boolean verified;
    private volatile boolean playReady;
    private volatile boolean outboundReady;
    private volatile boolean helloSent;
    private volatile boolean helloSeen;
    private volatile boolean requestSent;
    private volatile boolean requestSeen;
    private volatile boolean unsupported;
    private volatile int queuedBytes;

    private BridgeSession(Builder builder)
    {
        this.sessionId = SEQUENCE.incrementAndGet();
        this.side = builder.side;
        this.endpoint = builder.endpoint;
        this.registry = builder.registry;
        this.eventBus = builder.eventBus;
        this.logger = builder.logger;
        this.unknownChannelPolicy = builder.unknownChannelPolicy;
        this.handshakePolicy = builder.handshakePolicy;
        this.protocolVersion = builder.protocolVersion;
        this.weaverVersion = builder.weaverVersion;
        this.gameVersion = builder.gameVersion;
        this.localMods = List.copyOf(builder.localMods);
        this.localChannels = List.copyOf(builder.localChannels);
        this.inboundDirection = side == NetworkSide.SERVER
                ? PayloadDirection.SERVERBOUND
                : PayloadDirection.CLIENTBOUND;
        this.outboundDirection = inboundDirection.opposite();
        this.player = side == NetworkSide.CLIENT ? builder.localPlayer : PLACEHOLDER;
        this.authenticatedPlayer = side == NetworkSide.SERVER ? builder.authenticatedPlayer
                : null;
    }

    /**
     * Creates a builder for one session.
     *
     * @param side     the local side, not {@code null}
     * @param endpoint the transport used to send envelopes, not {@code null}
     * @return a builder, never {@code null}
     * @throws NullPointerException when an argument is {@code null}
     */
    public static Builder builder(NetworkSide side, Endpoint endpoint)
    {
        return new Builder(side, endpoint);
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
     * Whether the handshake has been verified by both sides.
     *
     * @return {@code true} once a request or response was accepted
     */
    public boolean isVerified()
    {
        return verified;
    }

    /**
     * Whether {@link NetworkConnectedEvent} has been posted.
     *
     * @return {@code true} after the connected event
     */
    public boolean isConnectedPosted()
    {
        return connectedPosted.get();
    }

    /**
     * The remote mods advertised during the handshake.
     *
     * @return an immutable list, never {@code null}
     */
    public List<NetworkModInfo> remoteMods()
    {
        return remoteMods;
    }

    /**
     * The precise failure reason, or an empty string.
     *
     * @return the failure reason, never {@code null}
     */
    public String failureReason()
    {
        return failureReason;
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
     * Sends a payload for an explicit phase.
     *
     * <p>The phase must equal the session's current phase. The envelope carries
     * that phase so the receiver can reject a mismatch instead of delivering a
     * payload into the wrong lifecycle state.</p>
     *
     * @param type    the registered payload type, not {@code null}
     * @param payload the payload to send, not {@code null}
     * @param phase   the current phase, not {@code null}
     * @param <T>     the payload type
     * @throws NullPointerException when an argument is {@code null}
     * @throws NetworkException     when the phase differs, the session is not
     *                              ready, or the channel is not registered for
     *                              the outgoing direction
     */
    public <T> void send(PayloadType<T> type, T payload, NetworkPhase phase)
    {
        Objects.requireNonNull(phase, "phase");
        if (phase != this.phase)
        {
            throw new NetworkException("connection is in phase " + this.phase
                    + " but the payload was requested for " + phase);
        }
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
        String value = reason.isBlank() ? "disconnected" : reason;
        terminate(value, true, true, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        terminate("connection closed", true, false, true);
    }

    /**
     * Receives one decoded envelope from the transport.
     *
     * <p>A malformed or protocol-violating envelope closes the session; this
     * method never throws for peer input.</p>
     *
     * @param envelope the envelope, possibly {@code null}
     */
    /** Takes {@code bytes} from the refilling inbound budget; false when it is exhausted. */
    private synchronized boolean consumeInbound(long bytes)
    {
        long now = System.nanoTime();
        long elapsed = now - inboundRefillNanos;
        if (elapsed > 0)
        {
            double refill = elapsed / 1_000_000_000.0 * INBOUND_REFILL_BYTES_PER_SECOND;
            if (refill >= 1.0)
            {
                inboundBudget = (long) Math.min(MAX_INBOUND_BUDGET_BYTES, inboundBudget + refill);
                inboundRefillNanos = now;
            }
        }
        inboundBudget -= bytes;
        return inboundBudget >= 0;
    }

    public void receive(BridgeEnvelope envelope)
    {
        if (envelope == null || !isOpen())
        {
            return;
        }
        if (!consumeInbound(Math.max(1, envelope.estimatedBytes())))
        {
            fail("inbound byte budget exhausted");
            return;
        }
        try
        {
            switch (envelope.kind())
            {
                case HELLO -> handleHello(envelope);
                case REQUEST -> handleRequest(envelope);
                case RESPONSE -> handleResponse(envelope);
                case PAYLOAD -> handlePayload(envelope);
                case DISCONNECT -> handleDisconnect(envelope);
            }
        }
        catch (NetworkException failure)
        {
            fail(failure.getMessage());
        }
        catch (Throwable failure)
        {
            fail("bridge envelope failed: " + message(failure));
        }
    }

    /**
     * Updates the mirrored connection phase.
     *
     * <p>A server entering {@link NetworkPhase#CONFIGURATION} for the first time
     * sends the bridge {@code HELLO}. Reconfiguration later in the connection
     * keeps the verified state and does not post a second connected event.</p>
     *
     * @param next the new phase, possibly {@code null}
     */
    public synchronized void onPhase(NetworkPhase next)
    {
        if (next == null || !isOpen() || next == phase)
        {
            return;
        }
        NetworkPhase previous = phase;
        phase = next;
        logger.accept("[weaver] NETWORK BRIDGE phase side=" + side + " from=" + previous
                + " to=" + next + " session=" + sessionId);
        if (side == NetworkSide.SERVER && next == NetworkPhase.CONFIGURATION && !helloSent)
        {
            sendHello();
        }
    }

    /**
     * Marks the client's outbound configuration protocol as ready.
     *
     * <p>This is the point at which the client may send its handshake request.
     * If a server {@code HELLO} already arrived, the request is sent now;
     * otherwise it is sent when the hello arrives.</p>
     */
    public synchronized void onClientOutboundReady()
    {
        if (side != NetworkSide.CLIENT || !isOpen())
        {
            return;
        }
        outboundReady = true;
        if (helloSeen && !requestSent)
        {
            sendRequest();
        }
    }

    /**
     * Marks the local side as having reached {@code PLAY}.
     *
     * <p>When the handshake is already verified this posts the connected event
     * and drains queued payloads. When it is not verified the caller decides
     * whether the peer is unsupported.</p>
     */
    public synchronized void onPlayReady()
    {
        if (!isOpen())
        {
            return;
        }
        playReady = true;
        maybeConnected();
        if (!verified && !unsupported)
        {
            discardUnsupported();
        }
    }

    /**
     * Records that the underlying game connection closed.
     *
     * <p>This posts at most one {@link NetworkDisconnectedEvent}, and only for a
     * session that posted a connected event.</p>
     *
     * @param reason   the transport reason, possibly {@code null}
     * @param graceful whether the close was orderly
     */
    public void onTransportClosed(String reason, boolean graceful)
    {
        open.set(false);
        clearInboundQueue();
        if (!unsupported)
        {
            postDisconnectedOnce(clip(reason), graceful);
        }
    }

    /**
     * Marks this session as an unsupported peer and releases its state without
     * posting events or disconnecting the game connection.
     *
     * <p>This is the vanilla-peer path: the peer never answered the bridge
     * handshake, so Weaver must stay invisible to it.</p>
     */
    public void discardUnsupported()
    {
        unsupported = true;
        open.set(false);
        clearInboundQueue();
        logger.accept("[weaver] NETWORK BRIDGE unsupported side=" + side
                + " session=" + sessionId + " reason=handshake-incomplete");
    }

    /**
     * Fails the session with a bounded reason.
     *
     * <p>The caller is expected to be the game bridge or a test. The session
     * sends a bridge {@code DISCONNECT}, closes the transport, and posts at most
     * one disconnected event.</p>
     *
     * @param reason the failure reason, possibly blank
     */
    public void fail(String reason)
    {
        String value = reason == null || reason.isBlank() ? "network bridge failure" : reason;
        logger.accept("[weaver] NETWORK BRIDGE failure side=" + side + " session="
                + sessionId + " reason=" + clip(value));
        terminate(value, false, true, true);
    }

    /**
     * Updates the local player identity on a client before the request is sent.
     *
     * @param localPlayer the local player, possibly {@code null}
     */
    public synchronized void setLocalPlayer(NetworkPlayer localPlayer)
    {
        if (side == NetworkSide.CLIENT && localPlayer != null && !requestSent)
        {
            player = localPlayer;
        }
    }

    /**
     * Supplies the player identity the game connection authenticated, on a
     * server.
     *
     * <p>The server never trusts the identity in the client's handshake
     * request: the request is accepted only when its UUID equals this
     * identity, and the session then reports this identity (not the claimed
     * one) from {@link #player()}. A request that arrives before an
     * authenticated identity was supplied is rejected. The value is ignored on
     * a client and after the handshake was verified.</p>
     *
     * @param authenticated the authenticated identity, possibly {@code null}
     */
    public synchronized void setAuthenticatedPlayer(NetworkPlayer authenticated)
    {
        if (side == NetworkSide.SERVER && authenticated != null && !verified)
        {
            authenticatedPlayer = authenticated;
        }
    }

    /**
     * The client request built for this session, or {@code null} before it is
     * built.
     *
     * @return the request, or {@code null}
     */
    public HandshakeRequest clientRequest()
    {
        return clientRequest;
    }

    private void handleHello(BridgeEnvelope envelope)
    {
        if (side != NetworkSide.CLIENT)
        {
            fail("unexpected HELLO envelope on a server");
            return;
        }
        if (envelope.phase() != NetworkPhase.CONFIGURATION)
        {
            fail("HELLO envelope carried phase " + envelope.phase()
                    + " instead of CONFIGURATION");
            return;
        }
        if (verified)
        {
            return;
        }
        HandshakeResponse announcement = HandshakeResponse.CODEC.decode(envelope.handshake());
        if (!announcement.accepted())
        {
            fail("server hello rejected: " + announcement.reason());
            return;
        }
        if (announcement.protocolVersion() != protocolVersion)
        {
            fail("protocol mismatch: client=" + protocolVersion + ", server="
                    + announcement.protocolVersion());
            return;
        }
        if (!announcement.gameVersion().equals(gameVersion))
        {
            fail("game version mismatch: client=" + gameVersion + ", server="
                    + announcement.gameVersion());
            return;
        }
        if (!WeaverApi.isCompatibleApi(announcement.weaverVersion()))
        {
            fail("weaver version mismatch: client=" + weaverVersion + ", server="
                    + announcement.weaverVersion());
            return;
        }
        helloSeen = true;
        logger.accept("[weaver] NETWORK BRIDGE hello side=CLIENT session=" + sessionId
                + " serverMods=" + announcement.mods().size());
        if (Boolean.getBoolean("weaver.hub"))
        {
            // only games started by Weaver Hub load HubLink (it needs Gson, which plain transports lack)
            try
            {
                HubLink.onServerHello(this, announcement.mods());
            }
            catch (RuntimeException | LinkageError failure)
            {
                logger.accept("[weaver] HUB LINK failed reason=" + failure);
            }
        }
        if (outboundReady && !requestSent)
        {
            sendRequest();
        }
    }

    private void handleRequest(BridgeEnvelope envelope)
    {
        if (side != NetworkSide.SERVER)
        {
            fail("unexpected REQUEST envelope on a client");
            return;
        }
        if (envelope.phase() != NetworkPhase.CONFIGURATION)
        {
            fail("REQUEST envelope carried phase " + envelope.phase()
                    + " instead of CONFIGURATION");
            return;
        }
        if (verified)
        {
            logger.accept("[weaver] NETWORK BRIDGE duplicate REQUEST ignored session="
                    + sessionId);
            return;
        }
        if (requestSeen)
        {
            logger.accept("[weaver] NETWORK BRIDGE duplicate REQUEST ignored session="
                    + sessionId);
            return;
        }
        requestSeen = true;
        handshakePhase = envelope.phase();
        HandshakeRequest request = HandshakeRequest.CODEC.decode(envelope.handshake());
        verifyRequest(request);
    }

    private void verifyRequest(HandshakeRequest claimed)
    {
        NetworkPlayer authenticated = authenticatedPlayer;
        if (authenticated == null)
        {
            rejectHandshake("player identity unavailable: the connection has no "
                    + "authenticated profile");
            return;
        }
        if (!authenticated.id().equals(claimed.player().id()))
        {
            rejectHandshake("player identity mismatch: request claimed "
                    + claimed.player().id() + " but the connection is authenticated as "
                    + authenticated.id());
            return;
        }
        HandshakeRequest request;
        try
        {
            request = new HandshakeRequest(claimed.protocolVersion(), claimed.weaverVersion(),
                    claimed.gameVersion(), authenticated, claimed.mods(), claimed.channels());
        }
        catch (Throwable failure)
        {
            rejectHandshake("handshake verification failed: " + message(failure));
            return;
        }
        HandshakeResponse response;
        try
        {
            response = BridgeHandshakeVerifier.verify(request, localMods, localChannels,
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
                response = rejectResponse(event.rejectionReason());
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
        String reason = response.reason();
        if (reason == null || reason.isBlank())
        {
            reason = "handshake accepted";
        }
        safeSend(BridgeEnvelope.handshake(BridgeEnvelope.Kind.RESPONSE, protocolVersion,
                handshakePhase, HandshakeResponse.CODEC.encode(response)));
        if (!isOpen())
        {
            return;
        }
        player = request.player();
        remoteMods = request.mods();
        verified = true;
        logger.accept("[weaver] NETWORK BRIDGE handshake side=SERVER session=" + sessionId
                + " player=" + player.name() + " mods=" + remoteMods.size()
                + " reason=" + reason);
        for (java.util.function.Consumer<BridgeSession> hook : SERVER_VERIFIED)
        {
            if (!isOpen())
            {
                return;
            }
            try
            {
                hook.accept(this);
            }
            catch (Throwable failure)
            {
                logger.accept("[weaver] NETWORK BRIDGE verified hook FAILED session=" + sessionId
                        + " reason=" + message(failure));
            }
        }
        if (phase == NetworkPhase.CONFIGURATION)
        {
            drainInbound();
        }
        maybeConnected();
    }

    private void rejectHandshake(String rawReason)
    {
        String reason = clip(rawReason);
        if (reason.isBlank())
        {
            reason = "handshake rejected";
        }
        rejectHandshake(rejectResponse(reason));
    }

    private void rejectHandshake(HandshakeResponse response)
    {
        String reason = clip(response.reason());
        if (reason.isBlank())
        {
            reason = "handshake rejected";
        }
        safeSend(BridgeEnvelope.handshake(BridgeEnvelope.Kind.RESPONSE, protocolVersion,
                handshakePhase, HandshakeResponse.CODEC.encode(response)));
        logger.accept("[weaver] NETWORK BRIDGE handshake-rejected side=SERVER session="
                + sessionId + " reason=" + reason);
        recordFailure(reason);
        terminate(reason, false, false, true);
    }

    private void handleResponse(BridgeEnvelope envelope)
    {
        if (side != NetworkSide.CLIENT)
        {
            fail("unexpected RESPONSE envelope on a server");
            return;
        }
        if (envelope.phase() != NetworkPhase.CONFIGURATION)
        {
            fail("RESPONSE envelope carried phase " + envelope.phase()
                    + " instead of CONFIGURATION");
            return;
        }
        if (verified)
        {
            return;
        }
        if (!requestSent || clientRequest == null)
        {
            fail("unexpected RESPONSE envelope before REQUEST");
            return;
        }
        HandshakeResponse response = HandshakeResponse.CODEC.decode(envelope.handshake());
        try
        {
            BridgeHandshakeVerifier.checkResponse(response, clientRequest);
        }
        catch (NetworkException failure)
        {
            recordFailure(failure.getMessage());
            fail(failure.getMessage());
            return;
        }
        remoteMods = response.mods();
        verified = true;
        logger.accept("[weaver] NETWORK BRIDGE handshake side=CLIENT session=" + sessionId
                + " player=" + player.name() + " serverMods=" + remoteMods.size());
        if (phase == NetworkPhase.CONFIGURATION)
        {
            drainInbound();
        }
        maybeConnected();
    }

    private void handlePayload(BridgeEnvelope envelope)
    {
        if (envelope.phase() != phase)
        {
            fail("payload phase mismatch: channel " + envelope.channel() + " carried "
                    + envelope.phase() + " but connection is in " + phase);
            return;
        }
        if (phase == NetworkPhase.LOGIN)
        {
            fail("unexpected PAYLOAD envelope during LOGIN");
            return;
        }
        if (!verified || (phase == NetworkPhase.PLAY && !playReady))
        {
            enqueue(envelope);
            return;
        }
        dispatchPayload(envelope);
    }

    private void handleDisconnect(BridgeEnvelope envelope)
    {
        String reason = envelope.reason().isBlank() ? "remote disconnected"
                : envelope.reason();
        recordFailure(reason);
        logger.accept("[weaver] NETWORK BRIDGE peer-disconnect side=" + side
                + " session=" + sessionId + " reason=" + clip(reason));
        terminate(reason, true, false, true);
    }

    private void sendHello()
    {
        helloSent = true;
        HandshakeResponse announcement = HandshakeResponse.accept(protocolVersion,
                weaverVersion, gameVersion, localMods, localChannels);
        logger.accept("[weaver] NETWORK BRIDGE hello side=SERVER session=" + sessionId
                + " mods=" + localMods.size() + " channels=" + localChannels.size());
        safeSend(BridgeEnvelope.handshake(BridgeEnvelope.Kind.HELLO, protocolVersion,
                phase, HandshakeResponse.CODEC.encode(announcement)));
    }

    private void sendRequest()
    {
        if (side != NetworkSide.CLIENT || requestSent || !isOpen())
        {
            return;
        }
        HandshakeRequest request;
        try
        {
            request = new HandshakeRequest(protocolVersion, weaverVersion, gameVersion,
                    player, localMods, localChannels);
        }
        catch (Throwable failure)
        {
            fail("failed to build handshake request: " + message(failure));
            return;
        }
        clientRequest = request;
        requestSent = true;
        logger.accept("[weaver] NETWORK BRIDGE request side=CLIENT session=" + sessionId
                + " player=" + player.name() + " mods=" + localMods.size()
                + " channels=" + localChannels.size());
        safeSend(BridgeEnvelope.handshake(BridgeEnvelope.Kind.REQUEST, protocolVersion,
                phase, HandshakeRequest.CODEC.encode(request)));
    }

    private synchronized void maybeConnected()
    {
        if (!isOpen() || !verified || !playReady
                || !connectedPosted.compareAndSet(false, true))
        {
            return;
        }
        logger.accept("[weaver] NETWORK BRIDGE connected side=" + side + " session="
                + sessionId + " player=" + player.name() + " playerId=" + player.id()
                + " remoteMods=" + remoteMods.size());
        NetworkConnectedEvent event = new NetworkConnectedEvent(this, remoteMods);
        try
        {
            eventBus.post(event);
        }
        catch (Throwable failure)
        {
            logger.accept("[weaver] NETWORK BRIDGE connected-event FAILED side=" + side
                    + " reason=" + message(failure));
        }
        drainInbound();
    }

    private void enqueue(BridgeEnvelope envelope)
    {
        boolean overflow = false;
        synchronized (queueLock)
        {
            int size = Math.max(1, envelope.estimatedBytes());
            if (inboundQueue.size() >= MAX_QUEUED_ENVELOPES
                    || queuedBytes + size > MAX_QUEUED_BYTES)
            {
                overflow = true;
            }
            else
            {
                inboundQueue.addLast(envelope);
                queuedBytes += size;
            }
        }
        if (overflow)
        {
            fail("inbound queue overflow");
        }
    }

    private void drainInbound()
    {
        List<BridgeEnvelope> batch;
        synchronized (queueLock)
        {
            if (inboundQueue.isEmpty())
            {
                return;
            }
            batch = new ArrayList<>(inboundQueue);
            inboundQueue.clear();
            queuedBytes = 0;
        }
        for (BridgeEnvelope envelope : batch)
        {
            if (!isOpen())
            {
                break;
            }
            if (envelope.phase() != phase)
            {
                logger.accept("[weaver] NETWORK BRIDGE dropped stale payload channel="
                        + envelope.channel() + " queuedPhase=" + envelope.phase()
                        + " currentPhase=" + phase);
                continue;
            }
            dispatchPayload(envelope);
        }
    }

    private void clearInboundQueue()
    {
        synchronized (queueLock)
        {
            inboundQueue.clear();
            queuedBytes = 0;
        }
    }

    private void dispatchPayload(BridgeEnvelope envelope)
    {
        String channel = envelope.channel();
        NetworkPhase payloadPhase = envelope.phase();
        Optional<PayloadRegistration<?>> registration =
                registry.find(inboundDirection, payloadPhase, channel);
        if (registration.isEmpty())
        {
            handleUnknownChannel(channel, payloadPhase);
            return;
        }
        deliverPayload(registration.get(), channel, envelope.payload());
    }

    private void handleUnknownChannel(String channel, NetworkPhase current)
    {
        Optional<PayloadRegistration<?>> otherPhase =
                registry.findAnyPhase(inboundDirection, channel);
        if (otherPhase.isPresent())
        {
            fail("channel " + channel + " is not registered for phase " + current
                    + "; it is registered for phase " + otherPhase.get().phase());
            return;
        }
        if (unknownChannelPolicy == UnknownChannelPolicy.REJECT)
        {
            fail("unknown channel: " + channel);
            return;
        }
        logger.accept("[weaver] NETWORK BRIDGE dropped payload unknown-channel=" + channel
                + " phase=" + current);
    }

    @SuppressWarnings("unchecked")
    private <T> void deliverPayload(PayloadRegistration<?> registration, String channel,
                                    byte[] payloadBytes)
    {
        deliverTyped((PayloadRegistration<T>) registration, channel, payloadBytes);
    }

    private <T> void deliverTyped(PayloadRegistration<T> registration, String channel,
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
            logger.accept("[weaver] NETWORK BRIDGE malformed payload channel=" + channel
                    + " reason=" + failure.getClass().getName());
            fail("malformed payload: " + channel);
            return;
        }
        if (decoded == null)
        {
            logger.accept("[weaver] NETWORK BRIDGE malformed payload channel=" + channel
                    + " reason=codec-returned-null");
            fail("malformed payload: " + channel);
            return;
        }
        NetworkPayloadEvent event = new NetworkPayloadEvent(inboundDirection, this,
                registration.type(), decoded);
        try
        {
            if (!eventBus.post(event))
            {
                return;
            }
        }
        catch (Throwable failure)
        {
            logger.accept("[weaver] NETWORK BRIDGE payload-event FAILED channel=" + channel
                    + " reason=" + message(failure));
            return;
        }
        try
        {
            registration.handler().handle(decoded, payloadContext);
        }
        catch (Throwable failure)
        {
            logger.accept("[weaver] NETWORK BRIDGE payload handler failed channel="
                    + channel + " reason=" + failure.getClass().getName());
        }
    }

    private <T> void sendInDirection(PayloadType<T> type, T payload,
                                     PayloadDirection direction)
    {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        if (!isOpen())
        {
            String reason = failureReason;
            throw new NetworkException(reason.isEmpty() ? "connection is closed"
                    : "connection is closed: " + reason);
        }
        if (!verified)
        {
            throw new NetworkException("connection is not ready: the bridge handshake "
                    + "has not completed");
        }
        NetworkPhase current = phase;
        Optional<PayloadRegistration<?>> registration =
                registry.find(direction, current, type.channel());
        if (registration.isEmpty())
        {
            throw unregistered(direction, current, type.channel());
        }
        @SuppressWarnings("unchecked")
        PayloadRegistration<T> typed = (PayloadRegistration<T>) registration.get();
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
        if (encoded.length > BridgeEnvelope.MAX_PAYLOAD_BYTES)
        {
            throw new NetworkException("encoded payload for channel " + type.channel()
                    + " is " + encoded.length + " bytes, exceeding the bridge maximum of "
                    + BridgeEnvelope.MAX_PAYLOAD_BYTES);
        }
        if (!safeSend(BridgeEnvelope.payload(protocolVersion, current, type.channel(),
                encoded)))
        {
            throw new NetworkException("failed to send payload for channel "
                    + type.channel());
        }
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

    private boolean safeSend(BridgeEnvelope envelope)
    {
        try
        {
            endpoint.send(envelope);
            return true;
        }
        catch (Throwable failure)
        {
            logger.accept("[weaver] NETWORK BRIDGE send FAILED side=" + side + " session="
                    + sessionId + " kind=" + envelope.kind() + " reason="
                    + message(failure));
            terminate("bridge send failed: " + message(failure), false, false, true);
            return false;
        }
    }

    private void trySendDisconnect(String reason)
    {
        if (!disconnectSent.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            endpoint.send(BridgeEnvelope.disconnect(protocolVersion, phase, reason));
        }
        catch (Throwable failure)
        {
            logger.accept("[weaver] NETWORK BRIDGE disconnect-send FAILED side=" + side
                    + " session=" + sessionId + " reason=" + message(failure));
        }
    }

    private void terminate(String rawReason, boolean graceful, boolean notifyPeer,
                           boolean closeTransport)
    {
        String reason = clip(rawReason);
        open.set(false);
        clearInboundQueue();
        if (notifyPeer)
        {
            trySendDisconnect(reason);
        }
        if (closeTransport && transportClosed.compareAndSet(false, true))
        {
            try
            {
                endpoint.disconnect(reason);
            }
            catch (Throwable failure)
            {
                logger.accept("[weaver] NETWORK BRIDGE transport-close FAILED side=" + side
                        + " session=" + sessionId + " reason=" + message(failure));
            }
        }
        logger.accept("[weaver] NETWORK BRIDGE closed side=" + side + " session="
                + sessionId + " graceful=" + graceful + " reason=" + reason);
        postDisconnectedOnce(reason, graceful);
    }

    private void postDisconnectedOnce(String rawReason, boolean graceful)
    {
        if (!connectedPosted.get())
        {
            return;
        }
        String reason = clip(rawReason);
        if (!disconnectedPosted.compareAndSet(false, true))
        {
            return;
        }
        NetworkDisconnectedEvent event = new NetworkDisconnectedEvent(this, reason, graceful);
        try
        {
            eventBus.post(event);
        }
        catch (Throwable failure)
        {
            logger.accept("[weaver] NETWORK BRIDGE disconnect-event FAILED side=" + side
                    + " reason=" + message(failure));
        }
    }

    private HandshakeResponse rejectResponse(String rawReason)
    {
        String reason = clip(rawReason);
        if (reason.isBlank())
        {
            reason = "handshake rejected";
        }
        return HandshakeResponse.reject(reason, protocolVersion, weaverVersion, gameVersion,
                localMods, localChannels);
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
                failureReason = clip(reason);
            }
        }
    }

    private static String clip(String value)
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
     * The transport used by a session to emit envelopes.
     *
     * <p>The game bridge implements this interface with the Minecraft
     * {@code Connection}; tests and the standalone proof implement it with a
     * synchronous or recording endpoint.</p>
     */
    public interface Endpoint
    {
        /**
         * Sends one envelope.
         *
         * @param envelope the envelope, not {@code null}
         */
        void send(BridgeEnvelope envelope);

        /**
         * Closes the underlying connection with a bounded reason.
         *
         * @param reason the reason, not {@code null}
         */
        void disconnect(String reason);
    }

    private final class SessionPayloadContext implements PayloadContext
    {
        @Override
        public NetworkConnection connection()
        {
            return BridgeSession.this;
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

    /**
     * Mutable builder for one {@link BridgeSession}.
     */
    public static final class Builder
    {
        private final NetworkSide side;
        private final Endpoint endpoint;
        private NetworkRegistry registry = NetworkRegistry.global();
        private EventBus eventBus = EventBus.GAME;
        private Consumer<String> logger = System.out::println;
        private NetworkPlayer localPlayer = PLACEHOLDER;
        private NetworkPlayer authenticatedPlayer;
        private List<NetworkModInfo> localMods = List.of();
        private List<String> localChannels;
        private UnknownChannelPolicy unknownChannelPolicy = UnknownChannelPolicy.REJECT;
        private HandshakePolicy handshakePolicy;
        private int protocolVersion = NetworkProtocol.VERSION;
        private String weaverVersion = WeaverApi.VERSION;
        private String gameVersion = WeaverApi.GAME_VERSION;

        private Builder(NetworkSide side, Endpoint endpoint)
        {
            this.side = Objects.requireNonNull(side, "side");
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        }

        /**
         * Sets the channel registry.
         *
         * @param registry the registry, not {@code null}
         * @return this builder
         */
        public Builder registry(NetworkRegistry registry)
        {
            this.registry = Objects.requireNonNull(registry, "registry");
            return this;
        }

        /**
         * Sets the event bus.
         *
         * @param eventBus the bus, not {@code null}
         * @return this builder
         */
        public Builder eventBus(EventBus eventBus)
        {
            this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
            return this;
        }

        /**
         * Sets the diagnostic sink. Lines are already prefixed with
         * {@code [weaver]}.
         *
         * @param logger the sink, not {@code null}
         * @return this builder
         */
        public Builder logger(Consumer<String> logger)
        {
            this.logger = Objects.requireNonNull(logger, "logger");
            return this;
        }

        /**
         * Sets the local player identity. On a server the value is ignored;
         * the remote identity comes from
         * {@link #authenticatedPlayer(NetworkPlayer)}.
         *
         * @param localPlayer the identity, not {@code null}
         * @return this builder
         */
        public Builder localPlayer(NetworkPlayer localPlayer)
        {
            this.localPlayer = Objects.requireNonNull(localPlayer, "localPlayer");
            return this;
        }

        /**
         * Sets the identity the game connection authenticated, on a server.
         * It can also be supplied later with
         * {@link BridgeSession#setAuthenticatedPlayer(NetworkPlayer)}; a
         * server rejects a handshake request while it has none.
         *
         * @param authenticatedPlayer the identity, not {@code null}
         * @return this builder
         */
        public Builder authenticatedPlayer(NetworkPlayer authenticatedPlayer)
        {
            this.authenticatedPlayer = Objects.requireNonNull(authenticatedPlayer,
                    "authenticatedPlayer");
            return this;
        }

        /**
         * Sets the local mod identities.
         *
         * @param localMods the mods, not {@code null}
         * @return this builder
         */
        public Builder localMods(List<NetworkModInfo> localMods)
        {
            this.localMods = List.copyOf(Objects.requireNonNull(localMods, "localMods"));
            return this;
        }

        /**
         * Sets the advertised channel ids. The default is the registry snapshot
         * at build time.
         *
         * @param localChannels the channels, not {@code null}
         * @return this builder
         */
        public Builder localChannels(List<String> localChannels)
        {
            this.localChannels = List.copyOf(Objects.requireNonNull(localChannels,
                    "localChannels"));
            return this;
        }

        /**
         * Sets the unknown-channel policy.
         *
         * @param unknownChannelPolicy the policy, not {@code null}
         * @return this builder
         */
        public Builder unknownChannelPolicy(UnknownChannelPolicy unknownChannelPolicy)
        {
            this.unknownChannelPolicy = Objects.requireNonNull(unknownChannelPolicy,
                    "unknownChannelPolicy");
            return this;
        }

        /**
         * Sets the optional server handshake policy.
         *
         * @param handshakePolicy the policy, or {@code null}
         * @return this builder
         */
        public Builder handshakePolicy(HandshakePolicy handshakePolicy)
        {
            this.handshakePolicy = handshakePolicy;
            return this;
        }

        /**
         * Sets the Weaver protocol version.
         *
         * @param protocolVersion the positive version
         * @return this builder
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
         * Sets the local Weaver API version.
         *
         * @param weaverVersion the non-blank version
         * @return this builder
         */
        public Builder weaverVersion(String weaverVersion)
        {
            this.weaverVersion = requireNonBlank(weaverVersion, "weaverVersion");
            return this;
        }

        /**
         * Sets the local game version.
         *
         * @param gameVersion the non-blank version
         * @return this builder
         */
        public Builder gameVersion(String gameVersion)
        {
            this.gameVersion = requireNonBlank(gameVersion, "gameVersion");
            return this;
        }

        /**
         * Builds the session.
         *
         * @return a new session, never {@code null}
         */
        public BridgeSession build()
        {
            if (localChannels == null)
            {
                localChannels = registry.channels();
            }
            return new BridgeSession(this);
        }

        private static String requireNonBlank(String value, String name)
        {
            Objects.requireNonNull(value, name);
            if (value.isBlank())
            {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }
}
