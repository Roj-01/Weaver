package net.weaver.core.modops.update;

/**
 * Outcome of an update check.
 *
 * <p>{@link #NOT_CONFIGURED} is the default when no {@link UpdateCheck} is
 * installed; it is not an error.
 */
public enum UpdateStatus {

    /** Every installed mod is current. */
    UP_TO_DATE,

    /** At least one update is available. */
    UPDATE_AVAILABLE,

    /** The check ran but could not determine an answer. */
    UNKNOWN,

    /** No update source is configured. */
    NOT_CONFIGURED,

    /** The check failed. */
    ERROR
}
