package net.weaver.minimap.map;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

/**
 * Reads and writes region files in a {@code .wmap} container.
 *
 * <p>Files are gzip-compressed streams written with big-endian
 * {@link DataOutputStream} primitives. Version {@code 2} is written and
 * version {@code 1} is still read. The version 2 uncompressed layout is:</p>
 *
 * <ol>
 *   <li>magic {@code 0x574D4150} ({@code "WMAP"}) as an int</li>
 *   <li>format version {@code 2} as an int</li>
 *   <li>region X as an int</li>
 *   <li>region Z as an int</li>
 *   <li>metadata revision as an int</li>
 *   <li>biome palette size as an int, then that many UTF identifiers</li>
 *   <li>one unsigned byte palette index per pixel, ordered by local Z and
 *       then local X</li>
 *   <li>a 32-byte presence bitmap, one bit per chunk, least-significant bit
 *       first, indexed by {@code chunkZ * 16 + chunkX}</li>
 *   <li>for each present chunk in {@code chunkZ * 16 + chunkX} order: 256
 *       ARGB colors as ints, then 256 heights as shorts; within each run the
 *       pixels are ordered by local Z and then local X</li>
 * </ol>
 *
 * <p>Version 1 omits the metadata revision, biome palette, and biome index
 * block; those are read as defaults. Writes create parent directories, write a
 * sibling temporary file, and replace the target with an atomic move when the
 * file system supports one. Truncated or corrupt files are rejected with an
 * {@link IOException}. Instances are stateless and safe to share between
 * threads.</p>
 */
public final class RegionStorage
{
    private static final int MAGIC = 0x574D4150;
    private static final int FORMAT_VERSION = 2;
    private static final int LEGACY_FORMAT_VERSION = 1;
    private static final int PRESENCE_BITMAP_BYTES =
            RegionData.CHUNKS_PER_REGION * RegionData.CHUNKS_PER_REGION / 8;
    private static final int BIOME_INDEX_BYTES =
            RegionData.BLOCKS_PER_REGION * RegionData.BLOCKS_PER_REGION;

    private final Path directory;

    /**
     * Creates storage rooted at a directory.
     *
     * @param directory the directory holding region files, never {@code null}
     * @throws NullPointerException when {@code directory} is {@code null}
     */
    public RegionStorage(Path directory)
    {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /**
     * Returns the file used for a region.
     *
     * @param key the region key, never {@code null}
     * @return the region file path
     * @throws NullPointerException when {@code key} is {@code null}
     */
    public Path fileFor(RegionKey key)
    {
        Objects.requireNonNull(key, "key");
        return directory.resolve(key.fileName());
    }

    /**
     * Moves a corrupt region file aside so a later request starts clean.
     *
     * @param key the region key, never {@code null}
     * @return the quarantine path, or {@code null} when no file exists
     * @throws NullPointerException when {@code key} is {@code null}
     * @throws IOException          when the file cannot be moved
     */
    public Path quarantine(RegionKey key) throws IOException
    {
        Objects.requireNonNull(key, "key");
        Path file = fileFor(key);
        if (Files.notExists(file))
        {
            return null;
        }
        Path target = file.resolveSibling(file.getFileName() + ".corrupt");
        moveReplacing(file, target);
        return target;
    }

    /**
     * Loads one region.
     *
     * <p>The returned data is not dirty. The stored region coordinates must
     * match the requested key.</p>
     *
     * @param key the region key, never {@code null}
     * @return the loaded region, or {@code null} when the file does not exist
     * @throws NullPointerException when {@code key} is {@code null}
     * @throws IOException          when the file cannot be read or is corrupt
     */
    public RegionData load(RegionKey key) throws IOException
    {
        Objects.requireNonNull(key, "key");
        Path file = fileFor(key);
        if (Files.notExists(file))
        {
            return null;
        }
        byte[] compressed;
        try
        {
            compressed = Files.readAllBytes(file);
        }
        catch (IOException failure)
        {
            throw new IOException("cannot read region file " + file, failure);
        }
        RegionData data;
        try (InputStream raw = new ByteArrayInputStream(compressed);
             InputStream buffered = new BufferedInputStream(raw);
             InputStream gzip = new GZIPInputStream(buffered);
             DataInputStream in = new DataInputStream(gzip))
        {
            data = readBody(key, file, in);
        }
        catch (EOFException truncated)
        {
            throw new IOException("truncated region file " + file, truncated);
        }
        catch (ZipException invalid)
        {
            throw new IOException("corrupt region file " + file + ": invalid gzip data",
                    invalid);
        }
        catch (IOException failure)
        {
            String message = failure.getMessage();
            if (message != null && message.startsWith("corrupt region file "))
            {
                throw failure;
            }
            throw new IOException("cannot read region file " + file + ": " + reason(failure),
                    failure);
        }
        data.clearDirty();
        return data;
    }

    /**
     * Saves one region atomically.
     *
     * <p>Empty regions are rejected; a region must have at least one present
     * chunk before it can be written.</p>
     *
     * @param data the region data, never {@code null}
     * @throws NullPointerException     when {@code data} is {@code null}
     * @throws IllegalArgumentException when {@code data} has no present chunks
     * @throws IOException              when the file cannot be written
     */
    public void save(RegionData data) throws IOException
    {
        Objects.requireNonNull(data, "data");
        if (data.isEmpty())
        {
            throw new IllegalArgumentException("cannot save empty region " + data.key());
        }
        Path file = fileFor(data.key());
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null)
        {
            throw new IOException("region file has no parent directory: " + file);
        }
        Path temporary = null;
        try
        {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, data.key().fileName() + ".", ".tmp");
            try (OutputStream raw = Files.newOutputStream(temporary);
                 OutputStream buffered = new BufferedOutputStream(raw);
                 GZIPOutputStream gzip = new GZIPOutputStream(buffered);
                 DataOutputStream out = new DataOutputStream(gzip))
            {
                writeBody(data, out);
            }
            moveReplacing(temporary, file);
            temporary = null;
        }
        catch (IOException | RuntimeException failure)
        {
            throw new IOException("cannot write region file " + file, failure);
        }
        finally
        {
            deleteQuietly(temporary);
        }
    }

    private static RegionData readBody(RegionKey key, Path file, DataInputStream in)
            throws IOException
    {
        int magic = in.readInt();
        if (magic != MAGIC)
        {
            throw corrupt(file, "wrong magic 0x" + Integer.toHexString(magic));
        }
        int version = in.readInt();
        if (version != LEGACY_FORMAT_VERSION && version != FORMAT_VERSION)
        {
            throw corrupt(file, "unsupported format version " + version);
        }
        int regionX = in.readInt();
        int regionZ = in.readInt();
        if (regionX != key.regionX() || regionZ != key.regionZ())
        {
            throw corrupt(file, "stored region " + regionX + "," + regionZ
                    + " does not match requested region " + key.regionX() + ","
                    + key.regionZ());
        }
        RegionData data = new RegionData(key);
        if (version == FORMAT_VERSION)
        {
            readMetadata(data, file, in);
        }
        readChunks(data, file, in);
        if (in.read() != -1)
        {
            throw corrupt(file, "trailing data after region payload");
        }
        data.markMipDirty();
        return data;
    }

    private static void readMetadata(RegionData data, Path file, DataInputStream in)
            throws IOException
    {
        int metadataVersion = in.readInt();
        if (metadataVersion < 0)
        {
            throw corrupt(file, "negative metadata version " + metadataVersion);
        }
        data.setMetadataVersion(metadataVersion);
        int paletteSize = in.readInt();
        if (paletteSize <= 0 || paletteSize > RegionData.MAX_BIOMES)
        {
            throw corrupt(file, "invalid biome palette size " + paletteSize);
        }
        java.util.List<String> palette = new java.util.ArrayList<>(paletteSize);
        for (int index = 0; index < paletteSize; index++)
        {
            String id = in.readUTF();
            if (id.isBlank())
            {
                throw corrupt(file, "blank biome identifier at palette index " + index);
            }
            palette.add(id);
        }
        try
        {
            data.setBiomePalette(palette);
        }
        catch (IllegalArgumentException invalid)
        {
            throw corrupt(file, "invalid biome palette: " + invalid.getMessage());
        }
        byte[] indices = new byte[BIOME_INDEX_BYTES];
        in.readFully(indices);
        for (int localZ = 0; localZ < RegionData.BLOCKS_PER_REGION; localZ++)
        {
            int row = localZ * RegionData.BLOCKS_PER_REGION;
            for (int localX = 0; localX < RegionData.BLOCKS_PER_REGION; localX++)
            {
                int index = indices[row + localX] & 0xFF;
                if (index >= paletteSize)
                {
                    throw corrupt(file, "biome index " + index
                            + " is outside a palette of " + paletteSize);
                }
                data.setBiomeIndex(localX, localZ, index);
            }
        }
    }

    private static void readChunks(RegionData data, Path file, DataInputStream in)
            throws IOException
    {
        byte[] bitmap = new byte[PRESENCE_BITMAP_BYTES];
        in.readFully(bitmap);
        for (int chunkZ = 0; chunkZ < RegionData.CHUNKS_PER_REGION; chunkZ++)
        {
            for (int chunkX = 0; chunkX < RegionData.CHUNKS_PER_REGION; chunkX++)
            {
                int chunkIndex = chunkZ * RegionData.CHUNKS_PER_REGION + chunkX;
                int mask = 1 << (chunkIndex & 7);
                if ((bitmap[chunkIndex >>> 3] & mask) == 0)
                {
                    continue;
                }
                data.setChunkPresent(chunkX, chunkZ, true);
                for (int pixelZ = 0; pixelZ < RegionData.CHUNK_PIXELS; pixelZ++)
                {
                    for (int pixelX = 0; pixelX < RegionData.CHUNK_PIXELS; pixelX++)
                    {
                        int localX = chunkX * RegionData.CHUNK_PIXELS + pixelX;
                        int localZ = chunkZ * RegionData.CHUNK_PIXELS + pixelZ;
                        data.setColor(localX, localZ, in.readInt());
                    }
                }
                for (int pixelZ = 0; pixelZ < RegionData.CHUNK_PIXELS; pixelZ++)
                {
                    for (int pixelX = 0; pixelX < RegionData.CHUNK_PIXELS; pixelX++)
                    {
                        int localX = chunkX * RegionData.CHUNK_PIXELS + pixelX;
                        int localZ = chunkZ * RegionData.CHUNK_PIXELS + pixelZ;
                        data.setHeight(localX, localZ, in.readShort());
                    }
                }
            }
        }
    }

    private static void writeBody(RegionData data, DataOutputStream out) throws IOException
    {
        RegionKey key = data.key();
        out.writeInt(MAGIC);
        out.writeInt(FORMAT_VERSION);
        out.writeInt(key.regionX());
        out.writeInt(key.regionZ());
        out.writeInt(data.metadataVersion());
        java.util.List<String> palette = data.biomePalette();
        out.writeInt(palette.size());
        for (String id : palette)
        {
            out.writeUTF(id);
        }
        for (int localZ = 0; localZ < RegionData.BLOCKS_PER_REGION; localZ++)
        {
            for (int localX = 0; localX < RegionData.BLOCKS_PER_REGION; localX++)
            {
                out.writeByte(data.biomeIndexAt(localX, localZ));
            }
        }
        byte[] bitmap = new byte[PRESENCE_BITMAP_BYTES];
        for (int chunkZ = 0; chunkZ < RegionData.CHUNKS_PER_REGION; chunkZ++)
        {
            for (int chunkX = 0; chunkX < RegionData.CHUNKS_PER_REGION; chunkX++)
            {
                if (data.isChunkPresent(chunkX, chunkZ))
                {
                    int chunkIndex = chunkZ * RegionData.CHUNKS_PER_REGION + chunkX;
                    bitmap[chunkIndex >>> 3] |= (byte) (1 << (chunkIndex & 7));
                }
            }
        }
        out.write(bitmap);
        for (int chunkZ = 0; chunkZ < RegionData.CHUNKS_PER_REGION; chunkZ++)
        {
            for (int chunkX = 0; chunkX < RegionData.CHUNKS_PER_REGION; chunkX++)
            {
                if (!data.isChunkPresent(chunkX, chunkZ))
                {
                    continue;
                }
                for (int pixelZ = 0; pixelZ < RegionData.CHUNK_PIXELS; pixelZ++)
                {
                    for (int pixelX = 0; pixelX < RegionData.CHUNK_PIXELS; pixelX++)
                    {
                        int localX = chunkX * RegionData.CHUNK_PIXELS + pixelX;
                        int localZ = chunkZ * RegionData.CHUNK_PIXELS + pixelZ;
                        out.writeInt(data.colorAt(localX, localZ));
                    }
                }
                for (int pixelZ = 0; pixelZ < RegionData.CHUNK_PIXELS; pixelZ++)
                {
                    for (int pixelX = 0; pixelX < RegionData.CHUNK_PIXELS; pixelX++)
                    {
                        int localX = chunkX * RegionData.CHUNK_PIXELS + pixelX;
                        int localZ = chunkZ * RegionData.CHUNK_PIXELS + pixelZ;
                        out.writeShort(data.heightAt(localX, localZ));
                    }
                }
            }
        }
    }

    private static IOException corrupt(Path file, String detail)
    {
        return new IOException("corrupt region file " + file + ": " + detail);
    }

    private static String reason(IOException failure)
    {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName() : message;
    }

    private static void moveReplacing(Path source, Path target) throws IOException
    {
        try
        {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException unsupported)
        {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path)
    {
        if (path == null)
        {
            return;
        }
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
        }
    }
}
