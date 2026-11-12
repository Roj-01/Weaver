package net.weaver.api.command;

/**
 * Signals a controlled command API failure.
 *
 * <p>This is the base type for command availability and registration failures.
 * A {@link CommandExecutor} may also throw it to report a controlled execution
 * failure.
 */
public class CommandException extends RuntimeException
{
    /**
     * Creates an exception with a message.
     *
     * @param message the detail message
     */
    public CommandException(String message)
    {
        super(message);
    }

    /**
     * Creates an exception with a message and cause.
     *
     * @param message the detail message
     * @param cause   the cause, or {@code null}
     */
    public CommandException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
