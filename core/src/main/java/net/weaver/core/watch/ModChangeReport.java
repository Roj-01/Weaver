package net.weaver.core.watch;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One detected change in the mods directory.
 *
 * <p>A report describes a regular {@code .jar} file at the time it was
 * observed. For a removed file the size and modification time are the values
 * recorded by the previous scan. Reports are immutable and are produced by
 * {@link ModWatchService#scanOnce()} or by the watcher thread.</p>
 *
 * @param path         the absolute, normalized jar path, not {@code null}
 * @param kind         the kind of change, not {@code null}
 * @param size         the observed size in bytes, never negative
 * @param lastModified the observed modification time in milliseconds since the
 *                     epoch, or a negative value when the platform cannot
 *                     report it
 */
public record ModChangeReport(Path path, Kind kind, long size, long lastModified)
{
    /**
     * The kind of a mods-directory change.
     */
    public enum Kind
    {
        /** A jar appeared after the previous scan. */
        ADDED,
        /** An existing jar disappeared after the previous scan. */
        REMOVED,
        /** An existing jar changed its size or modification time. */
        MODIFIED
    }

    /**
     * Validates the report fields.
     *
     * @throws NullPointerException     when {@code path} or {@code kind} is
     *                                  {@code null}
     * @throws IllegalArgumentException when {@code size} is negative
     */
    public ModChangeReport
    {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        if (size < 0L)
        {
            throw new IllegalArgumentException("size must not be negative but was " + size);
        }
        path = path.toAbsolutePath().normalize();
    }
}
