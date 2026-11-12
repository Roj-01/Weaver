package net.weaver.minimap.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pixel and height data for one 256x256-block region.
 *
 * <p>Each pixel corresponds to one block and is stored as an ARGB integer.
 * The array layout is row-major with Z as the outer axis and X as the inner
 * axis: {@code index = localZ * BLOCKS_PER_REGION + localX}. A region is
 * divided into a 16x16 grid of chunks, and each chunk is 16x16 pixels.</p>
 *
 * <p>Instances expose their backing arrays and are mutable. They are not
 * thread-safe; callers must synchronize access when sharing an instance
 * between threads.</p>
 *
 * <p>A region also keeps a compact biome palette (one byte index per pixel),
 * a small metadata revision field, and a lazily computed 64x64 downsampled
 * color array ({@link #mip64()}). The mip adds 16 KiB per loaded region and is
 * only recomputed after a pixel changes.</p>
 */
public final class RegionData
{
    /**
     * Number of chunks along each axis of a region.
     */
    public static final int CHUNKS_PER_REGION = 16;

    /**
     * Number of blocks (and pixels) along each axis of a region.
     */
    public static final int BLOCKS_PER_REGION = 256;

    /**
     * Number of pixels along each axis of a chunk.
     */
    public static final int CHUNK_PIXELS = 16;

    /**
     * Number of pixels along each axis of the downsampled color array.
     */
    public static final int MIP_SIZE = 64;

    /**
     * Number of surface pixels averaged into one mip pixel.
     */
    public static final int MIP_SCALE = BLOCKS_PER_REGION / MIP_SIZE;

    /**
     * Metadata revision written by the current storage format.
     */
    public static final int METADATA_VERSION = 1;

    /**
     * Maximum number of distinct biome identifiers in one palette. A byte
     * index is stored per pixel, so index 255 is the last usable slot.
     */
    public static final int MAX_BIOMES = 255;

    /**
     * Biome identifier used when a region has no recorded biome.
     */
    public static final String UNKNOWN_BIOME = "unknown";

    private final RegionKey key;
    private final int[] colors;
    private final short[] heights;
    private final boolean[] present;
    private final int[] mip;
    private final byte[] biomeIndices;
    private final List<String> biomePalette;
    private int metadataVersion;
    private boolean mipDirty;
    private boolean dirty;

    /**
     * Creates an empty region with no chunks present.
     *
     * @param key the region key, never {@code null}
     * @throws NullPointerException when {@code key} is {@code null}
     */
    public RegionData(RegionKey key)
    {
        this.key = Objects.requireNonNull(key, "key");
        this.colors = new int[BLOCKS_PER_REGION * BLOCKS_PER_REGION];
        this.heights = new short[BLOCKS_PER_REGION * BLOCKS_PER_REGION];
        this.present = new boolean[CHUNKS_PER_REGION * CHUNKS_PER_REGION];
        this.mip = new int[MIP_SIZE * MIP_SIZE];
        this.biomeIndices = new byte[BLOCKS_PER_REGION * BLOCKS_PER_REGION];
        this.biomePalette = new ArrayList<>();
        this.biomePalette.add(UNKNOWN_BIOME);
        this.metadataVersion = METADATA_VERSION;
        this.mipDirty = true;
    }

    /**
     * Returns the region key.
     *
     * @return the region key, never {@code null}
     */
    public RegionKey key()
    {
        return key;
    }

    /**
     * Returns the internal ARGB pixel array.
     *
     * <p>The returned array is mutable and not thread-safe. It has
     * {@code BLOCKS_PER_REGION * BLOCKS_PER_REGION} entries.</p>
     *
     * @return the internal pixel array, never {@code null}
     */
    public int[] colors()
    {
        return colors;
    }

    /**
     * Returns the internal height array.
     *
     * <p>The returned array is mutable and not thread-safe, and uses the same
     * layout as {@link #colors()}.</p>
     *
     * @return the internal height array, never {@code null}
     */
    public short[] heights()
    {
        return heights;
    }

    /**
     * Returns the internal chunk presence flags.
     *
     * <p>The returned array is mutable and not thread-safe. It has one entry
     * per chunk, indexed by {@code localChunkZ * CHUNKS_PER_REGION +
     * localChunkX}.</p>
     *
     * @return the internal presence array, never {@code null}
     */
    public boolean[] present()
    {
        return present;
    }

    /**
     * Reads one pixel color.
     *
     * @param localX the local X coordinate, from {@code 0} to {@code 255}
     * @param localZ the local Z coordinate, from {@code 0} to {@code 255}
     * @return the ARGB color
     * @throws IndexOutOfBoundsException when a coordinate is outside the region
     */
    public int colorAt(int localX, int localZ)
    {
        checkBlock(localX, localZ);
        return colors[localZ * BLOCKS_PER_REGION + localX];
    }

    /**
     * Writes one pixel color.
     *
     * @param localX the local X coordinate, from {@code 0} to {@code 255}
     * @param localZ the local Z coordinate, from {@code 0} to {@code 255}
     * @param argb   the ARGB color
     * @throws IndexOutOfBoundsException when a coordinate is outside the region
     */
    public void setColor(int localX, int localZ, int argb)
    {
        checkBlock(localX, localZ);
        colors[localZ * BLOCKS_PER_REGION + localX] = argb;
        mipDirty = true;
    }

    /**
     * Reads one pixel height.
     *
     * @param localX the local X coordinate, from {@code 0} to {@code 255}
     * @param localZ the local Z coordinate, from {@code 0} to {@code 255}
     * @return the height
     * @throws IndexOutOfBoundsException when a coordinate is outside the region
     */
    public short heightAt(int localX, int localZ)
    {
        checkBlock(localX, localZ);
        return heights[localZ * BLOCKS_PER_REGION + localX];
    }

    /**
     * Writes one pixel height.
     *
     * @param localX the local X coordinate, from {@code 0} to {@code 255}
     * @param localZ the local Z coordinate, from {@code 0} to {@code 255}
     * @param height the height
     * @throws IndexOutOfBoundsException when a coordinate is outside the region
     */
    public void setHeight(int localX, int localZ, short height)
    {
        checkBlock(localX, localZ);
        heights[localZ * BLOCKS_PER_REGION + localX] = height;
    }

    /**
     * Tests whether one chunk has data.
     *
     * @param localChunkX the local chunk X coordinate, from {@code 0} to
     *                    {@code 15}
     * @param localChunkZ the local chunk Z coordinate, from {@code 0} to
     *                    {@code 15}
     * @return {@code true} when the chunk is present
     * @throws IndexOutOfBoundsException when a coordinate is outside the region
     */
    public boolean isChunkPresent(int localChunkX, int localChunkZ)
    {
        checkChunk(localChunkX, localChunkZ);
        return present[localChunkZ * CHUNKS_PER_REGION + localChunkX];
    }

    /**
     * Sets whether one chunk has data.
     *
     * @param localChunkX the local chunk X coordinate, from {@code 0} to
     *                    {@code 15}
     * @param localChunkZ the local chunk Z coordinate, from {@code 0} to
     *                    {@code 15}
     * @param chunkPresent whether the chunk is present
     * @throws IndexOutOfBoundsException when a coordinate is outside the region
     */
    public void setChunkPresent(int localChunkX, int localChunkZ, boolean chunkPresent)
    {
        checkChunk(localChunkX, localChunkZ);
        present[localChunkZ * CHUNKS_PER_REGION + localChunkX] = chunkPresent;
    }

    /**
     * Returns whether this region has unsaved changes.
     *
     * @return {@code true} when the region is dirty
     */
    public boolean isDirty()
    {
        return dirty;
    }

    /**
     * Marks this region as having unsaved changes.
     */
    public void markDirty()
    {
        dirty = true;
    }

    /**
     * Clears the unsaved-changes flag.
     */
    public void clearDirty()
    {
        dirty = false;
    }

    /**
     * Returns whether no chunk is present.
     *
     * @return {@code true} when every chunk flag is {@code false}
     */
    public boolean isEmpty()
    {
        for (boolean chunkPresent : present)
        {
            if (chunkPresent)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a defensive copy of the pixel array.
     *
     * @return a new ARGB pixel array, never {@code null}
     */
    public int[] copyColors()
    {
        return colors.clone();
    }

    /**
     * Returns the internal 64x64 downsampled color array, recomputing it when
     * the surface pixels changed.
     *
     * <p>Each mip pixel is the alpha-weighted average of a 4x4 block of surface
     * pixels. Fully transparent surface pixels do not contribute. The returned
     * array is mutable and has {@code MIP_SIZE * MIP_SIZE} entries ordered by
     * Z and then X. Recomputation walks the full 256x256 surface array, so it
     * is intended for upload/zoom passes rather than per-frame pixel reads.</p>
     *
     * @return the internal mip array, never {@code null}
     */
    public int[] mip64()
    {
        if (mipDirty)
        {
            rebuildMip();
        }
        return mip;
    }

    /**
     * Reads one downsampled pixel.
     *
     * @param mipX the mip X coordinate, from {@code 0} to {@code 63}
     * @param mipZ the mip Z coordinate, from {@code 0} to {@code 63}
     * @return the ARGB mip color, or {@code 0} when the block has no data
     * @throws IndexOutOfBoundsException when a coordinate is outside the mip
     */
    public int mipColorAt(int mipX, int mipZ)
    {
        checkMip(mipX, mipZ);
        return mip64()[mipZ * MIP_SIZE + mipX];
    }

    /**
     * Returns whether the mip must be recomputed before the next read.
     *
     * @return {@code true} when a surface pixel changed
     */
    public boolean isMipDirty()
    {
        return mipDirty;
    }

    /**
     * Forces the mip to be recomputed on the next read.
     */
    public void markMipDirty()
    {
        mipDirty = true;
    }

    /**
     * Returns the metadata revision stored with this region.
     *
     * @return the metadata revision, never negative
     */
    public int metadataVersion()
    {
        return metadataVersion;
    }

    /**
     * Sets the metadata revision written with this region.
     *
     * @param metadataVersion the revision, at least {@code 0}
     * @throws IllegalArgumentException when the revision is negative
     */
    public void setMetadataVersion(int metadataVersion)
    {
        if (metadataVersion < 0)
        {
            throw new IllegalArgumentException(
                    "metadataVersion must not be negative: " + metadataVersion);
        }
        this.metadataVersion = metadataVersion;
    }

    /**
     * Returns the number of identifiers in the compact biome palette.
     *
     * @return the palette size, at least {@code 1}
     */
    public int biomePaletteSize()
    {
        return biomePalette.size();
    }

    /**
     * Returns the biome identifier at a palette index.
     *
     * @param index the palette index
     * @return the identifier, never {@code null}
     * @throws IndexOutOfBoundsException when the index is outside the palette
     */
    public String biomeId(int index)
    {
        if (index < 0 || index >= biomePalette.size())
        {
            throw new IndexOutOfBoundsException(
                    "biome palette index must be between 0 and "
                            + (biomePalette.size() - 1) + " inclusive: " + index);
        }
        return biomePalette.get(index);
    }

    /**
     * Returns a defensive copy of the biome palette.
     *
     * @return an immutable palette list, never {@code null}
     */
    public List<String> biomePalette()
    {
        return List.copyOf(biomePalette);
    }

    /**
     * Looks up a biome identifier, adding it to the palette when absent.
     *
     * <p>When the palette is full, the unknown identifier at index {@code 0}
     * is returned so scanning can continue deterministically.</p>
     *
     * @param biomeId the biome identifier, never {@code null} or blank
     * @return the palette index
     * @throws NullPointerException     when {@code biomeId} is {@code null}
     * @throws IllegalArgumentException when {@code biomeId} is blank
     */
    public int biomeIndex(String biomeId)
    {
        Objects.requireNonNull(biomeId, "biomeId");
        if (biomeId.isBlank())
        {
            throw new IllegalArgumentException("biomeId must not be blank");
        }
        int existing = biomePalette.indexOf(biomeId);
        if (existing >= 0)
        {
            return existing;
        }
        if (biomePalette.size() >= MAX_BIOMES)
        {
            return 0;
        }
        biomePalette.add(biomeId);
        return biomePalette.size() - 1;
    }

    /**
     * Replaces the biome palette. The per-pixel indices are left untouched;
     * callers loading a file must set the palette before the indices.
     *
     * @param ids the palette identifiers, never {@code null}
     * @throws NullPointerException     when {@code ids} or an entry is
     *                                  {@code null}
     * @throws IllegalArgumentException when the palette is empty, has more
     *                                  than {@link #MAX_BIOMES} entries, or
     *                                  contains a blank identifier
     */
    public void setBiomePalette(List<String> ids)
    {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty())
        {
            throw new IllegalArgumentException("biome palette must not be empty");
        }
        if (ids.size() > MAX_BIOMES)
        {
            throw new IllegalArgumentException(
                    "biome palette has " + ids.size() + " entries, maximum is "
                            + MAX_BIOMES);
        }
        for (String id : ids)
        {
            Objects.requireNonNull(id, "biome id");
            if (id.isBlank())
            {
                throw new IllegalArgumentException("biome id must not be blank");
            }
        }
        biomePalette.clear();
        biomePalette.addAll(ids);
    }

    /**
     * Reads the biome palette index of one pixel.
     *
     * @param localX the local X coordinate, from {@code 0} to {@code 255}
     * @param localZ the local Z coordinate, from {@code 0} to {@code 255}
     * @return the palette index
     * @throws IndexOutOfBoundsException when a coordinate is outside the region
     */
    public int biomeIndexAt(int localX, int localZ)
    {
        checkBlock(localX, localZ);
        return biomeIndices[localZ * BLOCKS_PER_REGION + localX] & 0xFF;
    }

    /**
     * Writes the biome palette index of one pixel.
     *
     * @param localX the local X coordinate, from {@code 0} to {@code 255}
     * @param localZ the local Z coordinate, from {@code 0} to {@code 255}
     * @param index  a valid palette index
     * @throws IndexOutOfBoundsException when a coordinate or index is outside
     *                                   the valid range
     */
    public void setBiomeIndex(int localX, int localZ, int index)
    {
        checkBlock(localX, localZ);
        if (index < 0 || index >= biomePalette.size())
        {
            throw new IndexOutOfBoundsException(
                    "biome palette index must be between 0 and "
                            + (biomePalette.size() - 1) + " inclusive: " + index);
        }
        biomeIndices[localZ * BLOCKS_PER_REGION + localX] = (byte) index;
    }

    /**
     * Returns the biome identifier at one pixel, or the unknown identifier when
     * the stored index is not in the palette.
     *
     * @param localX the local X coordinate, from {@code 0} to {@code 255}
     * @param localZ the local Z coordinate, from {@code 0} to {@code 255}
     * @return the biome identifier, never {@code null}
     * @throws IndexOutOfBoundsException when a coordinate is outside the region
     */
    public String biomeAt(int localX, int localZ)
    {
        int index = biomeIndexAt(localX, localZ);
        return index < biomePalette.size() ? biomePalette.get(index) : UNKNOWN_BIOME;
    }

    /**
     * Returns a deep copy of this region with the dirty flag cleared.
     *
     * @return an independent copy, never {@code null}
     */
    public RegionData copy()
    {
        RegionData copy = new RegionData(key);
        System.arraycopy(colors, 0, copy.colors, 0, colors.length);
        System.arraycopy(heights, 0, copy.heights, 0, heights.length);
        System.arraycopy(present, 0, copy.present, 0, present.length);
        System.arraycopy(biomeIndices, 0, copy.biomeIndices, 0, biomeIndices.length);
        copy.biomePalette.clear();
        copy.biomePalette.addAll(biomePalette);
        copy.metadataVersion = metadataVersion;
        copy.mipDirty = mipDirty;
        if (!mipDirty)
        {
            System.arraycopy(mip, 0, copy.mip, 0, mip.length);
        }
        copy.dirty = false;
        return copy;
    }

    private void rebuildMip()
    {
        for (int mipZ = 0; mipZ < MIP_SIZE; mipZ++)
        {
            for (int mipX = 0; mipX < MIP_SIZE; mipX++)
            {
                long red = 0L;
                long green = 0L;
                long blue = 0L;
                long alpha = 0L;
                int pixels = 0;
                int baseX = mipX * MIP_SCALE;
                int baseZ = mipZ * MIP_SCALE;
                for (int z = baseZ; z < baseZ + MIP_SCALE; z++)
                {
                    int row = z * BLOCKS_PER_REGION;
                    for (int x = baseX; x < baseX + MIP_SCALE; x++)
                    {
                        int argb = colors[row + x];
                        int pixelAlpha = argb >>> 24;
                        if (pixelAlpha == 0)
                        {
                            continue;
                        }
                        red += (argb >> 16) & 0xFF;
                        green += (argb >> 8) & 0xFF;
                        blue += argb & 0xFF;
                        alpha += pixelAlpha;
                        pixels++;
                    }
                }
                if (pixels == 0)
                {
                    mip[mipZ * MIP_SIZE + mipX] = 0;
                    continue;
                }
                int averageAlpha = (int) ((alpha + pixels / 2L) / pixels);
                int averageRed = (int) ((red + pixels / 2L) / pixels);
                int averageGreen = (int) ((green + pixels / 2L) / pixels);
                int averageBlue = (int) ((blue + pixels / 2L) / pixels);
                mip[mipZ * MIP_SIZE + mipX] = (averageAlpha << 24)
                        | (averageRed << 16) | (averageGreen << 8) | averageBlue;
            }
        }
        mipDirty = false;
    }

    private static void checkMip(int mipX, int mipZ)
    {
        if (mipX < 0 || mipX >= MIP_SIZE)
        {
            throw new IndexOutOfBoundsException(
                    "mipX must be between 0 and " + (MIP_SIZE - 1) + " inclusive: " + mipX);
        }
        if (mipZ < 0 || mipZ >= MIP_SIZE)
        {
            throw new IndexOutOfBoundsException(
                    "mipZ must be between 0 and " + (MIP_SIZE - 1) + " inclusive: " + mipZ);
        }
    }

    private static void checkBlock(int localX, int localZ)
    {
        if (localX < 0 || localX >= BLOCKS_PER_REGION)
        {
            throw new IndexOutOfBoundsException(
                    "localX must be between 0 and 255 inclusive: " + localX);
        }
        if (localZ < 0 || localZ >= BLOCKS_PER_REGION)
        {
            throw new IndexOutOfBoundsException(
                    "localZ must be between 0 and 255 inclusive: " + localZ);
        }
    }

    private static void checkChunk(int localChunkX, int localChunkZ)
    {
        if (localChunkX < 0 || localChunkX >= CHUNKS_PER_REGION)
        {
            throw new IndexOutOfBoundsException(
                    "localChunkX must be between 0 and 15 inclusive: " + localChunkX);
        }
        if (localChunkZ < 0 || localChunkZ >= CHUNKS_PER_REGION)
        {
            throw new IndexOutOfBoundsException(
                    "localChunkZ must be between 0 and 15 inclusive: " + localChunkZ);
        }
    }
}
