package net.weaver.minimap.api;

/**
 * Overrides a block color on the map.
 *
 * <p>Providers run in registration order; the first non-null result wins.
 * Returning {@code null} leaves the color to the next provider or the default
 * renderer. Exceptions are isolated and logged once.</p>
 */
@FunctionalInterface
public interface MapColorProvider
{
    /**
     * Returns a color override.
     *
     * @param context the block context, never {@code null}
     * @return the override, or {@code null} to keep the current color
     */
    MapColor color(MapColorContext context);
}
