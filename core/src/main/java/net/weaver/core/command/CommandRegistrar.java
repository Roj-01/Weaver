package net.weaver.core.command;

import net.weaver.api.command.CommandSpec;

import java.util.List;

/**
 * The small command-registration seam used by {@link CoreCommandBackend}.
 *
 * <p>Production uses {@link BrigadierRegistrar}. Tests inject a recording
 * implementation so backend lifecycle and scheduling can be verified without a
 * live Brigadier jar. Implementations must not throw from
 * {@link #isAvailable()} or {@link #commandNames(Object)}.
 */
interface CommandRegistrar
{
    /**
     * Whether the registrar can serve registrations.
     *
     * @return {@code true} when usable
     */
    boolean isAvailable();

    /**
     * Registers one spec with a dispatcher.
     *
     * @param dispatcher the raw dispatcher
     * @param spec       the command spec
     * @return the registered root node, or {@code null} when skipped
     */
    Object register(Object dispatcher, CommandSpec spec);

    /**
     * Registers one spec and returns every root node it owns.
     *
     * <p>The default implementation delegates to {@link #register(Object,
     * CommandSpec)} and reports the single returned root. Implementations that
     * also register aliases should override this method so a later rebuild can
     * detach the alias nodes by identity.
     *
     * @param dispatcher the raw dispatcher
     * @param spec       the command spec
     * @return the registered root nodes, never {@code null}
     */
    default List<Object> registerTree(Object dispatcher, CommandSpec spec)
    {
        Object root = register(dispatcher, spec);
        return root == null ? List.of() : List.of(root);
    }

    /**
     * Removes one root node previously returned by
     * {@link #registerTree(Object, CommandSpec)}.
     *
     * <p>Removal is by node identity, so a skipped vanilla conflict is never
     * detached by a rebuild.
     *
     * @param dispatcher the raw dispatcher
     * @param node       the root node to detach
     */
    default void removeNode(Object dispatcher, Object node)
    {
    }

    /**
     * Lists the root command names currently present in a dispatcher.
     *
     * @param dispatcher the raw dispatcher
     * @return the names, never {@code null}
     */
    List<String> commandNames(Object dispatcher);
}
