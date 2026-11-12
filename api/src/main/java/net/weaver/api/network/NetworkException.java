package net.weaver.api.network;

/**
 * Base type for failures raised by the Weaver networking API.
 *
 * <p>The exception is unchecked so networking code can run from ordinary
 * gameplay callbacks without checked-exception plumbing. Subtypes distinguish
 * general transport failures from registration conflicts
 * ({@link NetworkRegistrationException}).</p>
 */
public class NetworkException extends RuntimeException
{
    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    public NetworkException(String message)
    {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public NetworkException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
