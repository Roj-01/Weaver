package net.weaver.core.data;

/**
 * Signals that capability bytes could not be read from or written to an item
 * stack, or that the requested data violates the hard limits in
 * {@link ItemDataLimits}.
 */
public final class ItemDataException extends RuntimeException
{
    /**
     * Creates an exception with a descriptive message.
     *
     * @param message failure description
     */
    public ItemDataException(String message)
    {
        super(message);
    }

    /**
     * Creates an exception with a descriptive message and an underlying cause.
     *
     * @param message failure description
     * @param cause   underlying failure, or {@code null}
     */
    public ItemDataException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
