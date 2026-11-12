package net.weaver.core.config;

import net.weaver.api.config.ConfigException;
import net.weaver.api.config.ConfigManager;
import net.weaver.api.config.ConfigSpec;
import net.weaver.api.config.ConfigSyncDocument;
import net.weaver.api.config.ConfigSyncPolicy;
import net.weaver.api.config.ConfigSyncResult;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.Side;
import net.weaver.api.event.events.network.NetworkConnectedEvent;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadContext;
import net.weaver.api.network.PayloadDirection;
import net.weaver.api.network.PayloadRegistration;
import net.weaver.api.network.PayloadType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Game-free server-authoritative configuration synchronization.
 *
 * <p>The sync layer registers one clientbound payload channel,
 * {@value #MOD_ID}:{@value #PATH}/v1, on a {@link NetworkRegistry} and listens
 * for {@link NetworkConnectedEvent} on an {@link EventBus}. On a server it
 * sends the loaded document of every manager whose spec uses
 * {@link ConfigSyncPolicy#SERVER_TO_CLIENT}. On a client the registered handler
 * finds the manager for the document's mod id, validates the side, policy, and
 * schema version, and applies the values read-only.</p>
 *
 * <p>Installation is explicit and idempotent. The loader boot should call
 * {@link #install(Consumer)} after the network bridge is active; the call does
 * not require a mod callback. Failures never disconnect a peer and never escape
 * into the transport: bad documents are logged once per mod and reason. The
 * layer never writes a configuration file.</p>
 */
public final class ConfigSync
{
    /**
     * The mod id that owns the sync channel.
     */
    public static final String MOD_ID = "weaver";

    /**
     * The sync channel path.
     */
    public static final String PATH = "config/sync";

    /**
     * The registered configuration sync payload type.
     */
    public static final PayloadType<ConfigSyncDocument> TYPE =
            PayloadType.of(MOD_ID, PATH, ConfigSyncDocument.CODEC);

    private static final Object LOCK = new Object();
    private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();

    private static volatile boolean installed;
    private static volatile NetworkRegistry installedRegistry;
    private static volatile EventBus installedBus;
    private static volatile ListenerHandle connectedHandle;
    private static volatile ListenerHandle disconnectedHandle;

    private ConfigSync()
    {
        throw new AssertionError("No ConfigSync instances");
    }

    /**
     * Installs the sync layer on the process-wide registry and game event bus.
     *
     * <p>Equivalent to
     * {@link #install(NetworkRegistry, EventBus, Consumer)} with
     * {@link NetworkRegistry#global()} and {@link EventBus#GAME}.</p>
     *
     * @param log the diagnostic sink, not {@code null}
     * @return {@code true} when this call installed the layer, {@code false}
     *         when it was already installed or the channel was unavailable
     * @throws NullPointerException when {@code log} is {@code null}
     */
    public static boolean install(Consumer<String> log)
    {
        return install(NetworkRegistry.global(), EventBus.GAME, log);
    }

    /**
     * Installs the sync layer on an explicit registry and event bus.
     *
     * <p>The call is idempotent for the same registry and bus. A channel that
     * is already registered by another component makes installation skip with a
     * diagnostic instead of throwing or silently replacing it. Re-installing
     * against a different registry or bus removes the previous connection
     * listener and installs a new one.</p>
     *
     * @param registry the payload registry, not {@code null}
     * @param bus      the bus that posts network lifecycle events, not
     *                 {@code null}
     * @param log      the diagnostic sink, not {@code null}
     * @return {@code true} when this call installed the layer
     * @throws NullPointerException when an argument is {@code null}
     */
    public static boolean install(NetworkRegistry registry, EventBus bus, Consumer<String> log)
    {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(log, "log");
        synchronized (LOCK)
        {
            if (installed && installedRegistry == registry && installedBus == bus
                    && registry.find(PayloadDirection.CLIENTBOUND, NetworkPhase.PLAY,
                            TYPE.channel()).isPresent())
            {
                return false;
            }
            ListenerHandle previous = connectedHandle;
            if (previous != null)
            {
                previous.unregister();
            }
            connectedHandle = null;
            unregisterDisconnected();
            installed = false;
            installedRegistry = null;
            installedBus = null;
            LOGGED_FAILURES.clear();

            Optional<PayloadRegistration<?>> existing = registry.find(
                    PayloadDirection.CLIENTBOUND, NetworkPhase.PLAY, TYPE.channel());
            if (existing.isPresent())
            {
                logLine(log, "[weaver] CONFIG SYNC INSTALL SKIPPED channel=" + TYPE.channel()
                        + " reason=channel-already-registered");
                return false;
            }
            try
            {
                connectedHandle = bus.addListener(NetworkConnectedEvent.class,
                        event -> handleConnected(event, log));
                disconnectedHandle = bus.addListener(
                        net.weaver.api.event.events.network.NetworkDisconnectedEvent.class,
                        event -> handleDisconnected(event, log));
                registry.registerClientbound(MOD_ID, TYPE,
                        (payload, context) -> handleClientDocument(payload, context, log));
            }
            catch (RuntimeException failure)
            {
                if (connectedHandle != null)
                {
                    connectedHandle.unregister();
                }
                connectedHandle = null;
                unregisterDisconnected();
                logLine(log, "[weaver] CONFIG SYNC INSTALL FAILED channel=" + TYPE.channel()
                        + " reason=" + reason(failure));
                return false;
            }
            installed = true;
            installedRegistry = registry;
            installedBus = bus;
            logLine(log, "[weaver] CONFIG SYNC INSTALLED channel=" + TYPE.channel());
            return true;
        }
    }

    /**
     * Sends one manager's document on an open server connection.
     *
     * <p>This is the explicit transport step used by the connected-event
     * handler and by tests. The manager must be loaded, declare
     * {@link ConfigSyncPolicy#SERVER_TO_CLIENT}, and use a server-capable side;
     * the connection must be an open server connection in
     * {@link NetworkPhase#PLAY}. The channel must already be registered, either
     * by {@link #install(Consumer)} or by an isolated
     * {@link #install(NetworkRegistry, EventBus, Consumer)} call.</p>
     *
     * @param connection the open server connection, not {@code null}
     * @param manager    the loaded manager to send, not {@code null}
     * @return the document that was sent, never {@code null}
     * @throws NullPointerException when an argument is {@code null}
     * @throws ConfigException      when the manager or connection is not
     *                              eligible
     */
    public static ConfigSyncDocument sendTo(NetworkConnection connection,
                                            ConfigManager manager)
    {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(manager, "manager");
        ConfigSpec spec = manager.spec();
        if (!manager.isLoaded())
        {
            throw new ConfigException("Config sync cannot send mod '" + spec.modId()
                    + "' before it is loaded");
        }
        if (spec.syncPolicy() != ConfigSyncPolicy.SERVER_TO_CLIENT)
        {
            throw new ConfigException("Config sync is disabled for mod '" + spec.modId()
                    + "'; declare ConfigSyncPolicy.SERVER_TO_CLIENT to send it");
        }
        if (!spec.side().isServer())
        {
            throw new ConfigException("Config sync cannot send mod '" + spec.modId()
                    + "' from side " + spec.side() + "; a server-capable side is required");
        }
        if (connection.side() != NetworkSide.SERVER)
        {
            throw new ConfigException("Config sync requires a SERVER connection but was "
                    + connection.side());
        }
        if (!connection.isOpen())
        {
            throw new ConfigException("Config sync cannot send on a closed connection");
        }
        if (connection.phase() != NetworkPhase.PLAY)
        {
            throw new ConfigException("Config sync requires the PLAY phase but was "
                    + connection.phase());
        }
        ConfigSyncDocument document = ConfigSyncDocument.from(spec.modId(), Side.SERVER,
                manager.document());
        connection.send(TYPE, document);
        return document;
    }

    /**
     * Applies a document to a client manager through the sync path.
     *
     * <p>This is the entry point used by the clientbound handler, tests, and
     * embedders that receive a document out of band. It rejects a document that
     * was not produced by a server, a document for a different mod id, and a
     * manager that did not opt into {@link ConfigSyncPolicy#SERVER_TO_CLIENT},
     * then delegates the value validation to
     * {@link ConfigManager#applySyncedValues(java.util.Map, int)}.</p>
     *
     * @param manager  the client or common manager, not {@code null}
     * @param document the decoded server document, not {@code null}
     * @return the completed sync result, never {@code null}
     * @throws NullPointerException when an argument is {@code null}
     * @throws ConfigException      when the document side is not
     *                              {@link Side#SERVER}, the mod id differs, the
     *                              manager policy is not
     *                              {@link ConfigSyncPolicy#SERVER_TO_CLIENT}, or
     *                              the manager rejects the snapshot
     */
    public static ConfigSyncResult applyTo(ConfigManager manager,
                                           ConfigSyncDocument document)
    {
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(document, "document");
        if (document.side() != Side.SERVER)
        {
            throw new ConfigException("Config sync document for mod '" + document.modId()
                    + "' declares side " + document.side() + " instead of SERVER");
        }
        if (!manager.spec().modId().equals(document.modId()))
        {
            throw new ConfigException("Config sync document for mod '" + document.modId()
                    + "' cannot be applied to mod '" + manager.spec().modId() + "'");
        }
        if (manager.spec().syncPolicy() != ConfigSyncPolicy.SERVER_TO_CLIENT)
        {
            throw new ConfigException("Config sync is disabled for mod '"
                    + manager.spec().modId() + "'; declare ConfigSyncPolicy.SERVER_TO_CLIENT");
        }
        return manager.applySyncedValues(document.values(), document.schemaVersion());
    }

    static void resetForTests()
    {
        synchronized (LOCK)
        {
            if (connectedHandle != null)
            {
                connectedHandle.unregister();
            }
            connectedHandle = null;
            unregisterDisconnected();
            installed = false;
            installedRegistry = null;
            installedBus = null;
            LOGGED_FAILURES.clear();
        }
    }

    private static void unregisterDisconnected()
    {
        if (disconnectedHandle != null)
        {
            disconnectedHandle.unregister();
        }
        disconnectedHandle = null;
    }

    /** A client leaving a server gets its own config values back. */
    private static void handleDisconnected(
            net.weaver.api.event.events.network.NetworkDisconnectedEvent event, Consumer<String> log)
    {
        if (event.connection() == null || event.connection().side() != NetworkSide.CLIENT)
        {
            return;
        }
        for (ConfigManager manager : ConfigManager.loadedManagers())
        {
            try
            {
                manager.restoreLocalValues();
            }
            catch (RuntimeException failure)
            {
                logLine(log, "[weaver] CONFIG SYNC RESTORE FAILED mod=" + manager.spec().modId()
                        + " reason=" + reason(failure));
            }
        }
    }

    private static void handleConnected(NetworkConnectedEvent event, Consumer<String> log)
    {
        if (event.side() != NetworkSide.SERVER)
        {
            return;
        }
        NetworkConnection connection = event.connection();
        if (!connection.isOpen() || connection.phase() != NetworkPhase.PLAY)
        {
            logLine(log, "[weaver] CONFIG SYNC SKIPPED side=SERVER reason=connection-not-ready"
                    + " phase=" + connection.phase());
            return;
        }
        List<ConfigManager> managers = ConfigManager.loadedManagers();
        int sent = 0;
        int candidates = 0;
        for (ConfigManager manager : managers)
        {
            ConfigSpec spec = manager.spec();
            if (spec.syncPolicy() != ConfigSyncPolicy.SERVER_TO_CLIENT)
            {
                continue;
            }
            candidates++;
            if (!spec.side().isServer())
            {
                logOnce(log, "side:" + spec.modId(), "[weaver] CONFIG SYNC SKIPPED mod="
                        + spec.modId() + " side=" + spec.side()
                        + " reason=manager-side-is-not-server");
                continue;
            }
            try
            {
                ConfigSyncDocument document = sendTo(connection, manager);
                sent++;
                logLine(log, "[weaver] CONFIG SYNC SENT mod=" + document.modId()
                        + " version=" + document.schemaVersion()
                        + " keys=" + document.values().size());
            }
            catch (RuntimeException failure)
            {
                logLine(log, "[weaver] CONFIG SYNC SEND FAILED mod=" + spec.modId()
                        + " side=SERVER reason=" + reason(failure));
            }
        }
        if (sent == 0)
        {
            logLine(log, "[weaver] CONFIG SYNC SKIPPED side=SERVER reason=no-sync-managers"
                    + " loaded=" + managers.size() + " candidates=" + candidates);
        }
    }

    private static void handleClientDocument(ConfigSyncDocument document,
                                             PayloadContext context,
                                             Consumer<String> log)
    {
        String modId = document == null ? "<null>" : document.modId();
        try
        {
            if (document == null)
            {
                logOnce(log, "null:" + modId, "[weaver] CONFIG SYNC REJECTED mod=" + modId
                        + " reason=null-document");
                return;
            }
            if (context.side() != NetworkSide.CLIENT)
            {
                logOnce(log, "context-side:" + modId, "[weaver] CONFIG SYNC REJECTED mod="
                        + modId + " side=" + context.side()
                        + " reason=receiver-is-not-client");
                return;
            }
            if (document.side() != Side.SERVER)
            {
                logOnce(log, "document-side:" + modId,
                        "[weaver] CONFIG SYNC REJECTED mod=" + modId
                                + " side=" + document.side()
                                + " reason=document-side-is-not-server");
                return;
            }
            Optional<ConfigManager> found = ConfigManager.find(modId);
            if (found.isEmpty())
            {
                logOnce(log, "missing:" + modId, "[weaver] CONFIG SYNC SKIPPED mod="
                        + modId + " reason=no-config-manager");
                return;
            }
            ConfigManager manager = found.get();
            ConfigSpec spec = manager.spec();
            if (spec.syncPolicy() != ConfigSyncPolicy.SERVER_TO_CLIENT)
            {
                logOnce(log, "policy:" + modId, "[weaver] CONFIG SYNC SKIPPED mod="
                        + modId + " reason=policy-" + spec.syncPolicy());
                return;
            }
            if (spec.side() == Side.SERVER)
            {
                logOnce(log, "manager-side:" + modId,
                        "[weaver] CONFIG SYNC REJECTED mod=" + modId
                                + " side=" + spec.side()
                                + " reason=manager-side-is-server");
                return;
            }
            if (spec.schemaVersion() != document.schemaVersion())
            {
                logOnce(log, "schema:" + modId, "[weaver] CONFIG SYNC REJECTED mod="
                        + modId + " version=" + document.schemaVersion()
                        + " expected=" + spec.schemaVersion() + " reason=schema-mismatch");
                return;
            }
            ConfigSyncResult result = applyTo(manager, document);
            logLine(log, "[weaver] CONFIG SYNC OK mod=" + modId
                    + " side=" + spec.side() + " version=" + result.schemaVersion()
                    + " applied=" + result.appliedValues()
                    + " defaults=" + result.defaultedValues()
                    + " ignored=" + result.ignoredKeys()
                    + " problems=" + result.problems().size());
        }
        catch (Throwable failure)
        {
            logOnce(log, "apply:" + modId, "[weaver] CONFIG SYNC FAILED mod="
                    + modId + " reason=" + reason(failure));
        }
    }

    private static void logOnce(Consumer<String> log, String key, String message)
    {
        if (!LOGGED_FAILURES.add(key))
        {
            return;
        }
        logLine(log, message);
    }

    private static void logLine(Consumer<String> log, String message)
    {
        try
        {
            log.accept(message);
        }
        catch (RuntimeException ignored)
        {
        }
    }

    private static String reason(Throwable failure)
    {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getName()
                : message;
    }
}
