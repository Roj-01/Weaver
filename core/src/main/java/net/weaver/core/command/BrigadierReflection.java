package net.weaver.core.command;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Cached reflective view of the Brigadier API for one class loader.
 *
 * <p>Brigadier is loaded from the game class path at runtime. The core module
 * compiles without it, so every type and method used by the command backend is
 * resolved here through {@link Class#forName(String, boolean, ClassLoader)}
 * and cached per loader. The cache stores an all-or-nothing view: when a class
 * or method is missing the view reports itself unavailable and callers fall
 * back to logging instead of failing hard.
 */
final class BrigadierReflection
{
    private static final String DISPATCHER = "com.mojang.brigadier.CommandDispatcher";
    private static final String LITERAL_BUILDER =
            "com.mojang.brigadier.builder.LiteralArgumentBuilder";
    private static final String REQUIRED_BUILDER =
            "com.mojang.brigadier.builder.RequiredArgumentBuilder";
    private static final String ARGUMENT_BUILDER =
            "com.mojang.brigadier.builder.ArgumentBuilder";
    private static final String COMMAND_NODE = "com.mojang.brigadier.tree.CommandNode";
    private static final String COMMAND = "com.mojang.brigadier.Command";
    private static final String ARGUMENT_TYPE = "com.mojang.brigadier.arguments.ArgumentType";
    private static final String COMMAND_CONTEXT = "com.mojang.brigadier.context.CommandContext";
    private static final String SUGGESTION_PROVIDER =
            "com.mojang.brigadier.suggestion.SuggestionProvider";
    private static final String SUGGESTIONS_BUILDER =
            "com.mojang.brigadier.suggestion.SuggestionsBuilder";
    private static final String SUGGESTIONS = "com.mojang.brigadier.suggestion.Suggestions";
    private static final String SUGGESTION = "com.mojang.brigadier.suggestion.Suggestion";

    private static final ClassLoader FALLBACK = BrigadierReflection.class.getClassLoader();
    private static final ConcurrentHashMap<ClassLoader, Access> CACHE =
            new ConcurrentHashMap<>();

    private BrigadierReflection()
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
     * The Brigadier classes and methods resolved for one loader.
     */
    static final class Access
    {
        private boolean available;
        private Class<?> dispatcherClass;
        private Class<?> literalBuilderClass;
        private Class<?> requiredBuilderClass;
        private Class<?> commandClass;
        private Class<?> argumentTypeClass;
        private Class<?> commandContextClass;
        private Class<?> suggestionProviderClass;
        private Class<?> suggestionsBuilderClass;
        private Class<?> suggestionsClass;
        private Class<?> suggestionClass;
        private Method literal;
        private Method argument;
        private Method executes;
        private Method requires;
        private Method then;
        private Method redirect;
        private Method build;
        private Method suggests;
        private Method register;
        private Method getRoot;
        private Method getChild;
        private Method getChildren;
        private Method getName;
        private Field childrenField;
        private Field literalsField;
        private Field argumentsField;
        private Method getSource;
        private Method getInput;
        private Method getArgument;
        private Method getCommand;
        private Method getRequirement;
        private Method getRemaining;
        private Method builderGetInput;
        private Method suggest;
        private Method buildFuture;
        private Method suggestionsGetList;
        private Method suggestionGetText;
        private Method suggestionsEmpty;
        private Method stringType;
        private Method wordType;
        private Method greedyStringType;
        private Method integerType;
        private Method longType;
        private Method doubleType;
        private Method floatType;
        private Method booleanType;

        private Access(ClassLoader preferred)
        {
            try
            {
                ClassLoader loader = resolveLoader(preferred);
                if (loader == null)
                {
                    return;
                }
                dispatcherClass = load(DISPATCHER, loader);
                literalBuilderClass = load(LITERAL_BUILDER, loader);
                requiredBuilderClass = load(REQUIRED_BUILDER, loader);
                Class<?> argumentBuilderClass = load(ARGUMENT_BUILDER, loader);
                Class<?> commandNodeClass = load(COMMAND_NODE, loader);
                commandClass = load(COMMAND, loader);
                argumentTypeClass = load(ARGUMENT_TYPE, loader);
                commandContextClass = load(COMMAND_CONTEXT, loader);
                suggestionProviderClass = load(SUGGESTION_PROVIDER, loader);
                suggestionsBuilderClass = load(SUGGESTIONS_BUILDER, loader);
                suggestionsClass = load(SUGGESTIONS, loader);
                suggestionClass = load(SUGGESTION, loader);

                literal = literalBuilderClass.getMethod("literal", String.class);
                argument = requiredBuilderClass.getMethod("argument", String.class,
                        argumentTypeClass);
                executes = argumentBuilderClass.getMethod("executes", commandClass);
                requires = argumentBuilderClass.getMethod("requires", Predicate.class);
                then = argumentBuilderClass.getMethod("then", commandNodeClass);
                redirect = argumentBuilderClass.getMethod("redirect", commandNodeClass);
                build = argumentBuilderClass.getMethod("build");
                suggests = requiredBuilderClass.getMethod("suggests", suggestionProviderClass);
                register = dispatcherClass.getMethod("register", literalBuilderClass);
                getRoot = dispatcherClass.getMethod("getRoot");
                getChild = commandNodeClass.getMethod("getChild", String.class);
                getChildren = commandNodeClass.getMethod("getChildren");
                getName = commandNodeClass.getMethod("getName");
                childrenField = optionalField(commandNodeClass, "children");
                literalsField = optionalField(commandNodeClass, "literals");
                argumentsField = optionalField(commandNodeClass, "arguments");
                getSource = commandContextClass.getMethod("getSource");
                getInput = commandContextClass.getMethod("getInput");
                getArgument = commandContextClass.getMethod("getArgument", String.class,
                        Class.class);
                getCommand = commandNodeClass.getMethod("getCommand");
                getRequirement = commandNodeClass.getMethod("getRequirement");
                getRemaining = suggestionsBuilderClass.getMethod("getRemaining");
                builderGetInput = suggestionsBuilderClass.getMethod("getInput");
                suggest = suggestionsBuilderClass.getMethod("suggest", String.class);
                buildFuture = suggestionsBuilderClass.getMethod("buildFuture");
                suggestionsGetList = suggestionsClass.getMethod("getList");
                suggestionGetText = suggestionClass.getMethod("getText");
                suggestionsEmpty = suggestionsClass.getMethod("empty");

                stringType = statement(loader,
                        "com.mojang.brigadier.arguments.StringArgumentType", "string");
                wordType = statement(loader,
                        "com.mojang.brigadier.arguments.StringArgumentType", "word");
                greedyStringType = statement(loader,
                        "com.mojang.brigadier.arguments.StringArgumentType", "greedyString");
                integerType = statement(loader,
                        "com.mojang.brigadier.arguments.IntegerArgumentType", "integer");
                longType = statement(loader,
                        "com.mojang.brigadier.arguments.LongArgumentType", "longArg");
                doubleType = statement(loader,
                        "com.mojang.brigadier.arguments.DoubleArgumentType", "doubleArg");
                floatType = statement(loader,
                        "com.mojang.brigadier.arguments.FloatArgumentType", "floatArg");
                booleanType = statement(loader,
                        "com.mojang.brigadier.arguments.BoolArgumentType", "bool");
                available = true;
            }
            catch (Throwable failure)
            {
                available = false;
            }
        }

        /**
         * Whether every class and method used by the backend was resolved.
         *
         * @return {@code true} when the view is usable
         */
        boolean isAvailable()
        {
            return available;
        }

        /**
         * The Brigadier {@code Command} interface class.
         *
         * @return the interface class
         */
        Class<?> commandClass()
        {
            return commandClass;
        }

        /**
         * The Brigadier {@code ArgumentType} interface class.
         *
         * @return the interface class
         */
        Class<?> argumentTypeClass()
        {
            return argumentTypeClass;
        }

        /**
         * The Brigadier {@code SuggestionProvider} interface class.
         *
         * @return the interface class
         */
        Class<?> suggestionProviderClass()
        {
            return suggestionProviderClass;
        }

        /**
         * Creates a new literal argument builder.
         *
         * @param name the literal name
         * @return the builder
         * @throws ReflectiveOperationException when the call fails
         */
        Object literal(String name) throws ReflectiveOperationException
        {
            return literal.invoke(null, name);
        }

        /**
         * Creates a new required argument builder.
         *
         * @param name the argument name
         * @param type the Brigadier argument type
         * @return the builder
         * @throws ReflectiveOperationException when the call fails
         */
        Object argument(String name, Object type) throws ReflectiveOperationException
        {
            return argument.invoke(null, name, type);
        }

        /**
         * Applies an executor to a builder.
         *
         * @param builder the builder
         * @param command the Brigadier command proxy
         * @throws ReflectiveOperationException when the call fails
         */
        void executes(Object builder, Object command) throws ReflectiveOperationException
        {
            executes.invoke(builder, command);
        }

        /**
         * Applies a requirement predicate to a builder.
         *
         * @param builder   the builder
         * @param predicate the predicate
         * @throws ReflectiveOperationException when the call fails
         */
        void requires(Object builder, Predicate<Object> predicate)
                throws ReflectiveOperationException
        {
            requires.invoke(builder, predicate);
        }

        /**
         * Adds a built child node to a builder.
         *
         * @param builder the builder
         * @param child   the child node
         * @throws ReflectiveOperationException when the call fails
         */
        void then(Object builder, Object child) throws ReflectiveOperationException
        {
            then.invoke(builder, child);
        }

        /**
         * Redirects a builder to an existing node.
         *
         * @param builder the builder
         * @param target  the redirect target node
         * @throws ReflectiveOperationException when the call fails
         */
        void redirect(Object builder, Object target) throws ReflectiveOperationException
        {
            redirect.invoke(builder, target);
        }

        /**
         * Builds a command node from a builder.
         *
         * @param builder the builder
         * @return the built node
         * @throws ReflectiveOperationException when the call fails
         */
        Object build(Object builder) throws ReflectiveOperationException
        {
            return build.invoke(builder);
        }

        /**
         * Applies a suggestion provider to an argument builder.
         *
         * @param builder  the builder
         * @param provider the Brigadier suggestion provider proxy
         * @throws ReflectiveOperationException when the call fails
         */
        void suggests(Object builder, Object provider) throws ReflectiveOperationException
        {
            suggests.invoke(builder, provider);
        }

        /**
         * Registers a root builder with a dispatcher.
         *
         * @param dispatcher the dispatcher
         * @param root       the root builder
         * @return the registered root node
         * @throws ReflectiveOperationException when the call fails
         */
        Object register(Object dispatcher, Object root) throws ReflectiveOperationException
        {
            return register.invoke(dispatcher, root);
        }

        /**
         * Reads a dispatcher's root node.
         *
         * @param dispatcher the dispatcher
         * @return the root node
         * @throws ReflectiveOperationException when the call fails
         */
        Object root(Object dispatcher) throws ReflectiveOperationException
        {
            return getRoot.invoke(dispatcher);
        }

        /**
         * Reads a node child by name.
         *
         * @param parent the parent node
         * @param name   the child name
         * @return the child, or {@code null}
         * @throws ReflectiveOperationException when the call fails
         */
        Object child(Object parent, String name) throws ReflectiveOperationException
        {
            return getChild.invoke(parent, name);
        }

        /**
         * Reads a node's children.
         *
         * @param parent the parent node
         * @return the children
         * @throws ReflectiveOperationException when the call fails
         */
        Collection<?> children(Object parent) throws ReflectiveOperationException
        {
            return (Collection<?>) getChildren.invoke(parent);
        }

        /**
         * Removes a child node by identity.
         *
         * <p>Brigadier returns the live map values view from
         * {@code CommandNode#getChildren()}, so the iterator's
         * {@code remove()} detaches the node without replacing the dispatcher.
         * Identity comparison means only a node Weaver registered is removed,
         * never a same-named vanilla or third-party node.
         *
         * @param parent the parent node
         * @param node   the child node
         * @return {@code true} when the node was removed
         * @throws ReflectiveOperationException when the call fails
         */
        boolean removeChildNode(Object parent, Object node) throws ReflectiveOperationException
        {
            if (parent == null || node == null)
            {
                return false;
            }
            String name = name(node);
            Object removed = null;
            Map<?, ?> children = childrenField == null ? null
                    : (Map<?, ?>) childrenField.get(parent);
            if (children != null)
            {
                removed = children.remove(name);
                if (removed != node)
                {
                    java.util.Iterator<? extends Map.Entry<?, ?>> iterator =
                            children.entrySet().iterator();
                    while (iterator.hasNext())
                    {
                        Map.Entry<?, ?> entry = iterator.next();
                        if (entry.getValue() == node)
                        {
                            iterator.remove();
                            removed = node;
                            break;
                        }
                    }
                }
            }
            else
            {
                java.util.Iterator<?> iterator = children(parent).iterator();
                while (iterator.hasNext())
                {
                    Object child = iterator.next();
                    if (child == node)
                    {
                        iterator.remove();
                        removed = node;
                        break;
                    }
                }
            }
            removeByName(literalsField, parent, name, node);
            removeByName(argumentsField, parent, name, node);
            return removed == node;
        }

        private static void removeByName(Field field, Object parent, String name, Object node)
                throws ReflectiveOperationException
        {
            if (field == null)
            {
                return;
            }
            Map<?, ?> map = (Map<?, ?>) field.get(parent);
            if (map != null && map.get(name) == node)
            {
                map.remove(name);
            }
        }

        private static Field optionalField(Class<?> owner, String name)
        {
            try
            {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (Throwable failure)
            {
                return null;
            }
        }

        /**
         * Reads a node's name.
         *
         * @param node the node
         * @return the name
         * @throws ReflectiveOperationException when the call fails
         */
        String name(Object node) throws ReflectiveOperationException
        {
            return (String) getName.invoke(node);
        }

        /**
         * Reads a command context's source.
         *
         * @param context the context
         * @return the source
         * @throws ReflectiveOperationException when the call fails
         */
        Object source(Object context) throws ReflectiveOperationException
        {
            return getSource.invoke(context);
        }

        /**
         * Reads a command context's input.
         *
         * @param context the context
         * @return the input
         * @throws ReflectiveOperationException when the call fails
         */
        String input(Object context) throws ReflectiveOperationException
        {
            return (String) getInput.invoke(context);
        }

        /**
         * Reads one parsed argument from a command context.
         *
         * @param context the context
         * @param name    the argument name
         * @return the argument value
         * @throws ReflectiveOperationException when the call fails
         */
        Object argumentValue(Object context, String name) throws ReflectiveOperationException
        {
            return getArgument.invoke(context, name, Object.class);
        }

        /**
         * Reads a node's executor.
         *
         * @param node the node
         * @return the Brigadier command, or {@code null}
         * @throws ReflectiveOperationException when the call fails
         */
        Object command(Object node) throws ReflectiveOperationException
        {
            return getCommand.invoke(node);
        }

        /**
         * Reads a node's requirement predicate.
         *
         * @param node the node
         * @return the predicate, or {@code null}
         * @throws ReflectiveOperationException when the call fails
         */
        @SuppressWarnings("unchecked")
        Predicate<Object> requirement(Object node) throws ReflectiveOperationException
        {
            return (Predicate<Object>) getRequirement.invoke(node);
        }

        /**
         * Reads the remaining input from a suggestions builder.
         *
         * @param builder the suggestions builder
         * @return the remaining input
         * @throws ReflectiveOperationException when the call fails
         */
        String remaining(Object builder) throws ReflectiveOperationException
        {
            return (String) getRemaining.invoke(builder);
        }

        /**
         * Reads the full input from a suggestions builder.
         *
         * @param builder the suggestions builder
         * @return the input
         * @throws ReflectiveOperationException when the call fails
         */
        String builderInput(Object builder) throws ReflectiveOperationException
        {
            return (String) builderGetInput.invoke(builder);
        }

        /**
         * Adds one suggestion to a suggestions builder.
         *
         * @param builder the suggestions builder
         * @param text    the suggestion text
         * @throws ReflectiveOperationException when the call fails
         */
        void suggest(Object builder, String text) throws ReflectiveOperationException
        {
            suggest.invoke(builder, text);
        }

        /**
         * Builds the suggestions future.
         *
         * @param builder the suggestions builder
         * @return the future
         * @throws ReflectiveOperationException when the call fails
         */
        Object buildFuture(Object builder) throws ReflectiveOperationException
        {
            return buildFuture.invoke(builder);
        }

        /**
         * Reads the suggestion list from a suggestions object.
         *
         * @param suggestions the suggestions object
         * @return the list
         * @throws ReflectiveOperationException when the call fails
         */
        List<?> suggestionList(Object suggestions) throws ReflectiveOperationException
        {
            return (List<?>) suggestionsGetList.invoke(suggestions);
        }

        /**
         * Reads one suggestion's text.
         *
         * @param suggestion the suggestion
         * @return the text
         * @throws ReflectiveOperationException when the call fails
         */
        String suggestionText(Object suggestion) throws ReflectiveOperationException
        {
            return (String) suggestionGetText.invoke(suggestion);
        }

        /**
         * Creates an empty suggestions future.
         *
         * @return the future
         * @throws ReflectiveOperationException when the call fails
         */
        Object emptySuggestions() throws ReflectiveOperationException
        {
            return suggestionsEmpty.invoke(null);
        }

        /**
         * Creates a Brigadier command interface proxy.
         *
         * @param handler the invocation handler
         * @return the proxy
         */
        Object commandProxy(java.lang.reflect.InvocationHandler handler)
        {
            return Proxy.newProxyInstance(commandClass.getClassLoader(),
                    new Class<?>[]{commandClass}, handler);
        }

        /**
         * Creates a Brigadier suggestion provider interface proxy.
         *
         * @param handler the invocation handler
         * @return the proxy
         */
        Object suggestionProxy(java.lang.reflect.InvocationHandler handler)
        {
            return Proxy.newProxyInstance(suggestionProviderClass.getClassLoader(),
                    new Class<?>[]{suggestionProviderClass}, handler);
        }

        /**
         * Creates a Brigadier string argument type.
         *
         * @return the argument type
         * @throws ReflectiveOperationException when the call fails
         */
        Object stringType() throws ReflectiveOperationException
        {
            return stringType.invoke(null);
        }

        /**
         * Creates a Brigadier word argument type.
         *
         * @return the argument type
         * @throws ReflectiveOperationException when the call fails
         */
        Object wordType() throws ReflectiveOperationException
        {
            return wordType.invoke(null);
        }

        /**
         * Creates a Brigadier greedy-string argument type.
         *
         * @return the argument type
         * @throws ReflectiveOperationException when the call fails
         */
        Object greedyStringType() throws ReflectiveOperationException
        {
            return greedyStringType.invoke(null);
        }

        /**
         * Creates a Brigadier integer argument type.
         *
         * @return the argument type
         * @throws ReflectiveOperationException when the call fails
         */
        Object integerType() throws ReflectiveOperationException
        {
            return integerType.invoke(null);
        }

        /**
         * Creates a Brigadier long argument type.
         *
         * @return the argument type
         * @throws ReflectiveOperationException when the call fails
         */
        Object longType() throws ReflectiveOperationException
        {
            return longType.invoke(null);
        }

        /**
         * Creates a Brigadier double argument type.
         *
         * @return the argument type
         * @throws ReflectiveOperationException when the call fails
         */
        Object doubleType() throws ReflectiveOperationException
        {
            return doubleType.invoke(null);
        }

        /**
         * Creates a Brigadier float argument type.
         *
         * @return the argument type
         * @throws ReflectiveOperationException when the call fails
         */
        Object floatType() throws ReflectiveOperationException
        {
            return floatType.invoke(null);
        }

        /**
         * Creates a Brigadier boolean argument type.
         *
         * @return the argument type
         * @throws ReflectiveOperationException when the call fails
         */
        Object booleanType() throws ReflectiveOperationException
        {
            return booleanType.invoke(null);
        }

        private static Method statement(ClassLoader loader, String className, String methodName)
                throws ReflectiveOperationException
        {
            return load(className, loader).getMethod(methodName);
        }

        private static Class<?> load(String className, ClassLoader loader)
                throws ClassNotFoundException
        {
            return Class.forName(className, false, loader);
        }

        private static ClassLoader resolveLoader(ClassLoader preferred)
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
                    Class.forName(DISPATCHER, false, candidate);
                    return candidate;
                }
                catch (Throwable ignored)
                {
                }
            }
            return null;
        }
    }
}
