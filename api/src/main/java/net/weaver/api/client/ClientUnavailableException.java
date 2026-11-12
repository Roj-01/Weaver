package net.weaver.api.client;

/**
 * Signals that no usable client backend is available.
 *
 * <p>This is thrown when client API methods are called on a dedicated server,
 * when the client game classes are absent, or when the reflective resolution of
 * {@code net.weaver.core.client.CoreClientBackend} fails.
 */
public class ClientUnavailableException extends ClientException
{
    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    public ClientUnavailableException(String message)
    {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the resolution failure
     */
    public ClientUnavailableException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
