package net.weaver.minimap.map;

/**
 * Classification of one block for cave slice scanning.
 */
public enum CaveBlockKind
{
    /** Air or a non-solid decoration; rendered as transparent negative space. */
    AIR,

    /** A solid, opaque block. */
    SOLID,

    /** Water or another non-lava fluid. */
    WATER,

    /** Lava. */
    LAVA,

    /** Outside the build height; rendered as dark void. */
    VOID,

    /** Unloaded or unavailable column; rendered as transparent. */
    NONE
}
