package net.weaver.api.command;

/**
 * A handle for one reference-counted permission node registration.
 *
 * <p>Registering the same node again with the same policy and required op
 * level increments a reference count and returns a separate handle. Each
 * handle may be unregistered once; unregistering is idempotent and decrements
 * the shared count. The registration is removed when the count reaches zero.
 *
 * <p>Implementations are thread-safe. A handle becomes inactive after its own
 * {@link #unregister()} call and also after {@link PermissionService#reset()}
 * clears the service state.
 */
public interface PermissionRegistration
{
    /**
     * The node this handle registered.
     *
     * @return the node, never {@code null}
     */
    PermissionNode node();

    /**
     * Whether this handle still contributes a reference to a live
     * registration.
     *
     * @return {@code true} while the handle is unregistered and the
     *         registration is still installed
     */
    boolean isActive();

    /**
     * Releases this handle's reference.
     *
     * <p>Idempotent: a second call has no effect. The underlying registration
     * is removed once the last reference is released. This method is
     * thread-safe.
     */
    void unregister();
}
