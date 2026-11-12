package net.weaver.api;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.registry.DeferredRegister;
import net.weaver.api.registry.RegistryView;
import net.weaver.api.registry.RegistryKey;
import net.weaver.api.registry.RegistryManager;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The context object handed to a mod.
 *
 * <p>This is convenience only. A mod is free to import game classes directly and
 * to keep its own state on its own instance.
 */
public interface ModContext {

    /** Stable mod id from {@code weaver.mod.json}. */
    String id();

    /** Process side: {@code client} or {@code server}. */
    String side();

    /** Directory for this mod's configuration files. */
    Path configDir();

    /** Directory for this mod's persistent data. */
    Path dataDir();

    /** Writes one line to the loader log. */
    void log(String message);

    /** The global game event bus. Listeners should usually be registered here. */
    default EventBus events() {
        return EventBus.GAME;
    }

    /**
     * The mod's local event bus.
     *
     * <p>For contexts created by the loader this is the bus returned by
     * {@link EventBus#local(String)} for this mod, so events posted here reach
     * this mod's listeners first and then the global buses. This interface
     * default returning {@link EventBus#MOD} is only a fallback for custom
     * contexts that do not carry a local bus.
     *
     * @return the mod's local bus, never {@code null}
     */
    default EventBus modEvents() {
        return EventBus.MOD;
    }

    /**
     * The global aggregate mod bus, {@link EventBus#MOD}.
     *
     * <p>This bus receives events posted on every mod's local bus. It is useful
     * for cross-mod observation that must see all mod-scoped traffic.
     *
     * @return the global aggregate mod bus, never {@code null}
     */
    default EventBus globalModEvents() {
        return EventBus.MOD;
    }

    /**
     * The logical side this context serves. {@code BOTH} for the base context
     * and for callbacks that are not side-specific; side-specific callbacks
     * receive a client or server context.
     */
    default LogicalSide logicalSide() {
        return LogicalSide.BOTH;
    }

    /**
     * Physical process check: true on any client process, including a client
     * that runs an integrated server.
     *
     * <p>This is the same check as {@link #isClient()} and is kept as an
     * explicit name for code that must distinguish physical from logical side.
     */
    default boolean isPhysicalClient() {
        return isClient();
    }

    /**
     * Physical process check: true only on a dedicated server process. This is
     * the same check as {@link #isServer()}.
     */
    default boolean isPhysicalServer() {
        return isServer();
    }

    /** True when this context serves the logical client. */
    default boolean isLogicalClient() {
        return logicalSide().isClient();
    }

    /** True when this context serves the logical server. */
    default boolean isLogicalServer() {
        return logicalSide().isServer();
    }

    /**
     * Physical process check: true on a client process, including a client that
     * runs an integrated server. Inside a server callback on such a client this
     * is still true; use {@link #isLogicalServer()} there.
     */
    default boolean isClient() {
        return "client".equals(side());
    }

    /**
     * Physical process check: true only on a dedicated server process. Inside a
     * server callback on a client with an integrated server this is false; use
     * {@link #isLogicalServer()} there.
     */
    default boolean isServer() {
        return "server".equals(side());
    }

    /** Creates a deferred register scoped to this mod's namespace. */
    default <T> DeferredRegister<T> deferredRegister(RegistryKey<?> registry) {
        return DeferredRegister.create(registry, id());
    }

    /** Opens a live view over a game registry. */
    default <T> RegistryView<T> registry(RegistryKey<?> registry) {
        return RegistryManager.registry(registry);
    }

    default <T> Optional<RegistryView<T>> findRegistry(RegistryKey<?> registry) {
        return RegistryManager.findRegistry(registry);
    }
}
