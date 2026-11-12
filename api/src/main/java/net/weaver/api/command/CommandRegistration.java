package net.weaver.api.command;

/**
 * A handle for one registered command specification.
 *
 * <p>The core backend builds command trees from the active specs in a
 * dispatcher. {@link #unregister()} marks this registration inactive and
 * prevents the spec from being included in future builds. When a dispatcher is
 * already known, the core backend also rebuilds the live tree from the
 * remaining active specs, detaches this command's root and aliases, records a
 * new tree snapshot, and resends the tree to online players. The removal is
 * best-effort: if the rebuild cannot run, the logical inactive state is kept
 * and the failure is logged once.
 *
 * <p>Implementations are thread-safe. {@link #unregister()} is idempotent.
 */
public interface CommandRegistration
{
    /**
     * The registered specification.
     *
     * @return the spec, never {@code null}
     */
    CommandSpec spec();

    /**
     * Whether this registration still contributes its spec to future
     * dispatcher builds.
     *
     * @return {@code true} while the registration is active
     */
    boolean isActive();

    /**
     * Marks this registration inactive and, when a dispatcher is already
     * known, rebuilds the live tree without this command.
     *
     * <p>Idempotent. The logical inactive state is immediate even when the
     * best-effort live rebuild fails. This method is thread-safe.
     */
    void unregister();
}
