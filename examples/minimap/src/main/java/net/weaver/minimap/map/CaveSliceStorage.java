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
 * Reads and writes gzip-compressed cave slices in a {@code .wslice} container.
 *
 * <p>The uncompressed layout is:</p>
 *
 * <ol>
 *   <li>magic {@code 0x57534C49} ({@code "WSLI"}) as an int</li>
 *   <li>format version {@code 1} as an int</li>
 *   <li>region X, region Z, and Y as ints</li>
 *   <li>an 8192-byte presence bitmap, one bit per pixel, least-significant bit
 *       first, indexed by {@code localZ * 256 + localX}</li>
 *   <li>one ARGB int for each present pixel in the same order</li>
 * </ol>
 *
 * <p>Writes use a sibling temporary file and an atomic move. Corrupt,
 * truncated, or trailing data is rejected with an {@link IOException}.</p>
 */
public final class CaveSliceStorage
{
    private static final int MAGIC = 0x57534C49;
    private static final int FORMAT_VERSION = 1;
    private static final int PRESENCE_BITMAP_BYTES =
            RegionData.BLOCKS_PER_REGION * RegionData.BLOCKS_PER_REGION / 8;

    private final Path directory;

    /**
     * Creates storage rooted at a dedicated cave directory.
     *
     * @param directory the directory holding {@code .wslice} files, never
     *                  {@code null}
     * @throws NullPointerException when {@code directory} is {@code null}
     */
    public CaveSliceStorage(Path directory)
    {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /**
     * Returns the file used for a slice.
     *
     * @param key the region key, never {@code null}
     * @param y   the sampled Y level
     * @return the slice file path
     * @throws NullPointerException when {@code key} is {@code null}
     */
    public Path fileFor(RegionKey key, int y)
    {
        Objects.requireNonNull(key, "key");
        return directory.resolve("c." + key.regionX() + "." + key.regionZ() + "." + y
                + ".wslice");
    }

    /**
     * Loads one slice.
     *
     * @param key the region key, never {@code null}
     * @param y   the sampled Y level
     * @return the loaded slice, or {@code null} when the file does not exist
     * @throws NullPointerException when {@code key} is {@code null}
     * @throws IOException          when the file cannot be read or is corrupt
     */
    public CaveSlice load(RegionKey key, int y) throws IOException
    {
        Objects.requireNonNull(key, "key");
        Path file = fileFor(key, y);
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
            throw new IOException("cannot read cave slice file " + file, failure);
        }
        CaveSlice slice;
        try (InputStream raw = new ByteArrayInputStream(compressed);
             InputStream buffered = new BufferedInputStream(raw);
             InputStream gzip = new GZIPInputStream(buffered);
             DataInputStream in = new DataInputStream(gzip))
        {
            slice = readBody(key, y, file, in);
        }
        catch (EOFException truncated)
        {
            throw new IOException("truncated cave slice file " + file, truncated);
        }
        catch (ZipException invalid)
        {
            throw new IOException("corrupt cave slice file " + file
                    + ": invalid gzip data", invalid);
        }
        catch (IOException failure)
        {
            String message = failure.getMessage();
            if (message != null && message.startsWith("corrupt cave slice file "))
            {
                throw failure;
            }
            throw new IOException("cannot read cave slice file " + file + ": "
                    + reason(failure), failure);
        }
        return slice;
    }

    /**
     * Saves one slice atomically. All-air slices are valid and serialize to an
     * empty presence bitmap.
     *
     * @param slice the slice, never {@code null}
     * @throws NullPointerException when {@code slice} is {@code null}
     * @throws IOException          when the file cannot be written
     */
    public void save(CaveSlice slice) throws IOException
    {
        Objects.requireNonNull(slice, "slice");
        Path file = fileFor(slice.key(), slice.y());
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null)
        {
            throw new IOException("cave slice file has no parent directory: " + file);
        }
        Path temporary = null;
        try
        {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent,
                    "c." + slice.key().regionX() + "." + slice.key().regionZ()
                            + "." + slice.y() + ".", ".tmp");
            try (OutputStream raw = Files.newOutputStream(temporary);
                 OutputStream buffered = new BufferedOutputStream(raw);
                 GZIPOutputStream gzip = new GZIPOutputStream(buffered);
                 DataOutputStream out = new DataOutputStream(gzip))
            {
                writeBody(slice, out);
            }
            moveReplacing(temporary, file);
            temporary = null;
        }
        catch (IOException | RuntimeException failure)
        {
            throw new IOException("cannot write cave slice file " + file, failure);
        }
        finally
        {
            deleteQuietly(temporary);
        }
    }

    /**
     * Moves a corrupt slice file aside.
     *
     * @param key the region key, never {@code null}
     * @param y   the sampled Y level
     * @return the quarantine path, or {@code null} when no file exists
     * @throws NullPointerException when {@code key} is {@code null}
     * @throws IOException          when the file cannot be moved
     */
    public Path quarantine(RegionKey key, int y) throws IOException
    {
        Objects.requireNonNull(key, "key");
        Path file = fileFor(key, y);
        if (Files.notExists(file))
        {
            return null;
        }
        Path target = file.resolveSibling(file.getFileName() + ".corrupt");
        moveReplacing(file, target);
        return target;
    }

    private static CaveSlice readBody(RegionKey key, int y, Path file, DataInputStream in)
            throws IOException
    {
        int magic = in.readInt();
        if (magic != MAGIC)
        {
            throw corrupt(file, "wrong magic 0x" + Integer.toHexString(magic));
        }
        int version = in.readInt();
        if (version != FORMAT_VERSION)
        {
            throw corrupt(file, "unsupported format version " + version);
        }
        int regionX = in.readInt();
        int regionZ = in.readInt();
        int storedY = in.readInt();
        if (regionX != key.regionX() || regionZ != key.regionZ())
        {
            throw corrupt(file, "stored region " + regionX + "," + regionZ
                    + " does not match requested region " + key.regionX() + ","
                    + key.regionZ());
        }
        if (storedY != y)
        {
            throw corrupt(file, "stored Y " + storedY + " does not match requested Y " + y);
        }
        byte[] bitmap = new byte[PRESENCE_BITMAP_BYTES];
        in.readFully(bitmap);
        CaveSlice slice = new CaveSlice(key, y);
        for (int localZ = 0; localZ < RegionData.BLOCKS_PER_REGION; localZ++)
        {
            int row = localZ * RegionData.BLOCKS_PER_REGION;
            for (int localX = 0; localX < RegionData.BLOCKS_PER_REGION; localX++)
            {
                int pixel = row + localX;
                int mask = 1 << (pixel & 7);
                if ((bitmap[pixel >>> 3] & mask) == 0)
                {
                    continue;
                }
                slice.setPresent(localX, localZ, true);
                slice.setColor(localX, localZ, in.readInt());
            }
        }
        if (in.read() != -1)
        {
            throw corrupt(file, "trailing data after cave slice payload");
        }
        return slice;
    }

    private static void writeBody(CaveSlice slice, DataOutputStream out) throws IOException
    {
        out.writeInt(MAGIC);
        out.writeInt(FORMAT_VERSION);
        out.writeInt(slice.key().regionX());
        out.writeInt(slice.key().regionZ());
        out.writeInt(slice.y());
        byte[] bitmap = new byte[PRESENCE_BITMAP_BYTES];
        for (int localZ = 0; localZ < RegionData.BLOCKS_PER_REGION; localZ++)
        {
            int row = localZ * RegionData.BLOCKS_PER_REGION;
            for (int localX = 0; localX < RegionData.BLOCKS_PER_REGION; localX++)
            {
                if (slice.isPresent(localX, localZ))
                {
                    int pixel = row + localX;
                    bitmap[pixel >>> 3] |= (byte) (1 << (pixel & 7));
                }
            }
        }
        out.write(bitmap);
        for (int localZ = 0; localZ < RegionData.BLOCKS_PER_REGION; localZ++)
        {
            for (int localX = 0; localX < RegionData.BLOCKS_PER_REGION; localX++)
            {
                if (slice.isPresent(localX, localZ))
                {
                    out.writeInt(slice.colorAt(localX, localZ));
                }
            }
        }
    }

    private static IOException corrupt(Path file, String detail)
    {
        return new IOException("corrupt cave slice file " + file + ": " + detail);
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
