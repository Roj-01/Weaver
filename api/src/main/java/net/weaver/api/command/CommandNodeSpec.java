package net.weaver.api.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable definition of one command tree node.
 *
 * <p>A node is either a literal or an argument. Literal nodes match a fixed
 * word; argument nodes parse input with a {@link CommandArgumentType}. Both may
 * have children and an executor. Suggestions are only valid on argument nodes.
 * A literal may redirect to a sibling literal instead of declaring an executor
 * or children; the redirect target is resolved by the core command backend when
 * the whole tree is built.
 *
 * <p>Permission metadata is carried but not enforced by this type. The core
 * registers the node with {@link PermissionService} using the node, policy, and
 * required op level supplied here. The default policy is
 * {@link PermissionPolicy#DENY} with op level zero, which denies access unless
 * an explicit subject decision applies.
 *
 * <p>Instances are immutable and thread-safe when the supplied executor and
 * suggestion provider are themselves thread-safe. The child list is copied
 * defensively and exposed as an immutable list.
 */
public final class CommandNodeSpec
{
    private final String name;
    private final boolean argument;
    private final CommandArgumentType argumentType;
    private final PermissionNode permission;
    private final PermissionPolicy permissionPolicy;
    private final int opLevel;
    private final CommandExecutor executor;
    private final CommandSuggestionProvider suggestions;
    private final String redirectTo;
    private final List<CommandNodeSpec> children;

    private CommandNodeSpec(Builder builder)
    {
        this.name = builder.name;
        this.argument = builder.argument;
        this.argumentType = builder.argumentType;
        this.permission = builder.permission;
        this.permissionPolicy = builder.permissionPolicy;
        this.opLevel = builder.opLevel;
        this.executor = builder.executor;
        this.suggestions = builder.suggestions;
        this.redirectTo = builder.redirectTo;
        this.children = List.copyOf(builder.children);
    }

    /**
     * Starts a literal node.
     *
     * @param name the literal name, not blank and containing no whitespace
     * @return a new builder, never {@code null}
     * @throws NullPointerException     when {@code name} is {@code null}
     * @throws IllegalArgumentException when {@code name} is blank or contains
     *                                  whitespace
     */
    public static Builder literal(String name)
    {
        return new Builder(validateName(name), false, null);
    }

    /**
     * Starts an argument node.
     *
     * @param name the argument name, not blank and containing no whitespace
     * @param type the argument type, not {@code null}
     * @return a new builder, never {@code null}
     * @throws NullPointerException     when {@code name} or {@code type} is
     *                                  {@code null}
     * @throws IllegalArgumentException when {@code name} is blank or contains
     *                                  whitespace
     */
    public static Builder argument(String name, CommandArgumentType type)
    {
        Objects.requireNonNull(type, "type");
        return new Builder(validateName(name), true, type);
    }

    /**
     * The node name.
     *
     * @return the name, never {@code null}
     */
    public String name()
    {
        return name;
    }

    /**
     * Whether this node is an argument node.
     *
     * @return {@code true} for an argument node, {@code false} for a literal
     */
    public boolean isArgument()
    {
        return argument;
    }

    /**
     * The argument type.
     *
     * @return the type for an argument node, or {@code null} for a literal
     */
    public CommandArgumentType argumentType()
    {
        return argumentType;
    }

    /**
     * The permission node associated with this node.
     *
     * @return the permission node, or {@code null} when none was configured
     */
    public PermissionNode permission()
    {
        return permission;
    }

    /**
     * The fallback permission policy.
     *
     * @return the policy, never {@code null}
     */
    public PermissionPolicy permissionPolicy()
    {
        return permissionPolicy;
    }

    /**
     * The required op level used by a deny policy.
     *
     * @return the required op level
     */
    public int opLevel()
    {
        return opLevel;
    }

    /**
     * The node executor.
     *
     * @return the executor, or {@code null} when the node has none
     */
    public CommandExecutor executor()
    {
        return executor;
    }

    /**
     * The suggestion provider.
     *
     * @return the provider, or {@code null} when the node has none
     */
    public CommandSuggestionProvider suggestions()
    {
        return suggestions;
    }

    /**
     * The sibling literal this node redirects to.
     *
     * @return the sibling name, or {@code null} when the node is not a
     *         redirect
     */
    public String redirectTo()
    {
        return redirectTo;
    }

    /**
     * The child nodes.
     *
     * @return an immutable child list, never {@code null}
     */
    public List<CommandNodeSpec> children()
    {
        return children;
    }

    private static String validateName(String name)
    {
        Objects.requireNonNull(name, "name");
        if (name.isBlank())
        {
            throw new IllegalArgumentException("Command node name must not be blank");
        }
        if (containsWhitespace(name))
        {
            throw new IllegalArgumentException(
                    "Command node name must not contain whitespace: " + name);
        }
        return name;
    }

    private static boolean containsWhitespace(String text)
    {
        for (int i = 0; i < text.length(); i++)
        {
            if (Character.isWhitespace(text.charAt(i)))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds a {@link CommandNodeSpec}.
     *
     * <p>Validation is performed by {@link #build()}: an argument node requires
     * a type, suggestions require an argument node, redirects require a literal
     * without an executor or children, and duplicate child names are rejected.
     */
    public static final class Builder
    {
        private final String name;
        private final boolean argument;
        private final CommandArgumentType argumentType;
        private final List<CommandNodeSpec> children = new ArrayList<>();
        private PermissionNode permission;
        private PermissionPolicy permissionPolicy = PermissionPolicy.DENY;
        private int opLevel;
        private CommandExecutor executor;
        private CommandSuggestionProvider suggestions;
        private String redirectTo;

        private Builder(String name, boolean argument, CommandArgumentType argumentType)
        {
            this.name = name;
            this.argument = argument;
            this.argumentType = argumentType;
        }

        /**
         * Sets the permission node, leaving the policy and op level unchanged.
         *
         * @param permission the permission node, not {@code null}
         * @return this builder
         * @throws NullPointerException when {@code permission} is {@code null}
         */
        public Builder permission(PermissionNode permission)
        {
            this.permission = Objects.requireNonNull(permission, "permission");
            return this;
        }

        /**
         * Sets the permission node, fallback policy, and required op level.
         *
         * @param permission the permission node, not {@code null}
         * @param policy     the fallback policy, not {@code null}
         * @param opLevel    the required op level for a deny policy
         * @return this builder
         * @throws NullPointerException when {@code permission} or {@code policy}
         *                              is {@code null}
         */
        public Builder permission(PermissionNode permission, PermissionPolicy policy,
                int opLevel)
        {
            this.permission = Objects.requireNonNull(permission, "permission");
            this.permissionPolicy = Objects.requireNonNull(policy, "policy");
            this.opLevel = opLevel;
            return this;
        }

        /**
         * Sets the node executor.
         *
         * @param executor the executor, not {@code null}
         * @return this builder
         * @throws NullPointerException when {@code executor} is {@code null}
         */
        public Builder executor(CommandExecutor executor)
        {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        /**
         * Sets the suggestion provider.
         *
         * <p>Suggestions are only valid on argument nodes; a literal node with
         * suggestions fails at {@link #build()}.
         *
         * @param suggestions the provider, not {@code null}
         * @return this builder
         * @throws NullPointerException when {@code suggestions} is {@code null}
         */
        public Builder suggests(CommandSuggestionProvider suggestions)
        {
            this.suggestions = Objects.requireNonNull(suggestions, "suggestions");
            return this;
        }

        /**
         * Redirects a literal node to a sibling literal.
         *
         * @param siblingName the sibling literal name, not blank and containing
         *                    no whitespace
         * @return this builder
         * @throws NullPointerException when {@code siblingName} is {@code null}
         */
        public Builder redirect(String siblingName)
        {
            this.redirectTo = Objects.requireNonNull(siblingName, "siblingName");
            return this;
        }

        /**
         * Adds child nodes.
         *
         * @param children the children to add, not {@code null}
         * @return this builder
         * @throws NullPointerException when {@code children} or one of its
         *                              entries is {@code null}
         */
        public Builder child(CommandNodeSpec... children)
        {
            Objects.requireNonNull(children, "children");
            for (CommandNodeSpec child : children)
            {
                this.children.add(Objects.requireNonNull(child, "child"));
            }
            return this;
        }

        /**
         * Validates the definition and builds an immutable node spec.
         *
         * @return the node spec, never {@code null}
         * @throws IllegalArgumentException when the definition is inconsistent
         */
        public CommandNodeSpec build()
        {
            if (argument && argumentType == null)
            {
                throw new IllegalArgumentException(
                        "Argument node '" + name + "' requires an argument type");
            }
            if (!argument && argumentType != null)
            {
                throw new IllegalArgumentException(
                        "Literal node '" + name + "' must not have an argument type");
            }
            if (redirectTo != null)
            {
                if (argument)
                {
                    throw new IllegalArgumentException(
                            "Argument node '" + name + "' cannot redirect");
                }
                if (executor != null)
                {
                    throw new IllegalArgumentException("Redirecting node '" + name
                            + "' cannot also have an executor");
                }
                if (!children.isEmpty())
                {
                    throw new IllegalArgumentException("Redirecting node '" + name
                            + "' cannot also have children");
                }
                if (redirectTo.isBlank() || containsWhitespace(redirectTo))
                {
                    throw new IllegalArgumentException(
                            "Redirect target must be non-blank and contain no whitespace: "
                                    + redirectTo);
                }
            }
            if (suggestions != null && !argument)
            {
                throw new IllegalArgumentException("Literal node '" + name
                        + "' cannot have suggestions; suggestions require an argument node");
            }
            Set<String> childNames = new HashSet<>();
            for (CommandNodeSpec child : children)
            {
                if (!childNames.add(child.name()))
                {
                    throw new IllegalArgumentException("Duplicate child node name '"
                            + child.name() + "' under '" + name + "'");
                }
            }
            return new CommandNodeSpec(this);
        }
    }
}
