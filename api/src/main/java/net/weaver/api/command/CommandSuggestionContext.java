package net.weaver.api.command;

import java.util.Objects;

/**
 * An immutable context passed to a {@link CommandSuggestionProvider}.
 *
 * <p>The context contains the invocation context, the full current input, the
 * remaining unparsed input after the cursor position, and the opaque raw
 * Brigadier suggestion context. Instances are immutable and thread-safe. The
 * raw context and any game objects reachable through the command context remain
 * subject to the game's threading rules.
 */
public final class CommandSuggestionContext
{
    private final CommandContext commandContext;
    private final String input;
    private final String remaining;
    private final Object rawContext;

    /**
     * Creates a suggestion context.
     *
     * @param commandContext the invocation context, not {@code null}
     * @param input          the full current input, not {@code null}
     * @param remaining      the remaining unparsed input, not {@code null}
     * @param rawContext     the raw Brigadier suggestion context, or
     *                       {@code null}
     * @throws NullPointerException when {@code commandContext}, {@code input},
     *                              or {@code remaining} is {@code null}
     */
    public CommandSuggestionContext(CommandContext commandContext, String input,
            String remaining, Object rawContext)
    {
        this.commandContext = Objects.requireNonNull(commandContext, "commandContext");
        this.input = Objects.requireNonNull(input, "input");
        this.remaining = Objects.requireNonNull(remaining, "remaining");
        this.rawContext = rawContext;
    }

    /**
     * The invocation context.
     *
     * @return the command context, never {@code null}
     */
    public CommandContext commandContext()
    {
        return commandContext;
    }

    /**
     * The full current command input.
     *
     * @return the input, never {@code null}
     */
    public String input()
    {
        return input;
    }

    /**
     * The remaining unparsed input at the cursor.
     *
     * @return the remaining input, never {@code null}
     */
    public String remaining()
    {
        return remaining;
    }

    /**
     * The raw Brigadier suggestion context.
     *
     * @return the raw context, or {@code null} when unavailable
     */
    public Object rawContext()
    {
        return rawContext;
    }
}
