package net.weaver.minimap.map;

import java.util.Objects;

/**
 * Incremental builder for one cave slice.
 *
 * <p>Work is split into 256 chunk cells, each 16x16 columns. The client thread
 * calls {@link #step(int)} with a small budget so a slice never blocks a
 * frame. The builder is deterministic: the same source and key always produce
 * the same colors regardless of how the steps are divided.</p>
 */
public final class CaveSliceBuild
{
    private static final int CHUNK_CELLS =
            RegionData.CHUNKS_PER_REGION * RegionData.CHUNKS_PER_REGION;
    private static final int FALLBACK_SOLID = 0xFF7A7A7A;

    private final CaveBlockSource source;
    private final CavePalette palette;
    private final CaveSlice slice;
    private int nextChunk;

    /**
     * Creates a build job.
     *
     * @param source  the block source, never {@code null}
     * @param key     the region key, never {@code null}
     * @param y       the sampled Y level
     * @param palette the dimension palette, never {@code null}
     */
    public CaveSliceBuild(CaveBlockSource source, RegionKey key, int y, CavePalette palette)
    {
        this.source = Objects.requireNonNull(source, "source");
        this.palette = Objects.requireNonNull(palette, "palette");
        this.slice = new CaveSlice(key, y);
    }

    /**
     * Processes up to {@code chunkBudget} chunk cells.
     *
     * @param chunkBudget the number of 16x16 cells to process
     * @return {@code true} when the slice is complete
     */
    public boolean step(int chunkBudget)
    {
        int remaining = Math.max(0, chunkBudget);
        while (remaining > 0 && nextChunk < CHUNK_CELLS)
        {
            processChunk(nextChunk);
            nextChunk++;
            remaining--;
        }
        return done();
    }

    /** Returns whether every chunk cell has been processed. */
    public boolean done()
    {
        return nextChunk >= CHUNK_CELLS;
    }

    /**
     * Returns the slice under construction.
     *
     * @return the slice, never {@code null}
     */
    public CaveSlice slice()
    {
        return slice;
    }

    /**
     * Returns the number of processed chunk cells.
     *
     * @return a value from {@code 0} to {@code 256}
     */
    public int progress()
    {
        return nextChunk;
    }

    private void processChunk(int chunkIndex)
    {
        int chunkX = chunkIndex & (RegionData.CHUNKS_PER_REGION - 1);
        int chunkZ = chunkIndex >>> 4;
        int baseX = slice.key().minBlockX() + chunkX * RegionData.CHUNK_PIXELS;
        int baseZ = slice.key().minBlockZ() + chunkZ * RegionData.CHUNK_PIXELS;
        int y = slice.y();
        for (int localZ = 0; localZ < RegionData.CHUNK_PIXELS; localZ++)
        {
            int worldZ = baseZ + localZ;
            int regionZ = chunkZ * RegionData.CHUNK_PIXELS + localZ;
            for (int localX = 0; localX < RegionData.CHUNK_PIXELS; localX++)
            {
                int worldX = baseX + localX;
                int regionX = chunkX * RegionData.CHUNK_PIXELS + localX;
                CaveBlockKind kind = source.kindAt(worldX, y, worldZ);
                if (kind == CaveBlockKind.AIR || kind == CaveBlockKind.NONE)
                {
                    continue;
                }
                int color;
                if (kind == CaveBlockKind.WATER)
                {
                    color = CavePalette.shade(0xFF2C4F80, 0.82D);
                }
                else if (kind == CaveBlockKind.LAVA)
                {
                    color = CavePalette.shade(0xFFA84818, 0.95D);
                }
                else if (kind == CaveBlockKind.VOID)
                {
                    color = palette.voidColor();
                }
                else
                {
                    int base = source.blockColor(worldX, y, worldZ);
                    if ((base >>> 24) == 0)
                    {
                        base = FALLBACK_SOLID;
                    }
                    CaveBlockKind above = source.kindAt(worldX, y + 1, worldZ);
                    CaveBlockKind below = source.kindAt(worldX, y - 1, worldZ);
                    double factor = 0.92D;
                    if (above == CaveBlockKind.AIR)
                    {
                        factor = 1.16D;
                    }
                    else if (below == CaveBlockKind.AIR)
                    {
                        factor = 0.80D;
                    }
                    int exposed = 0;
                    if (source.kindAt(worldX - 1, y, worldZ) == CaveBlockKind.AIR)
                    {
                        exposed++;
                    }
                    if (source.kindAt(worldX + 1, y, worldZ) == CaveBlockKind.AIR)
                    {
                        exposed++;
                    }
                    if (source.kindAt(worldX, y, worldZ - 1) == CaveBlockKind.AIR)
                    {
                        exposed++;
                    }
                    if (source.kindAt(worldX, y, worldZ + 1) == CaveBlockKind.AIR)
                    {
                        exposed++;
                    }
                    factor += exposed * 0.025D;
                    color = CavePalette.shade(palette.tintSolid(base), factor);
                }
                slice.setPresent(regionX, regionZ, true);
                slice.setColor(regionX, regionZ, color);
            }
        }
    }
}
