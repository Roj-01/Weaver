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

class RegionStorageTest
{
    private static final int MAGIC = 0x574D4150;

    @TempDir
    Path temp;

    @Test
    void roundTripPreservesSparseChunksAndNegativeCoordinates() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", -2, -3);
        RegionData original = new RegionData(key);
        original.setChunkPresent(0, 0, true);
        original.setColor(0, 0, 0xFF112233);
        original.setHeight(0, 0, (short) -64);
        original.setColor(15, 15, 0x80445566);
        original.setHeight(15, 15, (short) 100);
        original.setChunkPresent(15, 0, true);
        original.setColor(240, 3, 0xFF00FF00);
        original.setHeight(240, 3, (short) 200);
        original.setChunkPresent(7, 9, true);
        original.setColor(120, 150, 0x80ABCDEF);
        original.setHeight(120, 150, (short) -20);
        original.setChunkPresent(15, 15, true);
        original.setColor(255, 255, 0xFFFFFFFF);
        original.setHeight(255, 255, (short) 320);
        original.markDirty();

        storage.save(original);
        RegionData loaded = storage.load(key);

        assertEquals(temp.resolve("r.-2.-3.wmap"), storage.fileFor(key));
        assertTrue(Files.exists(storage.fileFor(key)));
        assertNotNull(loaded);
        assertEquals(key, loaded.key());
        assertArrayEquals(original.colors(), loaded.colors());
        assertArrayEquals(original.heights(), loaded.heights());
        assertArrayEquals(original.present(), loaded.present());
        assertFalse(loaded.isDirty());
        assertFalse(loaded.isEmpty());
    }

    @Test
    void absentFileReturnsNull() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);

        assertNull(storage.load(new RegionKey("minecraft:overworld", 5, 5)));
    }

    @Test
    void wrongMagicIsRejected() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        writeCompressed(storage.fileFor(key), 0, 1, 0, 0, new byte[32]);

        IOException error = assertThrows(IOException.class, () -> storage.load(key));

        assertTrue(error.getMessage().contains(key.fileName()));
        assertTrue(error.getMessage().contains("magic"));
    }

    @Test
    void wrongVersionIsRejected() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        writeCompressed(storage.fileFor(key), MAGIC, 99, 0, 0, new byte[32]);

        IOException error = assertThrows(IOException.class, () -> storage.load(key));

        assertTrue(error.getMessage().contains(key.fileName()));
        assertTrue(error.getMessage().contains("version"));
    }

    @Test
    void versionOneFileIsReadWithDefaultMetadata() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 1, -2);
        writeVersionOne(storage.fileFor(key), key);

        RegionData loaded = storage.load(key);

        assertNotNull(loaded);
        assertEquals(key, loaded.key());
        assertEquals(0xFF010203, loaded.colorAt(0, 0));
        assertEquals(42, loaded.heightAt(0, 0));
        assertEquals(0xFF040506, loaded.colorAt(255, 255));
        assertEquals((short) -12, loaded.heightAt(255, 255));
        assertTrue(loaded.isChunkPresent(0, 0));
        assertTrue(loaded.isChunkPresent(15, 15));
        assertFalse(loaded.isChunkPresent(1, 1));
        assertEquals(RegionData.METADATA_VERSION, loaded.metadataVersion());
        assertEquals(RegionData.UNKNOWN_BIOME, loaded.biomeAt(0, 0));
        assertEquals(1, loaded.biomePaletteSize());
        assertTrue(loaded.isMipDirty());
    }

    @Test
    void versionOneFileCanBeResavedAsVersionTwo() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 2, 2);
        writeVersionOne(storage.fileFor(key), key);
        RegionData legacy = storage.load(key);

        storage.save(legacy);
        RegionData reloaded = storage.load(key);

        assertNotNull(reloaded);
        assertEquals(RegionData.METADATA_VERSION, reloaded.metadataVersion());
        assertEquals(legacy.biomePalette(), reloaded.biomePalette());
        assertArrayEquals(legacy.colors(), reloaded.colors());
        assertArrayEquals(legacy.heights(), reloaded.heights());
        assertArrayEquals(legacy.present(), reloaded.present());
    }

    @Test
    void versionTwoRoundTripPreservesBiomesAndMetadata() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", -4, 3);
        RegionData original = new RegionData(key);
        original.setMetadataVersion(7);
        original.setChunkPresent(0, 0, true);
        original.setChunkPresent(9, 12, true);
        original.setColor(5, 6, 0xFF102030);
        original.setHeight(5, 6, (short) 96);
        original.setColor(150, 200, 0xFF405060);
        original.setHeight(150, 200, (short) -32);
        int plains = original.biomeIndex("minecraft:plains");
        int desert = original.biomeIndex("minecraft:desert");
        original.setBiomeIndex(5, 6, plains);
        original.setBiomeIndex(150, 200, desert);

        storage.save(original);
        RegionData loaded = storage.load(key);

        assertNotNull(loaded);
        assertEquals(7, loaded.metadataVersion());
        assertEquals(original.biomePalette(), loaded.biomePalette());
        assertEquals("minecraft:plains", loaded.biomeAt(5, 6));
        assertEquals("minecraft:desert", loaded.biomeAt(150, 200));
        assertArrayEquals(original.colors(), loaded.colors());
        assertArrayEquals(original.heights(), loaded.heights());
        assertArrayEquals(original.present(), loaded.present());
    }

    @Test
    void truncatedVersionTwoFileIsRejected() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        Path file = storage.fileFor(key);
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (OutputStream raw = Files.newOutputStream(file);
             GZIPOutputStream gzip = new GZIPOutputStream(raw);
             DataOutputStream out = new DataOutputStream(gzip))
        {
            out.writeInt(MAGIC);
            out.writeInt(2);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(1);
            out.writeInt(1);
            out.writeUTF("unknown");
            out.write(new byte[10]);
        }

        IOException error = assertThrows(IOException.class, () -> storage.load(key));

        assertTrue(error.getMessage().contains(key.fileName()));
        assertTrue(error.getMessage().contains("truncated"));
    }

    @Test
    void invalidBiomePaletteIsRejected() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        Path file = storage.fileFor(key);
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (OutputStream raw = Files.newOutputStream(file);
             GZIPOutputStream gzip = new GZIPOutputStream(raw);
             DataOutputStream out = new DataOutputStream(gzip))
        {
            out.writeInt(MAGIC);
            out.writeInt(2);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(1);
            out.writeInt(300);
        }

        IOException error = assertThrows(IOException.class, () -> storage.load(key));

        assertTrue(error.getMessage().contains("palette"));
    }

    @Test
    void quarantineMovesCorruptFileAside() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 2, 2);
        RegionData data = new RegionData(key);
        data.setChunkPresent(0, 0, true);
        storage.save(data);

        Path quarantined = storage.quarantine(key);

        assertNotNull(quarantined);
        assertEquals("r.2.2.wmap.corrupt", quarantined.getFileName().toString());
        assertFalse(Files.exists(storage.fileFor(key)));
        assertTrue(Files.exists(quarantined));
        assertNull(storage.load(key));
    }

    @Test
    void truncatedFileIsRejected() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        writeCompressed(storage.fileFor(key), MAGIC, 1, 0, 0, null);

        IOException error = assertThrows(IOException.class, () -> storage.load(key));

        assertTrue(error.getMessage().contains(key.fileName()));
        assertTrue(error.getMessage().contains("truncated"));
    }

    @Test
    void nonGzipFileIsRejected() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        Files.writeString(storage.fileFor(key), "not a region file");

        IOException error = assertThrows(IOException.class, () -> storage.load(key));

        assertTrue(error.getMessage().contains(key.fileName()));
        assertTrue(error.getMessage().contains("gzip"));
    }

    @Test
    void emptyRegionCannotBeSaved() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 1, 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> storage.save(new RegionData(key)));

        assertTrue(error.getMessage().contains("empty"));
        assertFalse(Files.exists(storage.fileFor(key)));
    }

    @Test
    void saveReplacesAtomicallyAndLeavesNoTemporaryFiles() throws IOException
    {
        RegionStorage storage = new RegionStorage(temp);
        RegionKey key = new RegionKey("minecraft:overworld", 0, 0);
        RegionData data = new RegionData(key);
        data.setChunkPresent(0, 0, true);
        data.setColor(1, 2, 0xFF000001);

        storage.save(data);
        data.setColor(1, 2, 0xFF000002);
        storage.save(data);

        RegionData loaded = storage.load(key);
        assertNotNull(loaded);
        assertEquals(0xFF000002, loaded.colorAt(1, 2));
        try (var files = Files.list(temp))
        {
            assertEquals(List.of(temp.resolve("r.0.0.wmap")), files.toList());
        }
    }

    @Test
    void saveCreatesParentDirectories() throws IOException
    {
        Path nested = temp.resolve("a").resolve("b");
        RegionStorage storage = new RegionStorage(nested);
        RegionKey key = new RegionKey("minecraft:overworld", -1, 2);
        RegionData data = new RegionData(key);
        data.setChunkPresent(3, 4, true);
        data.setColor(48, 64, 0xFF123456);

        storage.save(data);

        assertTrue(Files.exists(nested.resolve("r.-1.2.wmap")));
        assertEquals(0xFF123456, storage.load(key).colorAt(48, 64));
    }

    private static void writeVersionOne(Path file, RegionKey key) throws IOException
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
            out.writeInt(MAGIC);
            out.writeInt(1);
            out.writeInt(key.regionX());
            out.writeInt(key.regionZ());
            byte[] bitmap = new byte[32];
            bitmap[0] = 1;
            bitmap[31] = (byte) 0x80;
            out.write(bitmap);
            for (int pixel = 0; pixel < 256; pixel++)
            {
                out.writeInt(0xFF010203);
            }
            for (int pixel = 0; pixel < 256; pixel++)
            {
                out.writeShort(42);
            }
            for (int pixel = 0; pixel < 256; pixel++)
            {
                out.writeInt(0xFF040506);
            }
            for (int pixel = 0; pixel < 256; pixel++)
            {
                out.writeShort(-12);
            }
        }
    }

    private static void writeCompressed(Path file, int magic, int version, int regionX,
                                        int regionZ, byte[] payload) throws IOException
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
            if (payload != null)
            {
                out.write(payload);
            }
        }
    }
}
