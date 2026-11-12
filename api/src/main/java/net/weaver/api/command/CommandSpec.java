package net.weaver.api.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An immutable root command definition.
 *
 * <p>A command spec carries the root literal name, an optional mod id and
 * description, aliases, permission metadata, an optional root executor,
 * optional root suggestions, and child nodes. It is a data model only: the
 * core command backend maps it onto a live Brigadier command tree and is
 * responsible for registering the permission nodes.
 *
 * <p>The root name must be non-blank, contain no whitespace, and use only
 * lowercase letters, digits, {@code '_'}, {@code '-'}, {@code '/'}, or
 * {@code ':'}; uppercase letters are rejected. Aliases must be non-blank,
 * contain no whitespace, differ from the root name, and be unique. The alias
 * list and child list are immutable.
 *
 * <p>The default permission policy is {@link PermissionPolicy#DENY} with op
 * level zero, which denies access unless an explicit subject decision applies.
 * Root suggestions are permitted, but Brigadier may ignore suggestions on a
 * literal root that has no argument children.
 *
 * <p>Instances are immutable and thread-safe when the supplied executor and
 * suggestion provider are themselves thread-safe.
 */
public final class CommandSpec
{
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_\\-/:]+");

    private final String name;
    private final String modId;
    private final String description;
    private final PermissionNode permission;
    private final PermissionPolicy permissionPolicy;
    private final int opLevel;
    private final List<String> aliases;
    private final CommandExecutor executor;
    private final CommandSuggestionProvider suggestions;
    private final List<CommandNodeSpec> children;

    private CommandSpec(Builder builder)
    {
        this.name = builder.name;
        this.modId = builder.modId;
        this.description = builder.description;
        this.permission = builder.permission;
        this.permissionPolicy = builder.permissionPolicy;
        this.opLevel = builder.opLevel;
        this.aliases = List.copyOf(builder.aliases);
        this.executor = builder.executor;
        this.suggestions = builder.suggestions;
        this.children = List.copyOf(builder.children);
    }

    /**
     * Starts a root command.
     *
     * @param name the root name, not blank and matching
     *             {@code [a-z0-9_/:\\-]+}
     * @return a new builder, never {@code null}
     * @throws NullPointerException     when {@code name} is {@code null}
     * @throws IllegalArgumentException when the name is blank, contains
     *                                  whitespace, or contains characters other
     *                                  than lowercase letters, digits,
     *                                  {@code '_'}, {@code '-'}, {@code '/'},
     *                                  or {@code ':'}
     */
    public static Builder command(String name)
    {
        return new Builder(validateName(name));
    }

    /**
     * Alias for {@link #command(String)}.
     *
     * @param name the root name, not blank and matching
     *             {@code [a-z0-9_/:\\-]+}
     * @return a new builder, never {@code null}
     * @throws NullPointerException     when {@code name} is {@code null}
     * @throws IllegalArgumentException when the name is invalid
     */
    public static Builder literal(String name)
    {
        return command(name);
    }

    /**
     * The root command name.
     *
     * @return the name, never {@code null}
     */
    public String name()
    {
        return name;
    }

    /**
     * The owning mod id.
     *
     * @return the mod id, or an empty string when none was configured
     */
    public String modId()
    {
        return modId;
    }

    /**
     * The human-readable description.
     *
     * @return the description, or an empty string when none was configured
     */
    public String description()
    {
        return description;
    }

    /**
     * The permission node for the root command.
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
     * The command aliases.
     *
     * @return an immutable alias list, never {@code null}
     */
    public List<String> aliases()
    {
        return aliases;
    }

    /**
     * The root executor.
     *
     * @return the executor, or {@code null} when the root has none
     */
    public CommandExecutor executor()
    {
        return executor;
    }

    /**
     * The root suggestion provider.
     *
     * @return the provider, or {@code null} when the root has none
     */
    public CommandSuggestionProvider suggestions()
    {
        return suggestions;
    }

    /**
     * The root child nodes.
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
            throw new IllegalArgumentException("Command name must not be blank");
        }
        if (!NAME_PATTERN.matcher(name).matches())
        {
            throw new IllegalArgumentException("Command name must use only lowercase "
                    + "letters, digits, '_', '-', '/', or ':': " + name);
        }
        return name;
    }

    /**
     * Builds a {@link CommandSpec}.
     *
     * <p>Aliases and children are validated by {@link #build()}: aliases must
     * be non-blank, contain no whitespace, differ from the root name, and be
     * unique; duplicate child names are rejected.
     */
    public static final class Builder
    {
        private final String name;
        private final List<String> aliases = new ArrayList<>();
        private final List<CommandNodeSpec> children = new ArrayList<>();
        private String modId = "";
        private String description = "";
        private PermissionNode permission;
        private PermissionPolicy permissionPolicy = PermissionPolicy.DENY;
        private int opLevel;
        private CommandExecutor executor;
        private CommandSuggestionProvider suggestions;

        private Builder(String name)
        {
            this.name = name;
        }

        /**
         * Sets the owning mod id.
         *
         * @param modId the mod id, or an empty string for none; not
         *              {@code null}
         * @return this builder
         * @throws NullPointerException when {@code modId} is {@code null}
         */
        public Builder modId(String modId)
        {
            this.modId = Objects.requireNonNull(modId, "modId");
            return this;
        }

        /**
         * Sets the human-readable description.
         *
         * @param description the description, not {@code null}
         * @return this builder
         * @throws NullPointerException when {@code description} is {@code null}
         */
        public Builder description(String description)
        {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        /**
         * Sets the root permission node, leaving the policy and op level
         * unchanged.
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
         * Sets the root permission node, fallback policy, and required op
         * level.
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
         * Sets the required op level for a deny policy.
         *
         * @param opLevel the required op level
         * @return this builder
         */
        public Builder opLevel(int opLevel)
        {
            this.opLevel = opLevel;
            return this;
        }

        /**
         * Adds aliases.
         *
         * @param aliases the aliases to add, not {@code null}
         * @return this builder
         * @throws NullPointerException when {@code aliases} or one of its
         *                              entries is {@code null}
         */
        public Builder alias(String... aliases)
        {
            Objects.requireNonNull(aliases, "aliases");
            for (String alias : aliases)
            {
                this.aliases.add(Objects.requireNonNull(alias, "alias"));
            }
            return this;
        }

        /**
         * Sets the root executor.
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
         * Sets the root suggestion provider.
         *
         * <p>The root is a literal, so Brigadier may ignore these suggestions
         * unless an argument child can consume them.
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
         * Adds root child nodes.
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
         * Validates the definition and builds an immutable command spec.
         *
         * @return the command spec, never {@code null}
         * @throws IllegalArgumentException when an alias or child definition is
         *                                  invalid
         */
        public CommandSpec build()
        {
            Set<String> seenAliases = new HashSet<>();
            for (String alias : aliases)
            {
                if (alias.isBlank())
                {
                    throw new IllegalArgumentException("Command alias must not be blank");
                }
                if (containsWhitespace(alias))
                {
                    throw new IllegalArgumentException(
                            "Command alias must not contain whitespace: " + alias);
                }
                if (alias.equals(name))
                {
                    throw new IllegalArgumentException(
                            "Command alias must differ from the command name: " + alias);
                }
                if (!seenAliases.add(alias))
                {
                    throw new IllegalArgumentException("Duplicate command alias: " + alias);
                }
            }
            Set<String> childNames = new HashSet<>();
            for (CommandNodeSpec child : children)
            {
                if (!childNames.add(child.name()))
                {
                    throw new IllegalArgumentException("Duplicate root child node name: "
                            + child.name());
                }
            }
            return new CommandSpec(this);
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
    }
}
