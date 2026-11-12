package net.weaver.core.command;

import net.weaver.api.command.PermissionResolver;

import java.util.UUID;

/**
 * Permission resolver backed by the 26.3 command source and permission-set
 * model, accessed entirely through reflection.
 *
 * <p>Starting with 26.3 the game no longer attaches an integer op level to a
 * command source. A {@code CommandSourceStack} instead exposes a
 * {@code PermissionSet} together with the {@code PermissionCheck} constants
 * {@code LEVEL_MODERATORS}, {@code LEVEL_GAMEMASTERS}, {@code LEVEL_ADMINS},
 * and {@code LEVEL_OWNERS} on {@code Commands}. Weaver keeps its stable
 * zero-through-four op scale so mods do not need to know about permission
 * nodes or vanilla permission constants. The resolver probes the check
 * constants from highest to lowest and maps the first match to 4 (owners),
 * 3 (admins), 2 (gamemasters), or 1 (moderators). A source without a
 * permission set, or a non-game source such as a test string, resolves to
 * op level zero.
 *
 * <p>The subject is normally the source entity so explicit Weaver decisions
 * follow the player or entity across command source wrappers. Sources without
 * an entity fall back to the source object itself. Every reflective failure is
 * contained: a missing class or method yields the identity subject and op
 * level zero rather than an exception.
 */
final class MinecraftPermissionResolver implements PermissionResolver
{
    /**
     * {@inheritDoc}
     */
    @Override
    public Object subject(Object source)
    {
        if (source == null)
        {
            return null;
        }
        try
        {
            GameReflection.Access access = GameReflection.forLoader(
                    source.getClass().getClassLoader());
            Object entity = access.entity(source);
            if (entity != null)
            {
                return entity;
            }
            Object player = access.player(source);
            if (player != null)
            {
                return player;
            }
        }
        catch (Throwable ignored)
        {
        }
        return source;
    }

    /**
     * {@inheritDoc}
     *
     * <p>An entity subject persists as {@code player:<uuid>} so the same
     * player keeps explicit decisions across restarts and wrapper changes.
     * Strings, UUIDs, and numbers keep the default {@code toString()} form;
     * a subject without a readable UUID is not persistable.
     */
    @Override
    public String subjectKey(Object subject)
    {
        if (subject == null)
        {
            return null;
        }
        if (subject instanceof String || subject instanceof UUID || subject instanceof Number)
        {
            return PermissionResolver.super.subjectKey(subject);
        }
        try
        {
            GameReflection.Access access = GameReflection.forLoader(
                    subject.getClass().getClassLoader());
            Object uuid = access.uuid(subject);
            if (uuid != null)
            {
                return "player:" + uuid;
            }
        }
        catch (Throwable ignored)
        {
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int opLevel(Object source)
    {
        if (source == null)
        {
            return 0;
        }
        try
        {
            GameReflection.Access access = GameReflection.forLoader(
                    source.getClass().getClassLoader());
            Object permissionSet = access.permissions(source);
            if (permissionSet == null)
            {
                return 0;
            }
            return access.opLevel(permissionSet);
        }
        catch (Throwable failure)
        {
            return 0;
        }
    }
}
