package net.weaver.api.command;

import java.util.List;

/**
 * Service provider interface implemented by the core command backend.
 *
 * <p>The backend owns the live Brigadier command tree and translates Weaver
 * command specs into it. It is internal mapping code: mods use
 * {@link CommandRegistry} and never implement this interface. The API module
 * keeps every argument and return value opaque so it has no compile-time
 * dependency on the game or Brigadier.
 *
 * <p>Threading: all methods may be called from any thread unless a method
 * documents otherwise. Implementations must be thread-safe and must not block
 * for long periods. The backend may internally schedule work onto the server
 * thread; callers never need to do so.
 */
public interface CommandBackend
{
    /**
     * Whether the backend can currently serve command registrations.
     *
     * <p>Called from any thread and must be fast. Must not throw or block.
     *
     * @return {@code true} when the backend is usable
     */
    boolean isAvailable();

    /**
     * Installs the backend.
     *
     * <p>Called once by the loader on the loader thread. Implementations
     * should make repeated calls idempotent. May throw a {@link CommandException}
     * when installation fails.
     */
    void install();

    /**
     * Uninstalls the backend and releases command state.
     *
     * <p>Called by the loader during shutdown on the loader thread.
     * Implementations should make repeated calls idempotent and must not throw
     * when already uninstalled.
     */
    void uninstall();

    /**
     * Registers a command specification.
     *
     * <p>Called from any thread, commonly during mod initialization. The
     * backend must be safe for concurrent registrations. A backend may queue
     * the spec and apply it on the server thread when a dispatcher is next
     * built.
     *
     * @param spec the specification, not {@code null}
     * @return a registration handle, never {@code null}
     * @throws CommandUnavailableException  when the backend is not available
     * @throws CommandRegistrationException when the spec cannot be accepted,
     *                                      for example because the name is taken
     */
    CommandRegistration register(CommandSpec spec);

    /**
     * Returns the raw game dispatcher.
     *
     * <p>Usually called on the server thread. The returned object is opaque to
     * the API; callers may cast it to the game type. A backend that has not
     * built a dispatcher may return {@code null}.
     *
     * @return the raw dispatcher, or {@code null}
     */
    Object dispatcher();

    /**
     * Returns the command names currently present in the backend.
     *
     * <p>Called from any thread. The returned list must be a snapshot; the
     * caller treats it as read-only. An unavailable backend returns an empty
     * list rather than {@code null}.
     *
     * @return the active command names, never {@code null}
     */
    List<String> commandNames();

    /**
     * Resends the current command tree to online players.
     *
     * <p>Called after a late registration has been applied and after a live
     * removal has rebuilt the tree. Implementations should treat the operation
     * as best-effort: a player without a connection, a missing game packet
     * type, or a send failure must be logged at most once and must never throw
     * back into the command registration path.
     *
     * <p>The default implementation does nothing so third-party backends stay
     * source and binary compatible.
     *
     * @param dispatcher the raw dispatcher, possibly {@code null}
     * @param players    the target players, or {@code null} to use the
     *                   backend's online player list
     * @param names      the command names at resend time, or {@code null}
     */
    default void resendTree(Object dispatcher, List<Object> players, List<String> names)
    {
    }

    /**
     * Rebuilds the live command tree from the active registrations.
     *
     * <p>Called when a registration is marked inactive while a dispatcher is
     * known. Implementations rebuild the Weaver portion of the tree from the
     * active specs, then resend it to online players. As with
     * {@link #resendTree(Object, List, List)}, failures must be isolated and
     * must never throw into {@link CommandRegistration#unregister()}.
     *
     * <p>The default implementation does nothing so third-party backends stay
     * source and binary compatible.
     *
     * @param dispatcher the raw dispatcher, possibly {@code null}
     * @param players    the target players, or {@code null} to use the
     *                   backend's online player list
     */
    default void rebuildTree(Object dispatcher, List<Object> players)
    {
    }
}
