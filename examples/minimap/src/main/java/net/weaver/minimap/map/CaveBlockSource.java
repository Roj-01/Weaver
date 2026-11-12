package net.weaver.minimap.map;

/**
 * Read-only block view used by the cave scanner and the automatic Y scorer.
 *
 * <p>Implementations must be deterministic for a fixed world state and must
 * not be shared between threads. The game-backed implementation is
 * {@link LevelCaveSource}; tests use small synthetic views.</p>
 */
public interface CaveBlockSource
{
    /** The lowest block Y in the dimension. */
    int minY();

    /** The highest block Y in the dimension, exclusive. */
    int maxY();

    /**
     * Classifies one block.
     *
     * @return the block kind, never {@code null}
     */
    CaveBlockKind kindAt(int x, int y, int z);

    /**
     * Returns the base map color of a solid block.
     *
     * @return an opaque ARGB color
     */
    int blockColor(int x, int y, int z);

    /**
     * Returns the surface height at a column.
     *
     * @return the first air Y above the surface, or {@link Integer#MIN_VALUE}
     *         when the column is unavailable
     */
    int surfaceY(int x, int z);
}
