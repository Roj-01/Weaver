package net.weaver.api.config;

/**
 * Signals a failure while reading, writing, or backing up a configuration
 * file.
 *
 * <p>{@link #corrupt()} distinguishes unparseable content from transient
 * storage problems such as a missing directory or a failed atomic move. The
 * manager treats corrupt content as recoverable: it backs the file up and
 * applies defaults instead of propagating the failure from a load.
 */
public class ConfigStoreException extends ConfigException
{
    private final boolean corrupt;

    /**
     * Creates a non-corrupt storage failure.
     *
     * @param message the detail message
     */
    public ConfigStoreException(String message)
    {
        this(message, null, false);
    }

    /**
     * Creates a non-corrupt storage failure with a cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ConfigStoreException(String message, Throwable cause)
    {
        this(message, cause, false);
    }

    /**
     * Creates a storage failure with an explicit corruption flag.
     *
     * @param message the detail message
     * @param corrupt whether the stored content is unparseable
     */
    public ConfigStoreException(String message, boolean corrupt)
    {
        this(message, null, corrupt);
    }

    /**
     * Creates a storage failure with a cause and an explicit corruption flag.
     *
     * @param message the detail message
     * @param cause   the underlying cause, may be {@code null}
     * @param corrupt whether the stored content is unparseable
     */
    public ConfigStoreException(String message, Throwable cause, boolean corrupt)
    {
        super(message, cause);
        this.corrupt = corrupt;
    }

    /**
     * Whether the stored content could not be parsed.
     *
     * @return {@code true} when the content is corrupt
     */
    public boolean corrupt()
    {
        return corrupt;
    }

    /**
     * Creates a corrupt-content failure.
     *
     * @param message the detail message
     * @return a corrupt store exception
     */
    public static ConfigStoreException corrupt(String message)
    {
        return new ConfigStoreException(message, null, true);
    }

    /**
     * Creates a corrupt-content failure with a cause.
     *
     * @param message the detail message
     * @param cause   the underlying parse failure
     * @return a corrupt store exception
     */
    public static ConfigStoreException corrupt(String message, Throwable cause)
    {
        return new ConfigStoreException(message, cause, true);
    }

    /**
     * Creates a non-corrupt storage failure.
     *
     * @param message the detail message
     * @param cause   the underlying I/O failure
     * @return an I/O store exception
     */
    public static ConfigStoreException io(String message, Throwable cause)
    {
        return new ConfigStoreException(message, cause, false);
    }
}
