package net.weaver.api.client;

/**
 * An immutable snapshot of one client block update event.
 *
 * <p>The record is safe to share between threads. The dimension is the game's
 * dimension identifier in {@code namespace:path} form, for example
 * {@code minecraft:overworld}.
 *
 * @param dimension the non-blank dimension identifier
 * @param x         the block x coordinate
 * @param y         the block y coordinate
 * @param z         the block z coordinate
 */
public record ClientBlockContext(String dimension, int x, int y, int z)
{
    /**
     * Validates the dimension identifier.
     *
     * @throws IllegalArgumentException when {@code dimension} is {@code null}
     *                                  or blank
     */
    public ClientBlockContext
    {
        dimension = ClientValidation.requireNonBlank(dimension, "dimension");
    }
}
