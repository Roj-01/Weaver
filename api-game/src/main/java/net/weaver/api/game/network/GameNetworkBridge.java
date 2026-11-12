package net.weaver.api.game.network;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.weaver.api.WeaverApi;
import net.weaver.api.event.EventBus;
import net.weaver.api.network.HandshakePolicy;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.UnknownChannelPolicy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Maps Minecraft 26.3 connections to {@link BridgeSession} instances.
 *
 * <p>This class is the game-facing half of the bridge. It converts
 * {@link GamePayload} bodies to and from {@link BridgeEnvelope} instances,
 * mirrors Minecraft's login, configuration, and play phases, and delegates all
 * protocol state to the game-free session. Sessions are keyed by the identity
 * of the game {@code Connection}; an integrated server has one client-side
 * connection and one server-side connection, and each keeps its own session.</p>
 *
 * <p>The bridge is installed in two steps. The loader arms
 * {@link #mixinConfigResource()} before the Mixin transformer is installed, and
 * then calls {@link #install(ClassLoader)}. The codec-registration mixins run
 * during the packet class initialisers, before 26.3 captures the type lists, so
 * {@code weaver:bridge} decodes to a real {@link GamePayload} instead of a
 * byte-discarding {@code DiscardedPayload}.</p>
 *
 * <p>No method here references a client-only class. The client mixins supply
 * the local player identity and the packet processor, which keeps a dedicated
 * server free of client linkage.</p>
 */
public final class GameNetworkBridge
{
    /**
     * The built-in mixin configuration resource owned by this artifact.
     */
    public static final String MIXIN_CONFIG_RESOURCE = "weaver.network.mixins.json";

    private static final String CORE_FEATURE_CLASS = "net.weaver.core.network.NetworkFeature";

    private static final String[] REQUIRED_GAME_CLASSES = {
            "net.minecraft.network.Connection",
            "net.minecraft.network.FriendlyByteBuf",
            "net.minecraft.network.PacketListener",
            "net.minecraft.network.protocol.Packet",
            "net.minecraft.network.ProtocolInfo",
            "net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket",
            "net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket",
            "net.minecraft.network.protocol.common.custom.CustomPacketPayload",
            "net.minecraft.server.players.PlayerList"
    };

    private static final Map<Connection, BridgeSession> SESSIONS = new ConcurrentHashMap<>();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean CODEC_CHECKED = new AtomicBoolean();
    private static final AtomicBoolean NOT_INSTALLED_LOGGED = new AtomicBoolean();

    private static volatile boolean mixinsArmed;
    private static volatile boolean gameClassesPresent;
    private static volatile boolean codecRegistered = true;
    private static volatile List<NetworkModInfo> localMods = List.of();
    private static volatile NetworkPlayer localPlayer =
            new NetworkPlayer(new UUID(0L, 0L), "player");
    private static volatile HandshakePolicy handshakePolicy;
    private static volatile UnknownChannelPolicy unknownChannelPolicy =
            UnknownChannelPolicy.REJECT;

    private GameNetworkBridge()
    {
        throw new AssertionError("No GameNetworkBridge instances");
    }

    /**
     * The mixin configuration resource the loader must arm.
     *
     * <p>The loader reads the resource with its own class loader, then passes
     * the bytes to the Mixin engine before installing the Weaver transformer.
     * The resource lives in the {@code api-game} artifact.</p>
     *
     * @return {@value #MIXIN_CONFIG_RESOURCE}, never {@code null}
     */
    public static String mixinConfigResource()
    {
        return MIXIN_CONFIG_RESOURCE;
    }

    /**
     * Installs the bridge assuming the caller armed the mixin resource.
     *
     * @param gameLoader the loader that sees {@code api-game} and Minecraft
     * @return {@code true} when the bridge can operate
     */
    public static boolean install(ClassLoader gameLoader)
    {
        return install(gameLoader, true);
    }

    /**
     * Installs the bridge and records whether the mixin resource was armed.
     *
     * <p>Game classes are probed with class-loader resources, not
     * {@code Class.forName}: loading a packet class here would define it before
     * the Mixin transformer is installed and would prevent codec registration.
     * The codec registration is verified lazily after the first bridge packet
     * is seen, when the packet classes are necessarily initialised.</p>
     *
     * @param gameLoader   the loader that sees {@code api-game} and Minecraft
     * @param mixinsArmed whether the loader successfully armed
     *                    {@link #mixinConfigResource()}
     * @return {@code true} when the bridge can operate
     */
    public static synchronized boolean install(ClassLoader gameLoader, boolean mixinsArmed)
    {
        ClassLoader loader = gameLoader == null
                ? GameNetworkBridge.class.getClassLoader() : gameLoader;
        boolean resourcePresent = GameNetworkBridge.class.getClassLoader()
                .getResource(MIXIN_CONFIG_RESOURCE) != null
                || (loader != null && loader.getResource(MIXIN_CONFIG_RESOURCE) != null);
        boolean classesPresent = classesPresent(loader);
        boolean armed = mixinsArmed && resourcePresent;
        GameNetworkBridge.mixinsArmed = armed;
        GameNetworkBridge.gameClassesPresent = classesPresent;
        boolean installed = armed && classesPresent;
        INSTALLED.set(installed);
        if (installed)
        {
            RegistrySyncCheck.install(GameNetworkBridge::log);
            net.weaver.api.game.data.PackDataRegistries.install(GameNetworkBridge::log);
            net.weaver.api.game.data.DataMaps.install(GameNetworkBridge::log);
        }
        log("[weaver] NETWORK BRIDGE installed=" + installed + " armed=" + armed
                + " resource=" + resourcePresent + " classes=" + classesPresent);
        notifyCore();
        return installed;
    }

    /**
     * Marks the bridge as uninstalled and releases all sessions.
     *
     * <p>The mixin transformation itself cannot be undone. This method exists
     * for tests and for an orderly shutdown; it posts no events.</p>
     */
    public static void uninstall()
    {
        INSTALLED.set(false);
        mixinsArmed = false;
        gameClassesPresent = false;
        codecRegistered = true;
        CODEC_CHECKED.set(false);
        for (BridgeSession session : SESSIONS.values())
        {
            session.discardUnsupported();
        }
        SESSIONS.clear();
        notifyCore();
    }

    /**
     * Whether the bridge is installed and able to carry Weaver channels.
     *
     * @return {@code true} when the mixin resource was armed, the game classes
     *         were present, and the codec check (once run) passed
     */
    public static boolean isInstalled()
    {
        return INSTALLED.get() && codecRegistered;
    }

    /**
     * Sets the mod identities advertised by this process.
     *
     * <p>The bridge has no loader metadata source in {@code api-game}, so the
     * default list is empty. The loader integration may supply the installed
     * mods. Duplicate ids and oversized lists are rejected.</p>
     *
     * @param mods the mods, not {@code null}
     * @throws NullPointerException     when {@code mods} or an entry is
     *                                  {@code null}
     * @throws IllegalArgumentException when the list has more than
     *                                  {@link NetworkProtocol#MAX_MODS}
     *                                  entries or a duplicate id
     */
    public static synchronized void setLocalMods(List<NetworkModInfo> mods)
    {
        Objects.requireNonNull(mods, "mods");
        if (mods.size() > NetworkProtocol.MAX_MODS)
        {
            throw new IllegalArgumentException("mods must contain at most "
                    + NetworkProtocol.MAX_MODS + " entries but contained " + mods.size());
        }
        Set<String> ids = new HashSet<>();
        for (NetworkModInfo mod : mods)
        {
            Objects.requireNonNull(mod, "mod");
            if (!ids.add(mod.id()))
            {
                throw new IllegalArgumentException("mods must not contain duplicate id '"
                        + mod.id() + "'");
            }
        }
        localMods = List.copyOf(mods);
    }

    /**
     * Sets the client's local player identity.
     *
     * <p>The client handshake mixin calls this before the request is sent. A
     * dedicated server ignores the value; the remote player comes from the
     * connection's authenticated profile, and a request claiming another UUID
     * is rejected.</p>
     *
     * @param player the local player, not {@code null}
     */
    public static void setLocalPlayer(NetworkPlayer player)
    {
        localPlayer = Objects.requireNonNull(player, "player");
    }

    /**
     * Sets the optional server handshake policy.
     *
     * @param policy the policy, or {@code null} for accept-all after the
     *               built-in checks
     */
    public static void setHandshakePolicy(HandshakePolicy policy)
    {
        handshakePolicy = policy;
    }

    /**
     * Sets the unknown-channel policy used by new sessions.
     *
     * @param policy the policy, not {@code null}
     */
    public static void setUnknownChannelPolicy(UnknownChannelPolicy policy)
    {
        unknownChannelPolicy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Returns the bridge connection for one game connection.
     *
     * @param connection the game connection, possibly {@code null}
     * @return the session, or {@code null} when none exists
     */
    public static NetworkConnection connection(Connection connection)
    {
        return connection == null ? null : SESSIONS.get(connection);
    }

    /**
     * Returns the open, verified server-side sessions that can receive
     * clientbound payloads.
     *
     * <p>On a dedicated server every session is serverbound. On an integrated
     * client the local client session is excluded, so a broadcast for runtime
     * capability changes targets players only.</p>
     *
     * @return an immutable snapshot, never {@code null}
     */
    public static List<NetworkConnection> connections()
    {
        List<NetworkConnection> open = new ArrayList<>();
        for (BridgeSession session : SESSIONS.values())
        {
            if (session.side() == NetworkSide.SERVER && session.isOpen()
                    && session.isVerified() && session.isConnectedPosted())
            {
                open.add(session);
            }
        }
        return List.copyOf(open);
    }

    /**
     * Maps a 26.3 protocol descriptor to a Weaver phase.
     *
     * @param protocolInfo the protocol, possibly {@code null}
     * @return the phase, or {@code null} for handshaking and status
     */
    public static NetworkPhase phaseOf(ProtocolInfo<?> protocolInfo)
    {
        return protocolInfo == null ? null : phaseOf(protocolInfo.id());
    }

    /**
     * Maps a 26.3 connection protocol to a Weaver phase.
     *
     * @param protocol the protocol, possibly {@code null}
     * @return the phase, or {@code null} for handshaking and status
     */
    public static NetworkPhase phaseOf(ConnectionProtocol protocol)
    {
        if (protocol == null)
        {
            return null;
        }
        return switch (protocol)
        {
            case LOGIN -> NetworkPhase.LOGIN;
            case CONFIGURATION -> NetworkPhase.CONFIGURATION;
            case PLAY -> NetworkPhase.PLAY;
            case HANDSHAKING, STATUS -> null;
        };
    }

    /**
     * Handles a new inbound protocol listener.
     *
     * <p>Called at the tail of {@code Connection.setupInboundProtocol}. The
     * session is created on the first tracked phase and its mirrored phase is
     * updated. A server entering configuration sends {@code HELLO}.</p>
     *
     * @param connection the game connection, not {@code null}
     * @param listener   the new listener, possibly {@code null}
     * @param phase      the new Weaver phase, possibly {@code null}
     */
    public static void onPhaseInbound(Connection connection, PacketListener listener,
                                      NetworkPhase phase)
    {
        if (connection == null || phase == null)
        {
            return;
        }
        if (!isInstalled())
        {
            logNotInstalledOnce();
            return;
        }
        try
        {
            BridgeSession session = session(connection);
            bindAuthenticatedPlayer(session, listener);
            session.onPhase(phase);
        }
        catch (Throwable failure)
        {
            logFailure("onPhaseInbound", failure);
        }
    }

    /**
     * Handles one inbound bridge payload.
     *
     * <p>The caller (a listener mixin) must already have called
     * {@code PacketUtils.ensureRunningOnSameThread}. The payload body is decoded
     * as a {@link BridgeEnvelope}; malformed input closes the bridge session and
     * never escapes the mixin.</p>
     *
     * @param connection the game connection, not {@code null}
     * @param listener   the receiving listener, possibly {@code null}
     * @param payload    the decoded game payload, not {@code null}
     * @param side       the local receiving side, not {@code null}
     */
    public static void onInbound(Connection connection, PacketListener listener,
                                 GamePayload payload, NetworkSide side)
    {
        if (connection == null || payload == null)
        {
            return;
        }
        if (!isInstalled())
        {
            logNotInstalledOnce();
            return;
        }
        BridgeSession session = null;
        try
        {
            NetworkPhase phase = listener == null ? null : phaseOf(listener.protocol());
            if (phase == null)
            {
                log("[weaver] NETWORK BRIDGE dropped packet phase=unknown side=" + side);
                return;
            }
            session = SESSIONS.get(connection);
            boolean created = false;
            if (session == null)
            {
                BridgeSession fresh = newSession(connection);
                BridgeSession raced = SESSIONS.putIfAbsent(connection, fresh);
                session = raced == null ? fresh : raced;
                created = raced == null;
            }
            bindAuthenticatedPlayer(session, listener);
            session.onPhase(phase);
            if (created && phase == NetworkPhase.PLAY)
            {
                session.onPlayReady();
            }
            verifyCodecOnce();
            if (!codecRegistered)
            {
                return;
            }
            BridgeEnvelope envelope = BridgeEnvelope.decode(payload.body());
            session.receive(envelope);
        }
        catch (Throwable failure)
        {
            logFailure("onInbound", failure);
            if (session != null)
            {
                session.fail("malformed bridge envelope: " + message(failure));
            }
        }
    }

    /**
     * Marks a connection as ready for play on its local side.
     *
     * <p>Called from {@code PlayerList.placeNewPlayer} on a server and from
     * {@code ClientPacketListener.handleLogin} on a client. A verified session
     * posts its connected event and drains queued payloads; an unverified
     * session is discarded as an unsupported (vanilla) peer without being
     * disconnected.</p>
     *
     * @param connection the game connection, not {@code null}
     */
    public static void onPlayReady(Connection connection)
    {
        if (connection == null || !isInstalled())
        {
            return;
        }
        try
        {
            BridgeSession session = SESSIONS.get(connection);
            if (session == null)
            {
                return;
            }
            session.onPlayReady();
            if (!session.isVerified())
            {
                if (SESSIONS.remove(connection, session))
                {
                    session.discardUnsupported();
                    vanillaServer(connection, session);
                }
            }
        }
        catch (Throwable failure)
        {
            logFailure("onPlayReady", failure);
        }
    }

    /**
     * A client reached play on a server that never answered the Weaver handshake
     * (a vanilla server). When Weaver Hub started the game and mods that need a
     * server are loaded, the Hub is asked to restart with the client-only mods and
     * this connection is closed with a message saying so.
     */
    /** Mirrors HubLink.PROPERTY without loading HubLink (and Gson) when the Hub is not in use. */
    private static final String HUB_PROPERTY = "weaver.hub";

    private static void vanillaServer(Connection connection, BridgeSession session)
    {
        if (session.side() != NetworkSide.CLIENT || !Boolean.getBoolean(HUB_PROPERTY))
        {
            return;
        }
        com.google.gson.JsonObject request = HubLink.vanilla(HubLink.address(connection.getRemoteAddress()));
        if (HubLink.send(request))
        {
            log("[weaver] HUB LINK vanilla server: restart requested with client-only mods");
            connection.disconnect(net.minecraft.network.chat.Component.literal(
                    "This is a vanilla server. Weaver Hub is restarting the game with only your client-side mods"
                    + " (minimap, performance...) and will join again. Nothing is deleted."));
        }
    }

    /**
     * Marks the client's outbound configuration protocol as ready.
     *
     * <p>Called at the tail of
     * {@code ClientHandshakePacketListenerImpl.handleLoginFinished}, after
     * 26.3 has switched the outbound protocol to configuration. If a server
     * hello already arrived, the client request is sent here.</p>
     *
     * @param connection the game connection, not {@code null}
     * @param player     the local player, possibly {@code null}
     */
    public static void onClientConfigurationReady(Connection connection, NetworkPlayer player)
    {
        if (connection == null || !isInstalled())
        {
            return;
        }
        if (player != null)
        {
            localPlayer = player;
        }
        try
        {
            BridgeSession session = SESSIONS.get(connection);
            if (session == null)
            {
                return;
            }
            session.setLocalPlayer(player == null ? localPlayer : player);
            session.onClientOutboundReady();
        }
        catch (Throwable failure)
        {
            logFailure("onClientConfigurationReady", failure);
        }
    }

    /**
     * Handles the underlying game connection closing.
     *
     * <p>Called at the return of {@code Connection.handleDisconnection}. The
     * session is removed from the map and posts at most one disconnected event,
     * and only if it had posted a connected event.</p>
     *
     * @param connection the closed connection, not {@code null}
     */
    public static void onDisconnected(Connection connection)
    {
        if (connection == null)
        {
            return;
        }
        BridgeSession session = SESSIONS.remove(connection);
        if (session == null)
        {
            return;
        }
        String reason = "connection closed";
        boolean graceful = false;
        try
        {
            DisconnectionDetails details = connection.getDisconnectionDetails();
            if (details != null && details.reason() != null)
            {
                reason = details.reason().getString();
                graceful = true;
            }
        }
        catch (Throwable ignored)
        {
            // the connection may already be tearing down
        }
        session.onTransportClosed(reason, graceful);
    }

    /**
     * Logs one bridge failure line.
     *
     * <p>Mixin bodies call this instead of letting an exception reach the game.
     * The line is stable so a log alone shows the failure.</p>
     *
     * @param operation the bridge operation that failed
     * @param failure   the failure
     */
    public static void logFailure(String operation, Throwable failure)
    {
        System.err.println("[weaver] NETWORK BRIDGE FAILED operation=" + operation
                + " reason=" + failure);
    }

    /**
     * Writes one bridge diagnostic line.
     *
     * @param message the complete line, not {@code null}
     */
    public static void log(String message)
    {
        System.out.println(message);
    }

    private static BridgeSession session(Connection connection)
    {
        return SESSIONS.computeIfAbsent(connection, GameNetworkBridge::newSession);
    }

    private static BridgeSession newSession(Connection connection)
    {
        NetworkSide side = sideOf(connection);
        BridgeSession.Endpoint endpoint = new BridgeSession.Endpoint()
        {
            @Override
            public void send(BridgeEnvelope envelope)
            {
                sendEnvelope(connection, side, envelope);
            }

            @Override
            public void disconnect(String reason)
            {
                disconnectGame(connection, reason);
            }
        };
        NetworkRegistry registry = NetworkRegistry.global();
        BridgeSession session = BridgeSession.builder(side, endpoint)
                .registry(registry)
                .eventBus(EventBus.GAME)
                .logger(System.out::println)
                .localPlayer(localPlayer)
                .localMods(localMods)
                .localChannels(registry.channels())
                .unknownChannelPolicy(unknownChannelPolicy)
                .handshakePolicy(handshakePolicy)
                .protocolVersion(NetworkProtocol.VERSION)
                .weaverVersion(WeaverApi.VERSION)
                .gameVersion(WeaverApi.GAME_VERSION)
                .build();
        log("[weaver] NETWORK BRIDGE session side=" + side + " connection="
                + Integer.toHexString(System.identityHashCode(connection)));
        if (Boolean.getBoolean(HUB_PROPERTY))
        {
            HubLink.remember(session, connection.getRemoteAddress());
        }
        return session;
    }

    /**
     * Supplies a server session with the profile the game connection
     * authenticated during login. Only a server-side common listener
     * (configuration or play) carries that profile; any other listener leaves
     * the session without an identity, and a handshake request is then
     * rejected rather than trusted.
     */
    private static void bindAuthenticatedPlayer(BridgeSession session, PacketListener listener)
    {
        if (session.side() != NetworkSide.SERVER
                || !(listener instanceof ServerCommonPacketListenerImpl common))
        {
            return;
        }
        GameProfile profile = common.getOwner();
        if (profile == null || profile.id() == null)
        {
            return;
        }
        try
        {
            session.setAuthenticatedPlayer(new NetworkPlayer(profile.id(), profile.name()));
        }
        catch (RuntimeException invalidIdentity)
        {
            logFailure("bindAuthenticatedPlayer", invalidIdentity);
        }
    }

    private static NetworkSide sideOf(Connection connection)
    {
        return connection.getReceiving() == PacketFlow.SERVERBOUND
                ? NetworkSide.SERVER : NetworkSide.CLIENT;
    }

    private static void sendEnvelope(Connection connection, NetworkSide side,
                                     BridgeEnvelope envelope)
    {
        byte[] body = envelope.encode();
        GamePayload payload = new GamePayload(body);
        Packet<?> packet = side == NetworkSide.SERVER
                ? new ClientboundCustomPayloadPacket(payload)
                : new ServerboundCustomPayloadPacket(payload);
        connection.send(packet);
    }

    private static void disconnectGame(Connection connection, String reason)
    {
        String bounded = clip(reason);
        if (bounded.isBlank())
        {
            bounded = "weaver network bridge disconnected";
        }
        try
        {
            // On a server, the listener sends a disconnect packet with the reason
            // before closing, so the player sees why. Connection#disconnect alone
            // closes at once and the client only shows "Disconnected".
            if (connection.getPacketListener() instanceof ServerCommonPacketListenerImpl listener)
            {
                listener.disconnect(Component.literal(bounded));
            }
            else
            {
                connection.disconnect(Component.literal(bounded));
            }
        }
        catch (Throwable failure)
        {
            logFailure("disconnectGame", failure);
        }
    }

    private static void verifyCodecOnce()
    {
        if (!CODEC_CHECKED.compareAndSet(false, true))
        {
            return;
        }
        try
        {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            ClientboundCustomPayloadPacket packet = new ClientboundCustomPayloadPacket(
                    new GamePayload(new byte[]{1, 2, 3}));
            try
            {
                ClientboundCustomPayloadPacket.CONFIG_STREAM_CODEC.encode(buffer, packet);
                ClientboundCustomPayloadPacket decoded =
                        ClientboundCustomPayloadPacket.CONFIG_STREAM_CODEC.decode(buffer);
                codecRegistered = decoded.payload() instanceof GamePayload;
            }
            finally
            {
                buffer.release();
            }
            if (!codecRegistered)
            {
                INSTALLED.set(false);
                mixinsArmed = false;
                log("[weaver] NETWORK BRIDGE CODEC missing id=weaver:bridge "
                        + "action=bridge-disabled");
                notifyCore();
            }
            else
            {
                log("[weaver] NETWORK BRIDGE CODEC verified id=weaver:bridge");
            }
        }
        catch (Throwable failure)
        {
            codecRegistered = false;
            INSTALLED.set(false);
            logFailure("verifyCodec", failure);
            notifyCore();
        }
    }

    private static void notifyCore()
    {
        try
        {
            Class<?> feature = Class.forName(CORE_FEATURE_CLASS, true,
                    GameNetworkBridge.class.getClassLoader());
            Method install = feature.getMethod("install", boolean.class, boolean.class);
            install.invoke(null, mixinsArmed, gameClassesPresent && codecRegistered);
        }
        catch (Throwable ignored)
        {
            // api-game can run without the core loader; the loader sets the state.
        }
    }

    private static boolean classesPresent(ClassLoader loader)
    {
        if (loader == null)
        {
            return false;
        }
        for (String className : REQUIRED_GAME_CLASSES)
        {
            String resource = className.replace('.', '/') + ".class";
            if (loader.getResource(resource) == null)
            {
                return false;
            }
        }
        return true;
    }

    private static void logNotInstalledOnce()
    {
        if (NOT_INSTALLED_LOGGED.compareAndSet(false, true))
        {
            log("[weaver] NETWORK BRIDGE skipped reason=not-installed "
                    + "action=arm-weaver.network.mixins.json-and-install");
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
     * Returns the current session map size for diagnostics.
     *
     * @return the number of live bridge sessions
     */
    public static int sessionCount()
    {
        return SESSIONS.size();
    }

    /**
     * Returns an optional session wrapper for diagnostics.
     *
     * @param connection the game connection, possibly {@code null}
     * @return the session, or an empty optional
     */
    public static Optional<NetworkConnection> find(Connection connection)
    {
        return connection == null ? Optional.empty()
                : Optional.ofNullable((NetworkConnection) SESSIONS.get(connection));
    }
}
