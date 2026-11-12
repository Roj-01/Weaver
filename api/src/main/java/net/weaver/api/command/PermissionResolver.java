package net.weaver.api.command;

import java.util.UUID;

/**
 * Maps a raw command source to a permission subject and an op level.
 *
 * <p>The resolver is called by {@link PermissionService} on the server thread
 * and possibly on hot paths, for example while evaluating a command tree.
 * Implementations must be fast, allocation-light, and thread-safe. They must
 * not block, schedule work, or call back into the game in a way that assumes a
 * particular thread.
 *
 * <p>The {@code source} passed to a resolver is the raw object supplied by the
 * caller and may be {@code null}. {@link #subject(Object)} may also return
 * {@code null}; in that case {@link PermissionService} uses the raw source
 * itself as the subject. Decision subjects are compared with
 * {@link Object#equals(Object)}, so resolvers should return stable, immutable
 * subjects such as a player identifier rather than a mutable game object.
 */
public interface PermissionResolver
{
    /**
     * Returns the permission subject for a raw source.
     *
     * <p>Called on the server thread and possibly hot. Implementations must be
     * fast and thread-safe.
     *
     * @param source the raw source, possibly {@code null}
     * @return the subject, or {@code null} to use the raw source as the subject
     */
    Object subject(Object source);

    /**
     * Returns the op level of a raw source.
     *
     * <p>Called on the server thread and possibly hot. Implementations must be
     * fast and thread-safe.
     *
     * @param source the raw source, possibly {@code null}
     * @return the op level; values at or below zero mean no op privilege
     */
    int opLevel(Object source);

    /**
     * Returns a stable, persistable key for a resolved subject.
     *
     * <p>{@link PermissionService} uses this key to store decisions in a
     * {@link PermissionStore}. A {@code null} result means the subject cannot
     * be persisted; its decisions stay object-keyed and in memory only.
     *
     * <p>The default implementation persists strings, UUIDs, and numbers by
     * their {@link Object#toString()} form. Resolvers with richer subjects,
     * such as a game entity, should override this method and return a stable
     * identifier.
     *
     * @param subject the resolved subject, possibly {@code null}
     * @return the persistence key, or {@code null} when not persistable
     */
    default String subjectKey(Object subject)
    {
        if (subject instanceof String || subject instanceof UUID || subject instanceof Number)
        {
            return subject.toString();
        }
        return null;
    }

    /**
     * Returns the identity resolver: the source is its own subject and every
     * source has op level zero.
     *
     * @return the simple resolver, never {@code null}
     */
    static PermissionResolver simple()
    {
        return SimplePermissionResolver.INSTANCE;
    }
}

/**
 * Shared stateless identity resolver.
 */
final class SimplePermissionResolver implements PermissionResolver
{
    static final SimplePermissionResolver INSTANCE = new SimplePermissionResolver();

    private SimplePermissionResolver()
    {
    }

    @Override
    public Object subject(Object source)
    {
        return source;
    }

    @Override
    public int opLevel(Object source)
    {
        return 0;
    }
}
