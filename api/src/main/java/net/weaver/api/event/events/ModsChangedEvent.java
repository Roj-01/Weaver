package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.List;
import java.util.Objects;

/**
 * Posted when the loader observes a change in the mods directory.
 *
 * <p>The event reports added, removed, and modified {@code .jar} files. It is
 * informational: the loader loaded mods once at boot and does not apply the
 * change in this version. Every reported change requires a restart, because
 * the JVM cannot remove an entry from the system class loader search path and
 * classes that are already loaded cannot be replaced.</p>
 *
 * <p>The event is not cancelable and exposes no result. It is posted on
 * {@link net.weaver.api.event.EventBus#GAME} with logical side
 * {@link net.weaver.api.event.LogicalSide#BOTH} by
 * {@code net.weaver.core.watch.ModWatchService}. A listener that touches game
 * state must marshal to the appropriate game thread because the watcher runs on
 * its own daemon thread.</p>
 */
public final class ModsChangedEvent extends Event
{
    /**
     * The kind of a reported mods-directory change.
     */
    public enum ChangeKind
    {
        /** A jar appeared after the previous scan. */
        ADDED,
        /** An existing jar disappeared after the previous scan. */
        REMOVED,
        /** An existing jar changed its size or modification time. */
        MODIFIED
    }

    /**
     * One changed jar file.
     *
     * @param path         the reported path, not {@code null}
     * @param kind         the kind of change, not {@code null}
     * @param size         the observed size in bytes, never negative
     * @param lastModified the observed modification time in milliseconds since
     *                     the epoch, or a negative value when unspecified
     */
    public record Change(String path, ChangeKind kind, long size, long lastModified)
    {
        /**
         * Validates the change fields.
         *
         * @throws NullPointerException     when {@code path} or {@code kind} is
         *                                  {@code null}
         * @throws IllegalArgumentException when {@code size} is negative
         */
        public Change
        {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(kind, "kind");
            if (size < 0L)
            {
                throw new IllegalArgumentException("size must not be negative but was " + size);
            }
        }
    }

    private final List<Change> changes;

    /**
     * Creates an event for one batch of changes.
     *
     * @param changes the changes in deterministic path order, not {@code null}
     * @throws NullPointerException when {@code changes} or one of its elements
     *                              is {@code null}
     */
    public ModsChangedEvent(List<Change> changes)
    {
        Objects.requireNonNull(changes, "changes");
        this.changes = List.copyOf(changes);
    }

    /**
     * The changed jars in deterministic path order.
     *
     * @return an immutable change list, never {@code null}
     */
    public List<Change> changes()
    {
        return changes;
    }

    /**
     * Whether the change requires a restart.
     *
     * <p>The loader does not apply mods-directory changes in this version, so
     * this is {@code true} whenever at least one change was reported.</p>
     *
     * @return {@code true} when {@link #changes()} is not empty
     */
    public boolean restartRequired()
    {
        return !changes.isEmpty();
    }
}
