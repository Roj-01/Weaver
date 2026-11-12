package net.weaver.api.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable command invocation context.
 *
 * <p>The context carries the wrapped source, the raw command input, the parsed
 * argument map, and the opaque Brigadier context object. The argument map is
 * copied defensively and exposed as an unmodifiable map. Argument values keep
 * their original objects; convenience accessors convert them on demand.
 *
 * <p>Instances are immutable and thread-safe. The raw context and argument
 * values are game objects and remain subject to the server thread rules of the
 * game.
 */
public final class CommandContext
{
    private final CommandSource source;
    private final String input;
    private final Map<String, Object> arguments;
    private final Object rawContext;

    /**
     * Creates an invocation context.
     *
     * @param source     the wrapped command source, not {@code null}
     * @param input      the raw command input, not {@code null}
     * @param arguments  the parsed arguments, or {@code null} for no arguments;
     *                   the map is copied defensively
     * @param rawContext the raw Brigadier context, or {@code null}
     * @throws NullPointerException when {@code source} or {@code input} is
     *                              {@code null}
     */
    public CommandContext(CommandSource source, String input,
            Map<String, Object> arguments, Object rawContext)
    {
        this.source = Objects.requireNonNull(source, "source");
        this.input = Objects.requireNonNull(input, "input");
        this.arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        this.rawContext = rawContext;
    }

    /**
     * The wrapped command source.
     *
     * @return the source, never {@code null}
     */
    public CommandSource source()
    {
        return source;
    }

    /**
     * The raw command input.
     *
     * @return the input, never {@code null}
     */
    public String input()
    {
        return input;
    }

    /**
     * The raw Brigadier context.
     *
     * @return the raw context, or {@code null} when unavailable
     */
    public Object raw()
    {
        return rawContext;
    }

    /**
     * The parsed arguments.
     *
     * @return an unmodifiable argument map, never {@code null}
     */
    public Map<String, Object> arguments()
    {
        return arguments;
    }

    /**
     * Returns one parsed argument value.
     *
     * @param name the argument name, not {@code null}
     * @return the value, or {@code null} when the argument is absent or its
     *         value is {@code null}
     * @throws NullPointerException when {@code name} is {@code null}
     */
    public Object argument(String name)
    {
        Objects.requireNonNull(name, "name");
        return arguments.get(name);
    }

    /**
     * Returns one parsed argument as text.
     *
     * @param name the argument name, not {@code null}
     * @return the value converted with {@link String#valueOf(Object)}, or
     *         {@code null} when the argument is absent or its value is
     *         {@code null}
     * @throws NullPointerException when {@code name} is {@code null}
     */
    public String stringArgument(String name)
    {
        Objects.requireNonNull(name, "name");
        if (!arguments.containsKey(name))
        {
            return null;
        }
        Object value = arguments.get(name);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * Returns one parsed argument as text, when present.
     *
     * @param name the argument name, not {@code null}
     * @return the optional text, never {@code null}
     * @throws NullPointerException when {@code name} is {@code null}
     */
    public Optional<String> optionalStringArgument(String name)
    {
        return Optional.ofNullable(stringArgument(name));
    }

    /**
     * Returns one parsed argument as an integer.
     *
     * <p>{@link Number} values use {@link Number#intValue()}; string values are
     * trimmed and parsed with {@link Integer#parseInt(String)}.
     *
     * @param name the argument name, not {@code null}
     * @return the integer value
     * @throws NullPointerException     when {@code name} is {@code null}
     * @throws IllegalArgumentException when the argument is absent, not
     *                                  numeric, or not representable as an
     *                                  integer
     */
    public int intArgument(String name)
    {
        Objects.requireNonNull(name, "name");
        if (!arguments.containsKey(name))
        {
            throw new IllegalArgumentException("Missing command argument: " + name);
        }
        Object value = arguments.get(name);
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        if (value instanceof String text)
        {
            try
            {
                return Integer.parseInt(text.trim());
            }
            catch (NumberFormatException failure)
            {
                throw new IllegalArgumentException("Command argument '" + name
                        + "' is not an integer: " + text, failure);
            }
        }
        throw new IllegalArgumentException("Command argument '" + name
                + "' is not numeric: " + (value == null ? "null" : value.getClass().getName()));
    }
}
