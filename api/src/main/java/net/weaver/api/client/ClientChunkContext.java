package net.weaver.api.client;

/**
 * An immutable snapshot of one client chunk load or unload event.
 *
 * <p>The record is safe to share between threads. The dimension is the game's
 * dimension identifier in {@code namespace:path} form, for example
 * {@code minecraft:overworld}.
 *
 * @param dimension the non-blank dimension identifier
 * @param chunkX    the chunk x coordinate
 * @param chunkZ    the chunk z coordinate
 */
public record ClientChunkContext(String dimension, int chunkX, int chunkZ)
{
    /**
     * Validates the dimension identifier.
     *
     * @throws IllegalArgumentException when {@code dimension} is {@code null}
     *                                  or blank
     */
    public ClientChunkContext
    {
        dimension = ClientValidation.requireNonBlank(dimension, "dimension");
    }
}
