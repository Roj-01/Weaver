package net.weaver.api.network;

/**
 * Signals that a payload registration conflicts with an existing registration.
 *
 * <p>A single {@link NetworkRegistry} accepts at most one registration for each
 * combination of direction, phase, and channel. Registering the same channel
 * twice is almost always a mod id or version mistake, so the registry fails
 * eagerly instead of silently replacing the first registration.</p>
 */
public final class NetworkRegistrationException extends NetworkException
{
    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    public NetworkRegistrationException(String message)
    {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public NetworkRegistrationException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
