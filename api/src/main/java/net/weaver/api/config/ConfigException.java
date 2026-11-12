package net.weaver.api.config;

/**
 * Base type for every failure raised by the Weaver configuration API.
 *
 * <p>Subtypes distinguish caller mistakes ({@link ConfigValidationException}),
 * storage failures ({@link ConfigStoreException}), and general configuration
 * errors. The exception is unchecked so configuration code can run from normal
 * gameplay callbacks without checked-exception plumbing.
 */
public class ConfigException extends RuntimeException
{
    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    public ConfigException(String message)
    {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ConfigException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
