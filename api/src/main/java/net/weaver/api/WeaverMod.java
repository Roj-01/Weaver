package net.weaver.api;

/**
 * Lifecycle contract implemented by a mod entrypoint.
 *
 * <p>Every method has a no-op default so a mod only overrides the phases it
 * needs. The loader calls each phase once, in order, on the game thread.
 */
public interface WeaverMod {

    /** Early phase, before the game registries are populated. */
    default void onInitialize(ModContext context) {
    }

    /** Called immediately before the built-in registries are frozen. */
    default void onRegister(ModContext context) {
    }

    /** Common setup after the registries are frozen. */
    default void onCommonSetup(ModContext context) {
    }

    /** Client-only setup after the client exists. */
    default void onClientSetup(ModContext context) {
    }

    /** Dedicated-server setup after the server starts. */
    default void onServerSetup(ModContext context) {
    }

    /** Called once after the side setup completes. */
    default void onLoadComplete(ModContext context) {
    }

    /** The server is about to initialize. */
    default void onServerStarting(ModContext context) {
    }

    /** The server finished initialization. */
    default void onServerStarted(ModContext context) {
    }

    /** The server is shutting down. */
    default void onServerStopping(ModContext context) {
    }

    /** The server stopped. */
    default void onServerStopped(ModContext context) {
    }

    /** Called at the start of every client tick. */
    default void onClientTick(ModContext context, long tick) {
    }

    /** Called at the start of every server tick. */
    default void onServerTick(ModContext context, long tick) {
    }

    /** A player joined the server. */
    default void onPlayerJoin(ModContext context, Object player) {
    }

    /** A player left the server. */
    default void onPlayerLeave(ModContext context, Object player) {
    }

    /** A level was created or loaded. */
    default void onLevelLoad(ModContext context, Object level) {
    }

    /** A level is being unloaded. */
    default void onLevelUnload(ModContext context, Object level) {
    }

    /** A resource reload started or finished. */
    default void onResourceReload(ModContext context, Object source, boolean client) {
    }

    /** The command dispatcher was rebuilt. */
    default void onCommandRegister(ModContext context, Object dispatcher) {
    }

    /** A client screen is being shown. */
    default void onScreenOpen(ModContext context, Object screen) {
    }

    /** A client screen is being replaced or closed. */
    default void onScreenClose(ModContext context, Object screen) {
    }

    /** A player chat message is about to be broadcast. */
    default void onChat(ModContext context, Object message, Object sender) {
    }

    /** A server-side player tick started. */
    default void onPlayerTick(ModContext context, Object player) {
    }

    /** The server save started or finished. */
    default void onServerSave(ModContext context, boolean start) {
    }

    /** A server level save started or finished. */
    default void onLevelSave(ModContext context, Object level, boolean start) {
    }
}
