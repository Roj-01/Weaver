package net.weaver.api.command;

/**
 * Signals that the command backend is not present or cannot be resolved.
 *
 * <p>{@link CommandRegistry} throws this type when Brigadier or the core
 * command backend is absent, or when the backend cannot be instantiated.
 */
public final class CommandUnavailableException extends CommandException
{
    /**
     * Creates an exception with a message.
     *
     * @param message the detail message
     */
    public CommandUnavailableException(String message)
    {
        super(message);
    }

    /**
     * Creates an exception with a message and cause.
     *
     * @param message the detail message
     * @param cause   the cause, or {@code null}
     */
    public CommandUnavailableException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
