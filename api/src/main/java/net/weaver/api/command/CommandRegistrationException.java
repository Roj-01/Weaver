package net.weaver.api.command;

/**
 * Signals that a command specification cannot be registered.
 *
 * <p>Typical causes are a duplicate root name, an alias conflict, an invalid
 * redirect target reported by the backend, or a backend that is shutting down.
 */
public final class CommandRegistrationException extends CommandException
{
    /**
     * Creates an exception with a message.
     *
     * @param message the detail message
     */
    public CommandRegistrationException(String message)
    {
        super(message);
    }

    /**
     * Creates an exception with a message and cause.
     *
     * @param message the detail message
     * @param cause   the cause, or {@code null}
     */
    public CommandRegistrationException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
