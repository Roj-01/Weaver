package net.weaver.api.capability;

import java.util.Objects;

/**
 * One completed attachment mutation observed by an
 * {@link AttachmentChangeListener}.
 *
 * <p>A {@link Kind#SET} change has a non-null {@link #newValue()} and the
 * previous explicit value in {@link #previousValue()}. A {@link Kind#REMOVE}
 * change has a {@code null} {@link #newValue()}. A {@link Kind#CLEAR} change
 * represents a whole-holder clear: {@link #capability()} and both value fields
 * are {@code null}.</p>
 *
 * @param holder        the holder that changed, never {@code null}
 * @param capability    the capability that changed, or {@code null} for a clear
 * @param previousValue the previous explicit value, or {@code null}
 * @param newValue      the new explicit value, or {@code null} for a removal
 * @param kind          the mutation kind, never {@code null}
 */
public record AttachmentChange(Object holder, Capability<?> capability,
        Object previousValue, Object newValue, Kind kind) {

    /** The kind of attachment mutation. */
    public enum Kind {
        /** An explicit value was written. */
        SET,
        /** An explicit value was removed. */
        REMOVE,
        /** Every explicit value for the holder was cleared. */
        CLEAR
    }

    /**
     * Validates the change.
     *
     * @throws NullPointerException when {@code holder} or {@code kind} is
     *                              {@code null}
     */
    public AttachmentChange {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(kind, "kind");
    }
}
