package net.weaver.api.client;

/**
 * Signals that a client API call was made from the wrong thread.
 *
 * <p>The client backend throws this when a registration, screen, HUD, or
 * tooltip operation that must run on the client/render thread is invoked from
 * another thread.
 */
public class ClientThreadException extends ClientException
{
    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    public ClientThreadException(String message)
    {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ClientThreadException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
