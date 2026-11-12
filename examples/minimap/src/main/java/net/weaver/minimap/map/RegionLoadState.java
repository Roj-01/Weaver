package net.weaver.minimap.map;

/**
 * Lifecycle of an asynchronous region or cave-slice load.
 *
 * <p>{@link #LOADED} and {@link #FAILED} are terminal for one request. A failed
 * key may be requested again after the corrupt file has been quarantined.</p>
 */
public enum RegionLoadState
{
    /** No request has been made and the value is not in memory. */
    UNLOADED,

    /** Waiting for the loader thread to pick the key up. */
    QUEUED,

    /** The loader thread is reading the key. */
    LOADING,

    /** The value was read and is available to the client thread. */
    LOADED,

    /** The read failed; the caller should recover with empty data. */
    FAILED
}
