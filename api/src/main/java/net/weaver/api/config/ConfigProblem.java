package net.weaver.api.config;

import java.util.Objects;

/**
 * One problem discovered while loading a configuration file.
 *
 * <p>A problem is informational: the manager still loads every value it can,
 * falling back to defaults for anything invalid. The special key
 * {@code "<file>"} identifies a file-level problem such as unparseable content,
 * a newer schema version, or a failed migration.
 *
 * @param key     the affected configuration key, or {@code "<file>"} for a
 *                file-level problem, not {@code null}
 * @param message the actionable explanation, not {@code null}
 */
public record ConfigProblem(String key, String message)
{
    /**
     * Validates the components.
     *
     * @throws NullPointerException when {@code key} or {@code message} is
     *                              {@code null}
     */
    public ConfigProblem
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(message, "message");
    }
}
