package net.weaver.core.modops;

/**
 * Final disposition of one candidate mod jar discovered by the loader.
 *
 * <p>Only {@link #LOADED} entries participate in the class path. Every other
 * status describes a jar that was discovered but not loaded, together with the
 * reason recorded on {@link ModListEntry#statusDetail()}.
 */
public enum ModStatus {

    /** The mod passed side, duplicate-id, breaks, and dependency validation. */
    LOADED,

    /** The mod declares a side that does not match the current process side. */
    WRONG_SIDE,

    /** Another jar with the same mod id was already discovered first. */
    DUPLICATE_ID,

    /** The jar or its {@code weaver.mod.json} descriptor could not be read. */
    READ_FAILED,

    /** The mod declares a {@code breaks} entry that matches a discovered mod. */
    REJECTED_BREAKS,

    /** A required dependency is not present among the discovered mods. */
    REJECTED_DEPENDENCY,

    /** A required dependency exists but its version does not satisfy the range. */
    REJECTED_VERSION,

    /** A fatal loader policy rejected the mod or aborted the load. */
    REJECTED_POLICY
}
