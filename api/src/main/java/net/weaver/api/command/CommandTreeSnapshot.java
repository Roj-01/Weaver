package net.weaver.api.command;

import java.util.List;

/**
 * An immutable, side-agnostic snapshot of a command tree.
 *
 * <p>The snapshot is intended for client command-tree synchronization and user
 * interface inspection. It records the raw game dispatcher (possibly
 * {@code null}), the command names known when the snapshot was taken, a source
 * label, and a monotonic sequence number. The dispatcher is opaque to the API;
 * consumers may cast it to the game type when they run in the shared class
 * space.
 *
 * <p>Instances are immutable and thread-safe. The command name list is copied
 * defensively and exposed as an unmodifiable list.
 *
 * @param dispatcher   the raw game dispatcher, possibly {@code null}
 * @param commandNames the command names at snapshot time, never {@code null}
 * @param source       the snapshot source label, possibly {@code null}
 * @param sequence     the monotonically increasing snapshot sequence
 */
public record CommandTreeSnapshot(Object dispatcher, List<String> commandNames,
        String source, long sequence)
{
    /**
     * Creates a snapshot with a defensive copy of the command names.
     *
     * @param dispatcher   the raw game dispatcher, possibly {@code null}
     * @param commandNames the command names, or {@code null} for none
     * @param source       the snapshot source label, possibly {@code null}
     * @param sequence     the snapshot sequence
     */
    public CommandTreeSnapshot
    {
        commandNames = commandNames == null ? List.of() : List.copyOf(commandNames);
    }
}
