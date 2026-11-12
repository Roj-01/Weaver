package net.weaver.api.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of one configuration load attempt.
 *
 * <p>This record is a diagnostic snapshot. It is returned from
 * {@link ConfigManager#load()} and {@link ConfigManager#reload()}. Repeated
 * {@code load()} calls return the same instance until an explicit reload; each
 * reload publishes a new result. The {@code appliedVersion} is the schema
 * version now in memory (normally the spec version), while {@code fileVersion}
 * is the version found in the file before migration, or {@code 0} when it could
 * not be determined.
 *
 * @param filePresent     whether a file was found at the store location
 * @param fileVersion     the schema version read from the file, or {@code 0}
 * @param appliedVersion  the schema version now applied in memory
 * @param appliedValues   how many declared values came from the file
 * @param defaultedValues how many declared values fell back to their default
 * @param migrated        whether one or more migrations ran successfully
 * @param corrupt         whether the file content was unparseable or had an
 *                        invalid schema version
 * @param backup          the backup created for corrupt content, or
 *                        {@code null}
 * @param problems        the immutable problem list in discovery order, never
 *                        {@code null}
 */
public record ConfigLoadResult(
        boolean filePresent,
        int fileVersion,
        int appliedVersion,
        int appliedValues,
        int defaultedValues,
        boolean migrated,
        boolean corrupt,
        Path backup,
        List<ConfigProblem> problems)
{
    /**
     * Validates and defensively copies the problem list.
     *
     * @throws NullPointerException when {@code problems} or one of its
     *                              elements is {@code null}
     */
    public ConfigLoadResult
    {
        Objects.requireNonNull(problems, "problems");
        problems = List.copyOf(problems);
    }

    /**
     * Whether at least one problem was recorded.
     *
     * @return {@code true} when {@link #problems()} is not empty
     */
    public boolean hasProblems()
    {
        return !problems.isEmpty();
    }
}
