package net.weaver.core.command;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Cached reflective view of the game command-source classes.
 *
 * <p>The core command backend must not compile against {@code net.minecraft}.
 * This helper resolves the small surface it needs from the source object's own
 * class loader: source naming, feedback delivery, entity/player subjects, and
 * the permission-set accessors introduced in 26.3. Missing classes or methods
 * are treated as unavailable so non-game sources degrade to identity subjects,
 * op level zero, and no feedback.
 */
final class GameReflection
{
    private static final String COMMAND_SOURCE = "net.minecraft.commands.CommandSourceStack";
    private static final String COMPONENT = "net.minecraft.network.chat.Component";
    private static final String COMMANDS = "net.minecraft.commands.Commands";
    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String MINECRAFT_SERVER = "net.minecraft.server.MinecraftServer";
    private static final String PLAYER_LIST = "net.minecraft.server.players.PlayerList";
    private static final String PERMISSION_CHECK =
            "net.minecraft.server.permissions.PermissionCheck";

    private static final ClassLoader FALLBACK = GameReflection.class.getClassLoader();
    private static final ConcurrentHashMap<ClassLoader, Access> CACHE =
            new ConcurrentHashMap<>();

    private GameReflection()
    {
    }

    /**
     * Returns the cached reflective view for a loader.
     *
     * @param loader the preferred loader, or {@code null} for the core loader
     * @return the cached view, never {@code null}
     */
    static Access forLoader(ClassLoader loader)
    {
        ClassLoader key = loader == null ? FALLBACK : loader;
        return CACHE.computeIfAbsent(key, Access::new);
    }

    /**
     * The game classes and methods resolved for one loader.
     */
    static final class Access
    {
        private final Method getTextName;
        private final Method getDisplayName;
        private final Method componentGetString;
        private final Method componentLiteral;
        private final Method sendSuccess;
        private final Method sendFailure;
        private final Method sendSystemMessage;
        private final Method getEntity;
        private final Method getPlayer;
        private final Method getUuid;
        private final Method getPlayerList;
        private final Method getPlayers;
        private final Method permissions;
        private final Method permissionCheck;
        private final Object[] permissionLevels;
        private final int[] opLevels;

        private Access(ClassLoader preferred)
        {
            Class<?> componentClass = load(COMPONENT, preferred);
            Class<?> sourceClass = load(COMMAND_SOURCE, preferred);
            Class<?> entityClass = load(ENTITY, preferred);
            Class<?> serverClass = load(MINECRAFT_SERVER, preferred);
            Class<?> playerListClass = load(PLAYER_LIST, preferred);
            Class<?> permissionCheckClass = load(PERMISSION_CHECK, preferred);
            Class<?> commandsClass = load(COMMANDS, preferred);

            getTextName = method(sourceClass, "getTextName");
            getDisplayName = method(sourceClass, "getDisplayName");
            componentGetString = method(componentClass, "getString");
            componentLiteral = staticMethod(componentClass, "literal", String.class);
            sendSuccess = method(sourceClass, "sendSuccess", Supplier.class, boolean.class);
            sendFailure = method(sourceClass, "sendFailure", componentClass);
            sendSystemMessage = method(sourceClass, "sendSystemMessage", componentClass);
            getEntity = method(sourceClass, "getEntity");
            getPlayer = method(sourceClass, "getPlayer");
            getUuid = method(entityClass, "getUUID");
            getPlayerList = method(serverClass, "getPlayerList");
            getPlayers = method(playerListClass, "getPlayers");
            permissions = method(sourceClass, "permissions");
            permissionCheck = method(permissionCheckClass, "check",
                    load("net.minecraft.server.permissions.PermissionSet", preferred));

            Object owners = staticField(commandsClass, "LEVEL_OWNERS");
            Object admins = staticField(commandsClass, "LEVEL_ADMINS");
            Object gamemasters = staticField(commandsClass, "LEVEL_GAMEMASTERS");
            Object moderators = staticField(commandsClass, "LEVEL_MODERATORS");
            permissionLevels = new Object[]{owners, admins, gamemasters, moderators};
            opLevels = new int[]{4, 3, 2, 1};
        }

        /**
         * Returns the diagnostic source name.
         *
         * @param source the raw source
         * @return the name, never {@code null}
         */
        String sourceName(Object source)
        {
            if (source == null)
            {
                return "unknown";
            }
            if (getTextName != null)
            {
                try
                {
                    Object name = getTextName.invoke(source);
                    if (name != null)
                    {
                        return String.valueOf(name);
                    }
                }
                catch (Throwable ignored)
                {
                }
            }
            if (getDisplayName != null && componentGetString != null)
            {
                try
                {
                    Object display = getDisplayName.invoke(source);
                    if (display != null)
                    {
                        Object name = componentGetString.invoke(display);
                        if (name != null)
                        {
                            return String.valueOf(name);
                        }
                    }
                }
                catch (Throwable ignored)
                {
                }
            }
            return source.getClass().getSimpleName();
        }

        /**
         * Builds the success feedback consumer for a source.
         *
         * @param source the raw source
         * @return a consumer, or {@code null} when the source has no feedback
         */
        Consumer<String> successConsumer(Object source)
        {
            if (source == null)
            {
                return null;
            }
            if (sendSuccess != null && componentLiteral != null)
            {
                return message -> sendSuccess(source, message);
            }
            return systemMessageConsumer(source);
        }

        /**
         * Builds the failure feedback consumer for a source.
         *
         * @param source the raw source
         * @return a consumer, or {@code null} when the source has no feedback
         */
        Consumer<String> failureConsumer(Object source)
        {
            if (source == null)
            {
                return null;
            }
            if (sendFailure != null && componentLiteral != null)
            {
                return message -> sendFailure(source, message);
            }
            return systemMessageConsumer(source);
        }

        /**
         * Returns the source entity, when available.
         *
         * @param source the raw source
         * @return the entity, or {@code null}
         */
        Object entity(Object source)
        {
            return invoke(getEntity, source);
        }

        /**
         * Returns the source player, when available.
         *
         * @param source the raw source
         * @return the player, or {@code null}
         */
        Object player(Object source)
        {
            return invoke(getPlayer, source);
        }

        /**
         * Returns the entity UUID, when available.
         *
         * @param entity the entity subject
         * @return the UUID object, or {@code null}
         */
        Object uuid(Object entity)
        {
            return invoke(getUuid, entity);
        }

        /**
         * Returns the online players of a server.
         *
         * @param server the server instance
         * @return the player list, never {@code null}
         */
        List<Object> players(Object server)
        {
            Object playerList = invoke(getPlayerList, server);
            Object values = invoke(getPlayers, playerList);
            if (!(values instanceof Collection<?> collection))
            {
                return List.of();
            }
            List<Object> players = new ArrayList<>(collection.size());
            for (Object player : collection)
            {
                if (player != null)
                {
                    players.add(player);
                }
            }
            return List.copyOf(players);
        }

        /**
         * Returns the source's permission set, when available.
         *
         * @param source the raw source
         * @return the permission set, or {@code null}
         */
        Object permissions(Object source)
        {
            return invoke(permissions, source);
        }

        /**
         * Returns the highest matching game permission level.
         *
         * @param permissionSet the source permission set
         * @return zero through four
         */
        int opLevel(Object permissionSet)
        {
            if (permissionSet == null || permissionCheck == null)
            {
                return 0;
            }
            for (int index = 0; index < permissionLevels.length; index++)
            {
                Object check = permissionLevels[index];
                if (check == null)
                {
                    continue;
                }
                try
                {
                    Object result = permissionCheck.invoke(check, permissionSet);
                    if (Boolean.TRUE.equals(result))
                    {
                        return opLevels[index];
                    }
                }
                catch (Throwable ignored)
                {
                }
            }
            return 0;
        }

        private void sendSuccess(Object source, String message)
        {
            try
            {
                Object component = literal(message);
                Supplier<Object> supplier = () -> component;
                sendSuccess.invoke(source, supplier, false);
            }
            catch (Throwable failure)
            {
                sendSystemMessage(source, message);
            }
        }

        private void sendFailure(Object source, String message)
        {
            try
            {
                sendFailure.invoke(source, literal(message));
            }
            catch (Throwable failure)
            {
                sendSystemMessage(source, message);
            }
        }

        private Consumer<String> systemMessageConsumer(Object source)
        {
            if (sendSystemMessage != null && componentLiteral != null)
            {
                return message -> sendSystemMessage(source, message);
            }
            return null;
        }

        private void sendSystemMessage(Object source, String message)
        {
            if (sendSystemMessage == null || componentLiteral == null)
            {
                return;
            }
            try
            {
                sendSystemMessage.invoke(source, literal(message));
            }
            catch (Throwable ignored)
            {
            }
        }

        private Object literal(String message) throws ReflectiveOperationException
        {
            return componentLiteral.invoke(null, message == null ? "" : message);
        }

        private static Object invoke(Method method, Object target)
        {
            if (method == null || target == null)
            {
                return null;
            }
            try
            {
                return method.invoke(target);
            }
            catch (Throwable failure)
            {
                return null;
            }
        }

        private static Method method(Class<?> type, String name, Class<?>... parameters)
        {
            if (type == null)
            {
                return null;
            }
            try
            {
                return type.getMethod(name, parameters);
            }
            catch (Throwable failure)
            {
                return null;
            }
        }

        private static Method staticMethod(Class<?> type, String name, Class<?>... parameters)
        {
            return method(type, name, parameters);
        }

        private static Object staticField(Class<?> type, String name)
        {
            if (type == null)
            {
                return null;
            }
            try
            {
                Field field = type.getField(name);
                return field.get(null);
            }
            catch (Throwable failure)
            {
                return null;
            }
        }

        private static Class<?> load(String className, ClassLoader preferred)
        {
            ClassLoader[] candidates = {
                preferred,
                FALLBACK,
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
            };
            for (ClassLoader candidate : candidates)
            {
                if (candidate == null)
                {
                    continue;
                }
                try
                {
                    return Class.forName(className, false, candidate);
                }
                catch (Throwable ignored)
                {
                }
            }
            return null;
        }
    }
}
