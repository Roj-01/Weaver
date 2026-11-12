package net.weaver.minimap.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinimapConfigTest
{
    @TempDir
    Path directory;

    @Test
    void missingFileCreatesDefaults() throws Exception
    {
        Path file = directory.resolve("minimap.json");
        List<String> log = new ArrayList<>();

        MinimapConfig config = MinimapConfig.load(file, log::add);

        assertTrue(Files.isRegularFile(file));
        assertTrue(config.enabled());
        assertEquals(MinimapConfig.Corner.TOP_RIGHT, config.corner());
        assertEquals(160, config.size());
        assertEquals(2.0D, config.zoom(), 0.0001D);
        assertTrue(log.isEmpty());
    }

    @Test
    void roundTripsEveryWritableValue() throws Exception
    {
        Path file = directory.resolve("minimap.json");
        MinimapConfig config = MinimapConfig.load(file, ignored ->
        {
        });
        config.setEnabled(false);
        config.setCorner(MinimapConfig.Corner.BOTTOM_LEFT);
        config.setOffsetX(12);
        config.setOffsetY(14);
        config.setSize(220);
        config.setZoom(3.25D);
        config.setShowCoordinates(false);
        config.setShowCompass(false);
        config.setShowWaypoints(false);
        config.setShowWaypointLabels(false);
        config.setShowPlayers(false);
        config.setShowEntities(true);
        config.setBackgroundOpacity(0.4D);
        config.setWaypointColor(0xFF123456);
        config.save();

        MinimapConfig loaded = MinimapConfig.load(file, ignored ->
        {
        });

        assertFalse(loaded.enabled());
        assertEquals(MinimapConfig.Corner.BOTTOM_LEFT, loaded.corner());
        assertEquals(12, loaded.offsetX());
        assertEquals(14, loaded.offsetY());
        assertEquals(220, loaded.size());
        assertEquals(3.25D, loaded.zoom(), 0.0001D);
        assertFalse(loaded.showCoordinates());
        assertFalse(loaded.showCompass());
        assertFalse(loaded.showWaypoints());
        assertFalse(loaded.showWaypointLabels());
        assertFalse(loaded.showPlayers());
        assertTrue(loaded.showEntities());
        assertEquals(0.4D, loaded.backgroundOpacity(), 0.0001D);
        assertEquals(0xFF123456, loaded.waypointColor());
    }

    @Test
    void clampsOutOfRangeValues() throws Exception
    {
        Path file = directory.resolve("minimap.json");
        Files.writeString(file, """
                {
                  "size": 9999,
                  "zoom": -3.0,
                  "offsetX": -4,
                  "backgroundOpacity": 8.0,
                  "scanChunksPerTick": 0,
                  "saveIntervalTicks": 1,
                  "corner": "NOT_A_CORNER"
                }
                """);

        MinimapConfig config = MinimapConfig.load(file, ignored ->
        {
        });

        assertEquals(MinimapConfig.MAX_SIZE, config.size());
        assertEquals(MinimapConfig.MIN_ZOOM, config.zoom(), 0.0001D);
        assertEquals(0, config.offsetX());
        assertEquals(1.0D, config.backgroundOpacity(), 0.0001D);
        assertEquals(1, config.scanChunksPerTick());
        assertEquals(20, config.saveIntervalTicks());
        assertEquals(MinimapConfig.Corner.TOP_RIGHT, config.corner());
    }

    @Test
    void corruptFileIsBackedUpAndDefaultsAreUsed() throws Exception
    {
        Path file = directory.resolve("minimap.json");
        Files.writeString(file, "{not json");
        List<String> log = new ArrayList<>();

        MinimapConfig config = MinimapConfig.load(file, log::add);

        assertTrue(Files.isRegularFile(directory.resolve("minimap.json.corrupt")));
        assertTrue(config.enabled());
        assertTrue(log.stream().anyMatch(line -> line.contains("config load failed")));
    }
}
