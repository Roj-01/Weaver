package net.weaver.api.config;

import java.util.Objects;

/**
 * Raised when a configuration value fails validation.
 *
 * <p>The message is always formatted as
 * {@code config '<modId>' key '<key>': <reason>} so callers can show it
 * directly in logs or diagnostics.
 */
public final class ConfigValidationException extends ConfigException
{
    private final String modId;
    private final String key;
    private final String reason;

    /**
     * Creates a validation failure without an underlying cause.
     *
     * @param modId  the mod that owns the configuration, not {@code null}
     * @param key    the dotted configuration key, not {@code null}
     * @param reason the actionable explanation, not {@code null}
     */
    public ConfigValidationException(String modId, String key, String reason)
    {
        super(formatMessage(modId, key, reason));
        this.modId = modId;
        this.key = key;
        this.reason = reason;
    }

    /**
     * Creates a validation failure with an underlying cause.
     *
     * @param modId  the mod that owns the configuration, not {@code null}
     * @param key    the dotted configuration key, not {@code null}
     * @param reason the actionable explanation, not {@code null}
     * @param cause  the underlying cause, may be {@code null}
     */
    public ConfigValidationException(String modId, String key, String reason, Throwable cause)
    {
        super(formatMessage(modId, key, reason), cause);
        this.modId = modId;
        this.key = key;
        this.reason = reason;
    }

    /**
     * The id of the mod that owns the configuration.
     *
     * @return the mod id, never {@code null}
     */
    public String modId()
    {
        return modId;
    }

    /**
     * The dotted configuration key that failed validation.
     *
     * @return the key, never {@code null}
     */
    public String key()
    {
        return key;
    }

    /**
     * The actionable explanation of the failure.
     *
     * @return the reason, never {@code null}
     */
    public String reason()
    {
        return reason;
    }

    private static String formatMessage(String modId, String key, String reason)
    {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(reason, "reason");
        return "config '" + modId + "' key '" + key + "': " + reason;
    }
}
