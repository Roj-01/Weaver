package net.weaver.api.client;

/**
 * Base type for failures raised by the Weaver client extension API.
 *
 * <p>The exception is unchecked so client code can run from ordinary gameplay
 * and render callbacks without checked-exception plumbing.
 * {@link ClientUnavailableException} reports a missing or unusable client
 * backend, and {@link ClientThreadException} reports a call made from the
 * wrong thread.
 */
public class ClientException extends RuntimeException
{
    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    public ClientException(String message)
    {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ClientException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
