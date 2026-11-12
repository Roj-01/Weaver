package net.weaver.api.config;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of applying one server-authoritative configuration snapshot.
 *
 * <p>A synced document is applied through the same per-value validation path as
 * a file load. {@code appliedValues} counts declared keys whose encoded value
 * was accepted, {@code defaultedValues} counts declared keys that were missing
 * or invalid and therefore kept their default, and {@code ignoredKeys} counts
 * keys in the document that the local spec does not declare. Problems describe
 * individual invalid values, never ignored keys.</p>
 *
 * @param schemaVersion   the schema version of the applied snapshot
 * @param appliedValues   how many declared values came from the snapshot
 * @param defaultedValues how many declared values used their default
 * @param ignoredKeys     how many undeclared keys were ignored
 * @param problems        the immutable invalid-value problems in discovery
 *                        order, never {@code null}
 */
public record ConfigSyncResult(int schemaVersion, int appliedValues, int defaultedValues,
                               int ignoredKeys, List<ConfigProblem> problems)
{
    /**
     * Validates and defensively copies the problem list.
     *
     * @throws NullPointerException     when {@code problems} or an element is
     *                                  {@code null}
     * @throws IllegalArgumentException when a count is negative
     */
    public ConfigSyncResult
    {
        Objects.requireNonNull(problems, "problems");
        if (schemaVersion < 1)
        {
            throw new IllegalArgumentException("schemaVersion must be at least 1 but was "
                    + schemaVersion);
        }
        if (appliedValues < 0 || defaultedValues < 0 || ignoredKeys < 0)
        {
            throw new IllegalArgumentException("config sync counts must not be negative");
        }
        problems = List.copyOf(problems);
    }

    /**
     * Whether at least one invalid value was reported.
     *
     * @return {@code true} when {@link #problems()} is not empty
     */
    public boolean hasProblems()
    {
        return !problems.isEmpty();
    }
}
