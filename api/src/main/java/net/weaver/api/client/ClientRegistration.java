package net.weaver.api.client;

/**
 * A handle for one client API registration.
 *
 * <p>A handle is returned by every {@link ClientRegistry} registration method.
 * It identifies the registration, reports whether it is still active, and
 * removes it. Handles are safe to use from any thread.
 */
public interface ClientRegistration
{
    /**
     * The stable id of the registration.
     *
     * <p>The backend chooses the exact value. Key mappings normally report the
     * mapping id, and other registrations report the id supplied by their
     * factory, layer, provider, or listener.
     *
     * @return the non-null registration id
     */
    String id();

    /**
     * Whether the registration is still installed and will still be invoked.
     *
     * <p>A registration becomes inactive after {@link #unregister()} or after
     * the backend is uninstalled or reset. This method is thread-safe.
     *
     * @return {@code true} while the registration is active
     */
    boolean isActive();

    /**
     * Removes the registration.
     *
     * <p>The call is idempotent and thread-safe: calling it more than once,
     * concurrently, or after the backend was uninstalled has no additional
     * effect. Removing an already inactive registration must not throw.
     */
    void unregister();
}
