package net.weaver.minimap.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveSliceStorageTest
{
    private static final int MAGIC = 0x57534C49;

    @TempDir
    Path temp;

    @Test
    void roundTripPreservesSparsePixels() throws IOException
    {
        CaveSliceStorage storage = new CaveSliceStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", -2, 5);
        CaveSlice original = new CaveSlice(key, -32);
        original.setPresent(0, 0, true);
        original.setColor(0, 0, 0xFF102030);
        original.setPresent(255, 255, true);
        original.setColor(255, 255, 0xFF405060);
        original.setPresent(17, 200, true);
        original.setColor(17, 200, 0xFF708090);

        storage.save(original);
        CaveSlice loaded = storage.load(key, -32);

        assertEquals(temp.resolve("c.-2.5.-32.wslice"), storage.fileFor(key, -32));
        assertNotNull(loaded);
        assertEquals(key, loaded.key());
        assertEquals(-32, loaded.y());
        assertArrayEquals(original.colors(), loaded.colors());
        assertArrayEquals(original.present(), loaded.present());
    }

    @Test
    void allAirSliceRoundTrips() throws IOException
    {
        CaveSliceStorage storage = new CaveSliceStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 1, 1);
        CaveSlice slice = new CaveSlice(key, 12);

        storage.save(slice);
        CaveSlice loaded = storage.load(key, 12);

        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void absentFileReturnsNull() throws IOException
    {
        CaveSliceStorage storage = new CaveSliceStorage(temp);

        assertNull(storage.load(new RegionKey("minecraft:overworld", 0, 0), 1));
    }

    @Test
    void wrongMagicIsRejected() throws IOException
    {
        CaveSliceStorage storage = new CaveSliceStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        writeCompressed(storage.fileFor(key, 4), 0, 1, 0, 0, 4, null);

        IOException error = assertThrows(IOException.class, () -> storage.load(key, 4));

        assertTrue(error.getMessage().contains("magic"));
        assertTrue(error.getMessage().contains("c.0.0.4.wslice"));
    }

    @Test
    void wrongVersionIsRejected() throws IOException
    {
        CaveSliceStorage storage = new CaveSliceStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        writeCompressed(storage.fileFor(key, 4), MAGIC, 9, 0, 0, 4, null);

        IOException error = assertThrows(IOException.class, () -> storage.load(key, 4));

        assertTrue(error.getMessage().contains("version"));
    }

    @Test
    void mismatchedYIsRejected() throws IOException
    {
        CaveSliceStorage storage = new CaveSliceStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        writeCompressed(storage.fileFor(key, 5), MAGIC, 1, 0, 0, 4, new byte[8192]);

        IOException error = assertThrows(IOException.class, () -> storage.load(key, 5));

        assertTrue(error.getMessage().contains("Y"));
    }

    @Test
    void truncatedFileIsRejected() throws IOException
    {
        CaveSliceStorage storage = new CaveSliceStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        writeCompressed(storage.fileFor(key, 4), MAGIC, 1, 0, 0, 4, null);

        IOException error = assertThrows(IOException.class, () -> storage.load(key, 4));

        assertTrue(error.getMessage().contains("truncated"));
    }

    @Test
    void trailingDataIsRejected() throws IOException
    {
        CaveSliceStorage storage = new CaveSliceStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        writeCompressed(storage.fileFor(key, 4), MAGIC, 1, 0, 0, 4,
                new byte[8193]);

        IOException error = assertThrows(IOException.class, () -> storage.load(key, 4));

        assertTrue(error.getMessage().contains("trailing"));
    }

    @Test
    void nonGzipFileIsRejected() throws IOException
    {
        CaveSliceStorage storage = new CaveSliceStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        Files.writeString(storage.fileFor(key, 4), "not a slice");

        IOException error = assertThrows(IOException.class, () -> storage.load(key, 4));

        assertTrue(error.getMessage().contains("gzip"));
    }

    @Test
    void saveLeavesNoTemporaryFiles() throws IOException
    {
        CaveSliceStorage storage = new CaveSliceStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        CaveSlice slice = new CaveSlice(key, 0);
        slice.setPresent(3, 4, true);
        slice.setColor(3, 4, 0xFF112233);

        storage.save(slice);
        storage.save(slice);

        try (var files = Files.list(temp))
        {
            assertEquals(List.of(temp.resolve("c.0.0.0.wslice")), files.toList());
        }
        assertFalse(storage.load(key, 0).isEmpty());
    }

    @Test
    void quarantineMovesCorruptFileAside() throws IOException
    {
        CaveSliceStorage storage = new CaveSliceStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 3, 4);
        storage.save(new CaveSlice(key, -8));

        Path quarantined = storage.quarantine(key, -8);

        assertNotNull(quarantined);
        assertEquals("c.3.4.-8.wslice.corrupt", quarantined.getFileName().toString());
        assertFalse(Files.exists(storage.fileFor(key, -8)));
        assertNull(storage.load(key, -8));
    }

    private static void writeCompressed(Path file, int magic, int version, int regionX,
                                        int regionZ, int y, byte[] payload)
            throws IOException
    {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }
        try (OutputStream raw = Files.newOutputStream(file);
             GZIPOutputStream gzip = new GZIPOutputStream(raw);
             DataOutputStream out = new DataOutputStream(gzip))
        {
            out.writeInt(magic);
            out.writeInt(version);
            out.writeInt(regionX);
            out.writeInt(regionZ);
            out.writeInt(y);
            if (payload != null)
            {
                out.write(payload);
            }
        }
    }
}
