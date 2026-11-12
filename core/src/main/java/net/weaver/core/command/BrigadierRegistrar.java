package net.weaver.core.command;

import net.weaver.api.command.CommandContext;
import net.weaver.api.command.CommandExecutor;
import net.weaver.api.command.CommandNodeSpec;
import net.weaver.api.command.CommandRegistrationException;
import net.weaver.api.command.CommandSource;
import net.weaver.api.command.CommandSpec;
import net.weaver.api.command.CommandSuggestionContext;
import net.weaver.api.command.CommandSuggestionProvider;
import net.weaver.api.command.PermissionNode;
import net.weaver.api.command.PermissionService;
import net.weaver.core.LifecycleHooks;

import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Maps Weaver command specs onto a live Brigadier dispatcher by reflection.
 *
 * <p>The registrar builds a Brigadier tree in three passes: it builds sibling
 * nodes first so redirects can point at a real node object, then adds the
 * built children to their parent builder, and finally registers the completed
 * root builder. Executors and suggestion providers are Java proxies because
 * the core module does not compile against Brigadier. Every callback contains
 * its own failures: an executor returns zero, a suggestion provider returns an
 * empty future, and a build failure is reported to the backend for logging.
 *
 * <p>Root conflicts are skipped, not thrown. When the dispatcher already has a
 * literal with the requested root or alias name, the spec is left out and a
 * {@code COMMAND CONFLICT} line is logged so other specs can still register.
 *
 * <p>{@link #removeNode(Object, Object)} detaches one owned root node through
 * the live map values view returned by Brigadier, so a rebuild can re-register
 * the remaining active specs without replacing the dispatcher. Removal is by
 * identity, so a skipped vanilla conflict is never detached.
 */
final class BrigadierRegistrar implements CommandRegistrar
{
    private final ClassLoader loader;

    /**
     * Creates a registrar.
     *
     * @param loader the preferred Brigadier class loader, usually the
     *               dispatcher's loader; may be {@code null}
     */
    BrigadierRegistrar(ClassLoader loader)
    {
        this.loader = loader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAvailable()
    {
        try
        {
            return BrigadierReflection.forLoader(loader).isAvailable();
        }
        catch (Throwable failure)
        {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object register(Object dispatcher, CommandSpec spec)
    {
        List<Object> nodes = registerTree(dispatcher, spec);
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Object> registerTree(Object dispatcher, CommandSpec spec)
    {
        if (dispatcher == null)
        {
            throw new CommandRegistrationException("Command dispatcher is null");
        }
        if (spec == null)
        {
            throw new CommandRegistrationException("Command spec is null");
        }
        try
        {
            return registerInternal(dispatcher, spec);
        }
        catch (CommandRegistrationException failure)
        {
            throw failure;
        }
        catch (Throwable failure)
        {
            throw new CommandRegistrationException(
                    "Failed to register command " + spec.name(), failure);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeNode(Object dispatcher, Object node)
    {
        if (dispatcher == null || node == null)
        {
            return;
        }
        try
        {
            BrigadierReflection.Access access = accessFor(dispatcher);
            if (!access.isAvailable())
            {
                throw new CommandRegistrationException(
                        "Brigadier is not available to the command registrar");
            }
            access.removeChildNode(access.root(dispatcher), node);
        }
        catch (CommandRegistrationException failure)
        {
            throw failure;
        }
        catch (Throwable failure)
        {
            throw new CommandRegistrationException(
                    "Failed to remove a command node", failure);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> commandNames(Object dispatcher)
    {
        if (dispatcher == null)
        {
            return List.of();
        }
        try
        {
            BrigadierReflection.Access access = accessFor(dispatcher);
            if (!access.isAvailable())
            {
                return List.of();
            }
            Collection<?> children = access.children(access.root(dispatcher));
            List<String> names = new ArrayList<>(children.size());
            for (Object child : children)
            {
                String name = access.name(child);
                if (name != null)
                {
                    names.add(name);
                }
            }
            return List.copyOf(names);
        }
        catch (Throwable failure)
        {
            return List.of();
        }
    }

    private List<Object> registerInternal(Object dispatcher, CommandSpec spec) throws Throwable
    {
        BrigadierReflection.Access access = accessFor(dispatcher);
        if (!access.isAvailable())
        {
            throw new CommandRegistrationException(
                    "Brigadier is not available to the command registrar");
        }
        Object root = access.root(dispatcher);
        if (access.child(root, spec.name()) != null)
        {
            logConflict(spec.name());
            return List.of();
        }
        List<String> argumentNames = argumentNames(spec);
        Object rootBuilder = access.literal(spec.name());
        if (spec.executor() != null)
        {
            access.executes(rootBuilder, commandProxy(access, spec, null, argumentNames,
                    spec.executor()));
        }
        if (spec.permission() != null)
        {
            access.requires(rootBuilder, requirement(spec.permission()));
        }
        buildChildren(access, spec, rootBuilder, argumentNames, spec.children());
        List<Object> nodes = new ArrayList<>();
        Object rootNode = access.register(dispatcher, rootBuilder);
        nodes.add(rootNode);
        for (String alias : spec.aliases())
        {
            if (access.child(root, alias) != null)
            {
                logConflict(alias);
                continue;
            }
            Object aliasBuilder = access.literal(alias);
            copyNodeBehaviour(access, aliasBuilder, rootNode);
            access.redirect(aliasBuilder, rootNode);
            try
            {
                nodes.add(access.register(dispatcher, aliasBuilder));
            }
            catch (Throwable failure)
            {
                logLine("[weaver] COMMAND ALIAS REGISTER FAILED name=" + alias
                        + " reason=" + failure);
            }
        }
        return List.copyOf(nodes);
    }

    private void buildChildren(BrigadierReflection.Access access, CommandSpec rootSpec,
            Object parentBuilder, List<String> argumentNames, List<CommandNodeSpec> children)
            throws Throwable
    {
        if (children.isEmpty())
        {
            return;
        }
        Map<String, CommandNodeSpec> siblings = new LinkedHashMap<>();
        for (CommandNodeSpec child : children)
        {
            siblings.put(child.name(), child);
        }
        Map<String, Object> built = new LinkedHashMap<>();
        Set<String> building = new HashSet<>();
        for (CommandNodeSpec child : children)
        {
            Object childNode = buildNode(access, rootSpec, argumentNames, child, siblings, built,
                    building);
            access.then(parentBuilder, childNode);
        }
    }

    private Object buildNode(BrigadierReflection.Access access, CommandSpec rootSpec,
            List<String> argumentNames, CommandNodeSpec node,
            Map<String, CommandNodeSpec> siblings, Map<String, Object> built, Set<String> building)
            throws Throwable
    {
        Object existing = built.get(node.name());
        if (existing != null)
        {
            return existing;
        }
        if (!building.add(node.name()))
        {
            throw new CommandRegistrationException(
                    "Redirect cycle at command node '" + node.name() + "'");
        }
        try
        {
            Object builder;
            if (node.redirectTo() != null)
            {
                CommandNodeSpec targetSpec = siblings.get(node.redirectTo());
                if (targetSpec == null)
                {
                    throw new CommandRegistrationException("Missing redirect target '"
                            + node.redirectTo() + "' for command node '" + node.name() + "'");
                }
                if (targetSpec.isArgument())
                {
                    throw new CommandRegistrationException("Redirect target '"
                            + node.redirectTo() + "' for command node '" + node.name()
                            + "' is not a literal");
                }
                Object targetNode = buildNode(access, rootSpec, argumentNames, targetSpec, siblings,
                        built, building);
                builder = access.literal(node.name());
                copyNodeBehaviour(access, builder, targetNode,
                        node.permission() == null ? null : requirement(node.permission()));
                access.redirect(builder, targetNode);
            }
            else
            {
                if (node.isArgument())
                {
                    Object argumentType = ArgumentTypeMapper.map(access, node.argumentType());
                    builder = access.argument(node.name(), argumentType);
                    if (node.suggestions() != null)
                    {
                        access.suggests(builder, suggestionProxy(access, rootSpec, node,
                                argumentNames, node.suggestions()));
                    }
                }
                else
                {
                    builder = access.literal(node.name());
                }
                if (node.executor() != null)
                {
                    access.executes(builder, commandProxy(access, rootSpec, node, argumentNames,
                            node.executor()));
                }
                if (node.permission() != null)
                {
                    access.requires(builder, requirement(node.permission()));
                }
                buildChildren(access, rootSpec, builder, argumentNames, node.children());
            }
            Object builtNode = access.build(builder);
            built.put(node.name(), builtNode);
            return builtNode;
        }
        finally
        {
            building.remove(node.name());
        }
    }

    /**
     * Copies a target node's executor and requirement onto a redirect node.
     *
     * <p>Brigadier follows a redirect only when more input remains after the
     * redirecting literal. A bare alias such as {@code /p} would otherwise have
     * no command of its own and fail with "Unknown command". Copying the target
     * behavior keeps the short form usable while the redirect still forwards
     * longer inputs to the target subtree.
     */
    private static void copyNodeBehaviour(BrigadierReflection.Access access, Object builder,
            Object target) throws ReflectiveOperationException
    {
        copyNodeBehaviour(access, builder, target, null);
    }

    /**
     * Copies a target node's executor and requirement onto a redirect node,
     * combining the requirement with the redirect node's own requirement.
     *
     * <p>A redirecting node that declares its own permission must keep
     * enforcing it: the resulting requirement is the redirect node's own
     * requirement AND the target's requirement, so a privileged literal that
     * redirects to an unprivileged sibling is still gated.
     */
    private static void copyNodeBehaviour(BrigadierReflection.Access access, Object builder,
            Object target, Predicate<Object> own) throws ReflectiveOperationException
    {
        Object command = access.command(target);
        if (command != null)
        {
            access.executes(builder, command);
        }
        Predicate<Object> targetRequirement = access.requirement(target);
        Predicate<Object> combined;
        if (own == null)
        {
            combined = targetRequirement;
        }
        else if (targetRequirement == null)
        {
            combined = own;
        }
        else
        {
            combined = source -> own.test(source) && targetRequirement.test(source);
        }
        if (combined != null)
        {
            access.requires(builder, combined);
        }
    }

    private Predicate<Object> requirement(PermissionNode node)
    {
        return source ->
        {
            try
            {
                return PermissionService.isAllowed(source, node);
            }
            catch (Throwable failure)
            {
                logLine("[weaver] COMMAND PERMISSION FAILED node=" + node + " reason=" + failure);
                return false;
            }
        };
    }

    private Object commandProxy(BrigadierReflection.Access access, CommandSpec rootSpec,
            CommandNodeSpec node, List<String> argumentNames, CommandExecutor executor)
    {
        InvocationHandler handler = (proxy, method, arguments) ->
        {
            if (method.getDeclaringClass() == Object.class)
            {
                return objectMethod(proxy, method, arguments);
            }
            if (!"run".equals(method.getName()) || arguments == null || arguments.length != 1)
            {
                return defaultValue(method.getReturnType());
            }
            try
            {
                CommandContext context = buildContext(access, arguments[0], argumentNames);
                return executor.execute(context);
            }
            catch (Throwable failure)
            {
                logLine("[weaver] COMMAND EXECUTE FAILED name=" + rootSpec.name() + " node="
                        + nodeName(node) + " reason=" + failure);
                return 0;
            }
        };
        return access.commandProxy(handler);
    }

    private Object suggestionProxy(BrigadierReflection.Access access, CommandSpec rootSpec,
            CommandNodeSpec node, List<String> argumentNames,
            CommandSuggestionProvider provider)
    {
        InvocationHandler handler = (proxy, method, arguments) ->
        {
            if (method.getDeclaringClass() == Object.class)
            {
                return objectMethod(proxy, method, arguments);
            }
            if (!"getSuggestions".equals(method.getName()) || arguments == null
                    || arguments.length != 2)
            {
                return emptySuggestions(access, null);
            }
            Object suggestionsBuilder = arguments[1];
            try
            {
                CommandContext context = buildContext(access, arguments[0], argumentNames);
                String remaining = access.remaining(suggestionsBuilder);
                String input = access.builderInput(suggestionsBuilder);
                CommandSuggestionContext suggestionContext = new CommandSuggestionContext(context,
                        input, remaining, arguments[0]);
                List<String> suggestions = provider.suggest(suggestionContext);
                if (suggestions != null)
                {
                    for (String suggestion : suggestions)
                    {
                        if (suggestion != null)
                        {
                            access.suggest(suggestionsBuilder, suggestion);
                        }
                    }
                }
                return access.buildFuture(suggestionsBuilder);
            }
            catch (Throwable failure)
            {
                logLine("[weaver] COMMAND SUGGEST FAILED name=" + rootSpec.name() + " node="
                        + nodeName(node) + " reason=" + failure);
                return emptySuggestions(access, suggestionsBuilder);
            }
        };
        return access.suggestionProxy(handler);
    }

    private CommandContext buildContext(BrigadierReflection.Access access, Object rawContext,
            List<String> argumentNames) throws Throwable
    {
        Object rawSource = access.source(rawContext);
        if (rawSource == null)
        {
            throw new CommandRegistrationException("Command context source is unavailable");
        }
        CommandSource source = CommandSourceFactory.create(rawSource);
        String input = access.input(rawContext);
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (String name : argumentNames)
        {
            try
            {
                Object value = access.argumentValue(rawContext, name);
                if (value != null)
                {
                    arguments.put(name, value);
                }
            }
            catch (Throwable ignored)
            {
            }
        }
        return new CommandContext(source, input == null ? "" : input, arguments, rawContext);
    }

    private Object emptySuggestions(BrigadierReflection.Access access, Object builder)
    {
        if (builder != null)
        {
            try
            {
                return access.buildFuture(builder);
            }
            catch (Throwable ignored)
            {
            }
        }
        try
        {
            return access.emptySuggestions();
        }
        catch (Throwable failure)
        {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    private BrigadierReflection.Access accessFor(Object dispatcher)
    {
        BrigadierReflection.Access access = BrigadierReflection.forLoader(loader);
        if (access.isAvailable() || dispatcher == null)
        {
            return access;
        }
        return BrigadierReflection.forLoader(dispatcher.getClass().getClassLoader());
    }

    private static List<String> argumentNames(CommandSpec spec)
    {
        Set<String> names = new LinkedHashSet<>();
        collectArgumentNames(spec.children(), names);
        return List.copyOf(names);
    }

    private static void collectArgumentNames(List<CommandNodeSpec> nodes, Set<String> names)
    {
        for (CommandNodeSpec node : nodes)
        {
            if (node.isArgument())
            {
                names.add(node.name());
            }
            collectArgumentNames(node.children(), names);
        }
    }

    private static String nodeName(CommandNodeSpec node)
    {
        return node == null ? "root" : node.name();
    }

    private static Object objectMethod(Object proxy, java.lang.reflect.Method method,
            Object[] arguments)
    {
        return switch (method.getName())
        {
            case "toString" -> "WeaverCommandHandler@" + Integer.toHexString(
                    System.identityHashCode(proxy));
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
            default -> null;
        };
    }

    private static Object defaultValue(Class<?> type)
    {
        if (type == int.class || type == Integer.class)
        {
            return 0;
        }
        if (type == long.class || type == Long.class)
        {
            return 0L;
        }
        if (type == boolean.class || type == Boolean.class)
        {
            return false;
        }
        if (type == double.class || type == Double.class)
        {
            return 0.0D;
        }
        if (type == float.class || type == Float.class)
        {
            return 0.0F;
        }
        if (type == short.class || type == Short.class)
        {
            return (short) 0;
        }
        if (type == byte.class || type == Byte.class)
        {
            return (byte) 0;
        }
        if (type == char.class || type == Character.class)
        {
            return (char) 0;
        }
        return null;
    }

    private static void logConflict(String name)
    {
        logLine("[weaver] COMMAND CONFLICT name=" + name + " reason=existing");
    }

    private static void logLine(String message)
    {
        try
        {
            LifecycleHooks.logLine(message);
        }
        catch (Throwable ignored)
        {
        }
    }
}
