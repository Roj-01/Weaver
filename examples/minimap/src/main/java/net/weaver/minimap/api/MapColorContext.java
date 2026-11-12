package net.weaver.minimap.api;

/**
 * Immutable block context passed to {@link MapColorProvider}.
 *
 * @param dimension the current dimension id, or {@code null} outside a world
 * @param x         the block X
 * @param y         the block Y
 * @param z         the block Z
 * @param baseColor the color the default renderer selected, in ARGB
 */
public record MapColorContext(String dimension, int x, int y, int z, int baseColor)
{
}
