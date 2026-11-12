package net.weaver.minimap.map;

/**
 * The map-related subset of the mod configuration.
 *
 * <p>{@link net.weaver.minimap.config.MinimapConfig} implements this interface;
 * the map engine depends on the interface so scan/cache tuning can be tested
 * without a config file. All values are already validated by the config.</p>
 */
public interface MapSettings
{
    /** The HUD map width and height in pixels. */
    default int size()
    {
        return 160;
    }

    /** Blocks shown per pixel. */
    default double zoom()
    {
        return 2.0D;
    }

    /** Chunks scanned per client tick for the surface layer. */
    int scanChunksPerTick();

    /** Ticks between dirty-region write passes. */
    int saveIntervalTicks();

    /** Maximum number of surface regions kept in memory. */
    int regionCacheSize();

    /** Maximum number of cave slices kept in memory. */
    int caveCacheSize();

    /** Whether the cave layer is enabled at all. */
    boolean caveEnabled();

    /** Whether the cave Y is chosen automatically. */
    boolean caveAuto();

    /** The manual cave Y when {@link #caveAuto()} is false. */
    int caveY();

    /** Whether the surface map is blended over the cave slice. */
    boolean caveSurfaceOverlay();

    /** Surface overlay opacity, from 0 to 1. */
    double caveSurfaceOpacity();

    /**
     * Cave scan budget per client tick. One unit processes eight 16x16 column
     * cells of a slice, so a value of {@code 2} advances the scanner by 512
     * columns per tick.
     */
    int caveScanChunksPerTick();
}
