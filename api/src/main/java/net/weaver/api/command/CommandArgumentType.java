package net.weaver.api.command;

import java.util.Objects;

/**
 * An immutable description of a command argument type.
 *
 * <p>The API does not compile against Brigadier. Built-in argument kinds are
 * represented by shared constants. A mod that needs an exact Brigadier argument
 * type, such as an entity selector or a resource location, wraps the raw game
 * object with {@link #custom(Object)}. The core command backend maps these
 * descriptions onto live Brigadier argument types.
 *
 * <p>Instances are immutable and thread-safe. Custom types are compared by
 * {@link Object#equals(Object)}; a raw Brigadier argument type should have
 * stable equality or be a singleton.
 */
public final class CommandArgumentType
{
    /**
     * The supported argument kinds.
     */
    public enum Kind
    {
        /**
         * A quoted or unquoted string argument.
         */
        STRING,

        /**
         * A single unquoted word argument.
         */
        WORD,

        /**
         * A greedy string that consumes the rest of the input.
         */
        GREEDY_STRING,

        /**
         * A 32-bit signed integer argument.
         */
        INTEGER,

        /**
         * A 64-bit signed integer argument.
         */
        LONG,

        /**
         * A double-precision floating-point argument.
         */
        DOUBLE,

        /**
         * A single-precision floating-point argument.
         */
        FLOAT,

        /**
         * A boolean argument.
         */
        BOOLEAN,

        /**
         * A caller-supplied raw game argument type.
         */
        CUSTOM
    }

    /**
     * A Brigadier string argument.
     */
    public static final CommandArgumentType STRING = new CommandArgumentType(Kind.STRING, null);

    /**
     * A Brigadier word argument.
     */
    public static final CommandArgumentType WORD = new CommandArgumentType(Kind.WORD, null);

    /**
     * A Brigadier greedy-string argument.
     */
    public static final CommandArgumentType GREEDY_STRING =
            new CommandArgumentType(Kind.GREEDY_STRING, null);

    /**
     * A Brigadier integer argument.
     */
    public static final CommandArgumentType INTEGER = new CommandArgumentType(Kind.INTEGER, null);

    /**
     * A Brigadier long argument.
     */
    public static final CommandArgumentType LONG = new CommandArgumentType(Kind.LONG, null);

    /**
     * A Brigadier double argument.
     */
    public static final CommandArgumentType DOUBLE = new CommandArgumentType(Kind.DOUBLE, null);

    /**
     * A Brigadier float argument.
     */
    public static final CommandArgumentType FLOAT = new CommandArgumentType(Kind.FLOAT, null);

    /**
     * A Brigadier boolean argument.
     */
    public static final CommandArgumentType BOOLEAN =
            new CommandArgumentType(Kind.BOOLEAN, null);

    private final Kind kind;
    private final Object customType;

    private CommandArgumentType(Kind kind, Object customType)
    {
        this.kind = kind;
        this.customType = customType;
    }

    /**
     * Wraps a raw Brigadier argument type.
     *
     * @param brigadierArgumentType the raw argument type, not {@code null}
     * @return a custom argument type, never {@code null}
     * @throws NullPointerException when {@code brigadierArgumentType} is
     *                              {@code null}
     */
    public static CommandArgumentType custom(Object brigadierArgumentType)
    {
        return new CommandArgumentType(Kind.CUSTOM,
                Objects.requireNonNull(brigadierArgumentType, "brigadierArgumentType"));
    }

    /**
     * The argument kind.
     *
     * @return the kind, never {@code null}
     */
    public Kind kind()
    {
        return kind;
    }

    /**
     * The raw Brigadier argument type for a custom argument.
     *
     * @return the raw type, or {@code null} for a built-in kind
     */
    public Object customType()
    {
        return customType;
    }

    /**
     * Compares this argument type with another object by kind and custom type.
     *
     * @param other the object to compare
     * @return {@code true} when the kinds and custom types are equal
     */
    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof CommandArgumentType type))
        {
            return false;
        }
        return kind == type.kind && Objects.equals(customType, type.customType);
    }

    /**
     * Returns a hash code derived from the kind and custom type.
     *
     * @return the hash code
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(kind, customType);
    }

    /**
     * Returns a diagnostic representation of this argument type.
     *
     * @return the representation, never {@code null}
     */
    @Override
    public String toString()
    {
        return kind == Kind.CUSTOM
                ? "CommandArgumentType{custom=" + customType.getClass().getName() + "}"
                : "CommandArgumentType{" + kind + "}";
    }
}
