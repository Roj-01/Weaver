package net.weaver.minimap.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.weaver.minimap.map.MapSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapConfigV2Test
{
    @TempDir
    Path directory;

    private MinimapConfig load(String name)
    {
        return MinimapConfig.load(directory.resolve(name), ignored ->
        {
        });
    }

    @Test
    void defaultsIncludeVersionTwoValues()
    {
        MinimapConfig config = load("defaults.json");

        assertInstanceOf(MapSettings.class, config);
        assertEquals(MinimapConfig.Shape.CIRCLE, config.hudShape());
        assertEquals(MinimapConfig.Rotation.NORTH_UP, config.rotationMode());
        assertTrue(config.showBiome());
        assertTrue(config.showDimension());
        assertTrue(config.showZoom());
        assertFalse(config.showTime());
        assertTrue(config.showOffscreenArrows());
        assertFalse(config.showChunkGrid());
        assertFalse(config.showSlimeChunks());
        assertTrue(config.showWorldBorder());
        assertTrue(config.showSpawn());
        assertTrue(config.hideInChat());
        assertTrue(config.hideInDebug());
        assertTrue(config.hudFadeWhenChat());
        assertEquals(0.35D, config.hudBorderOpacity(), 0.0001D);
        assertTrue(config.hudShadow());
        assertEquals(1.0D, config.hudScale(), 0.0001D);
        assertEquals(0.86D, config.mapBackgroundOpacity(), 0.0001D);
        assertTrue(config.mapSmoothing());
        assertFalse(config.mapShowGrid());
        assertTrue(config.showMapPlayers());
        assertFalse(config.showMapEntities());
        assertTrue(config.showMapWaypoints());
        assertTrue(config.showMapDeaths());
        assertTrue(config.showMapBiomeLayer());
        assertFalse(config.showMapHeightLayer());
        assertTrue(config.showMapCaveLayer());
        assertEquals(1.0D, config.mapPanSpeed(), 0.0001D);
        assertEquals(1.0D, config.mapZoomSensitivity(), 0.0001D);
        assertTrue(config.showHostile());
        assertTrue(config.showPassive());
        assertTrue(config.showItems());
        assertFalse(config.showOtherEntities());
        assertEquals(64, config.entityLimitHud());
        assertEquals(256, config.entityLimitFullscreen());
        assertEquals(MinimapConfig.EntityLabels.NONE, config.entityLabelMode());
        assertTrue(config.deathWaypointEnabled());
        assertEquals(30, config.temporaryWaypointMinutes());
        assertEquals(128.0D, config.waypointLabelDistance(), 0.0001D);
        assertTrue(config.waypointBeamEnabled());
        assertEquals(96.0D, config.waypointBeamDistance(), 0.0001D);
        assertEquals("dark", config.themeId());
        assertEquals(0xFF5CC8FF, config.accentColor());
        assertFalse(config.customAccent());
        assertEquals(0xFF5CC8FF, config.playerColor());
        assertEquals(0x33FFFFFF, config.gridColor());
        assertEquals(0xFFFF5C6C, config.deathColor());
        assertEquals(MinimapConfig.PerformancePreset.BALANCED, config.performancePreset());
        assertEquals(2, config.textureUploadsPerFrame());
        assertEquals(64, config.maxRegionsPerFrame());
        assertTrue(config.hideWithDebugScreen());
        assertTrue(config.respectOtherHud());
        assertFalse(config.disableOnLowMemory());
        assertFalse(config.safeMode());
        assertEquals(0.85D, config.caveTransparency(), 0.0001D);
    }

    @Test
    void versionOneFileKeepsOldValuesAndAddsDefaults() throws Exception
    {
        Path file = directory.resolve("v1.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "enabled": false,
                  "size": 200,
                  "zoom": 3.5,
                  "scanChunksPerTick": 6,
                  "regionCacheSize": 128,
                  "caveY": 12,
                  "showCoordinates": false
                }
                """, StandardCharsets.UTF_8);

        MinimapConfig config = MinimapConfig.load(file, ignored ->
        {
        });

        assertFalse(config.enabled());
        assertEquals(200, config.size());
        assertEquals(3.5D, config.zoom(), 0.0001D);
        assertEquals(6, config.scanChunksPerTick());
        assertEquals(128, config.regionCacheSize());
        assertEquals(12, config.caveY());
        assertFalse(config.showCoordinates());
        assertEquals(MinimapConfig.Shape.CIRCLE, config.hudShape());
        assertEquals(MinimapConfig.PerformancePreset.BALANCED, config.performancePreset());
        assertEquals("dark", config.themeId());
    }

    @Test
    void clampsAndValidatesVersionTwoValues() throws Exception
    {
        Path file = directory.resolve("clamp.json");
        Files.writeString(file, """
                {
                  "version": 2,
                  "hudScale": 99.0,
                  "hudBorderOpacity": 2.0,
                  "mapPanSpeed": 0.0,
                  "mapZoomSensitivity": 9.0,
                  "entityLimitHud": -5,
                  "entityLimitFullscreen": 999999,
                  "temporaryWaypointMinutes": 0,
                  "waypointLabelDistance": 1.0,
                  "waypointBeamDistance": 1000.0,
                  "caveTransparency": -1.0,
                  "textureUploadsPerFrame": 0,
                  "maxRegionsPerFrame": 0,
                  "hudShape": "TRIANGLE",
                  "rotationMode": "SIDEWAYS",
                  "entityLabelMode": "MAYBE",
                  "performancePreset": "TURBO",
                  "themeId": "BAD THEME"
                }
                """, StandardCharsets.UTF_8);

        MinimapConfig config = MinimapConfig.load(file, ignored ->
        {
        });

        assertEquals(MinimapConfig.MAX_HUD_SCALE, config.hudScale(), 0.0001D);
        assertEquals(1.0D, config.hudBorderOpacity(), 0.0001D);
        assertEquals(0.25D, config.mapPanSpeed(), 0.0001D);
        assertEquals(4.0D, config.mapZoomSensitivity(), 0.0001D);
        assertEquals(0, config.entityLimitHud());
        assertEquals(4096, config.entityLimitFullscreen());
        assertEquals(1, config.temporaryWaypointMinutes());
        assertEquals(8.0D, config.waypointLabelDistance(), 0.0001D);
        assertEquals(512.0D, config.waypointBeamDistance(), 0.0001D);
        assertEquals(0.0D, config.caveTransparency(), 0.0001D);
        assertEquals(1, config.textureUploadsPerFrame());
        assertEquals(1, config.maxRegionsPerFrame());
        assertEquals(MinimapConfig.Shape.CIRCLE, config.hudShape());
        assertEquals(MinimapConfig.Rotation.NORTH_UP, config.rotationMode());
        assertEquals(MinimapConfig.EntityLabels.NONE, config.entityLabelMode());
        assertEquals(MinimapConfig.PerformancePreset.BALANCED, config.performancePreset());
        assertEquals("dark", config.themeId());
    }

    @Test
    void presetsApplyCoherentBudgets()
    {
        MinimapConfig config = load("preset.json");

        config.setPerformancePreset(MinimapConfig.PerformancePreset.LOW);
        assertEquals(1, config.textureUploadsPerFrame());
        assertEquals(32, config.maxRegionsPerFrame());
        assertEquals(2, config.scanChunksPerTick());
        assertEquals(1, config.caveScanChunksPerTick());
        assertEquals(48, config.regionCacheSize());
        assertEquals(32, config.caveCacheSize());
        assertEquals(400, config.saveIntervalTicks());

        config.setPerformancePreset(MinimapConfig.PerformancePreset.ULTRA);
        assertEquals(8, config.textureUploadsPerFrame());
        assertEquals(256, config.maxRegionsPerFrame());
        assertEquals(16, config.scanChunksPerTick());
        assertEquals(8, config.caveScanChunksPerTick());
        assertEquals(384, config.regionCacheSize());
        assertEquals(224, config.caveCacheSize());
        assertEquals(60, config.saveIntervalTicks());

        for (MinimapConfig.PerformancePreset preset : MinimapConfig.PerformancePreset.values())
        {
            config.setPerformancePreset(preset);
            assertTrue(config.textureUploadsPerFrame() >= 1
                    && config.textureUploadsPerFrame() <= 16);
            assertTrue(config.maxRegionsPerFrame() >= 1
                    && config.maxRegionsPerFrame() <= 512);
            assertTrue(config.scanChunksPerTick() >= 1
                    && config.scanChunksPerTick() <= 16);
            assertTrue(config.caveScanChunksPerTick() >= 1
                    && config.caveScanChunksPerTick() <= 16);
            assertTrue(config.regionCacheSize() >= 16
                    && config.regionCacheSize() <= 512);
            assertTrue(config.caveCacheSize() >= 8 && config.caveCacheSize() <= 256);
            assertTrue(config.saveIntervalTicks() >= 20
                    && config.saveIntervalTicks() <= 6000);
        }
    }

    @Test
    void resetSectionLeavesOtherSectionsAlone()
    {
        MinimapConfig config = load("reset-section.json");
        config.setSize(300);
        config.setHudScale(1.4D);
        config.setShowBiome(false);
        config.setAccentColor(0xFF010203);
        config.setPerformancePreset(MinimapConfig.PerformancePreset.LOW);

        config.resetSection("hud");

        assertEquals(300, config.size());
        assertEquals(1.0D, config.hudScale(), 0.0001D);
        assertTrue(config.showBiome());
        assertEquals(0xFF010203, config.accentColor());
        assertEquals(MinimapConfig.PerformancePreset.LOW, config.performancePreset());
    }

    @Test
    void resetMinimapAlsoResetsGeneralLayout()
    {
        MinimapConfig config = load("reset-minimap.json");
        config.setSize(300);
        config.setCorner(MinimapConfig.Corner.BOTTOM_LEFT);
        config.setHudScale(1.4D);

        config.resetSection("minimap");

        assertEquals(160, config.size());
        assertEquals(MinimapConfig.Corner.TOP_RIGHT, config.corner());
        assertEquals(1.0D, config.hudScale(), 0.0001D);
    }

    @Test
    void resetAllRestoresEveryDefault()
    {
        MinimapConfig config = load("reset-all.json");
        config.setEnabled(false);
        config.setSize(300);
        config.setHudShape(MinimapConfig.Shape.ROUNDED_SQUARE);
        config.setPerformancePreset(MinimapConfig.PerformancePreset.ULTRA);
        config.setThemeId("emerald");
        config.setAccentColor(1);
        config.setCaveTransparency(0.9D);
        config.setSafeMode(true);

        config.resetAll();

        assertTrue(config.enabled());
        assertEquals(160, config.size());
        assertEquals(MinimapConfig.Shape.CIRCLE, config.hudShape());
        assertEquals(MinimapConfig.PerformancePreset.BALANCED, config.performancePreset());
        assertEquals(2, config.textureUploadsPerFrame());
        assertEquals("dark", config.themeId());
        assertEquals(0xFF5CC8FF, config.accentColor());
        assertEquals(0.85D, config.caveTransparency(), 0.0001D);
        assertFalse(config.safeMode());
    }

    @Test
    void sectionAliasesAreAccepted()
    {
        MinimapConfig config = load("alias.json");
        config.setMapShowGrid(false);
        config.setThemeId("emerald");

        config.resetSection("map");
        config.resetSection("appearance");

        assertFalse(config.mapShowGrid());
        assertEquals("dark", config.themeId());
    }

    @Test
    void unknownSectionIsRejected()
    {
        MinimapConfig config = load("bad-section.json");

        assertThrows(IllegalArgumentException.class, () -> config.resetSection("nonsense"));
        assertTrue(MinimapConfig.sections().contains("hud"));
        assertTrue(MinimapConfig.sections().contains("performance"));
    }

    @Test
    void exportImportRoundTripsEverySnapshotValue()
    {
        MinimapConfig source = load("source.json");
        source.setEnabled(false);
        source.setCorner(MinimapConfig.Corner.BOTTOM_LEFT);
        source.setSize(220);
        source.setHudShape(MinimapConfig.Shape.ROUNDED_SQUARE);
        source.setRotationMode(MinimapConfig.Rotation.PLAYER_UP);
        source.setShowTime(true);
        source.setShowChunkGrid(true);
        source.setHudScale(1.3D);
        source.setMapBackgroundOpacity(0.5D);
        source.setShowMapHeightLayer(true);
        source.setEntityLabelMode(MinimapConfig.EntityLabels.ALWAYS);
        source.setTemporaryWaypointMinutes(45);
        source.setDeathWaypointEnabled(false);
        source.setWaypointBeamEnabled(true);
        source.setThemeId("EMERALD");
        source.setAccentColor(0xFF112233);
        source.setCustomAccent(true);
        source.setPerformancePreset(MinimapConfig.PerformancePreset.HIGH);
        source.setHideWithDebugScreen(false);
        source.setDisableOnLowMemory(true);
        source.setCaveTransparency(0.8D);

        MinimapConfig target = load("target.json");
        target.importJson(source.exportJson());

        assertEquals(source.snapshot(), target.snapshot());
        assertEquals("emerald", target.themeId());
    }

    @Test
    void importRejectsMalformedText()
    {
        MinimapConfig config = load("import.json");

        assertThrows(IllegalArgumentException.class, () -> config.importJson("{not json"));
        assertThrows(IllegalArgumentException.class, () -> config.importJson("[1, 2, 3]"));
    }

    @Test
    void snapshotMirrorsAccessors()
    {
        MinimapConfig config = load("snapshot.json");
        config.setSize(210);
        config.setHudScale(1.2D);
        config.setPerformancePreset(MinimapConfig.PerformancePreset.HIGH);
        config.setThemeId("midnight");

        MinimapConfig.Snapshot snapshot = config.snapshot();

        assertEquals(config.size(), snapshot.size());
        assertEquals(config.zoom(), snapshot.zoom(), 0.0001D);
        assertEquals(config.hudShape(), snapshot.hudShape());
        assertEquals(config.rotationMode(), snapshot.rotationMode());
        assertEquals(config.hudScale(), snapshot.hudScale(), 0.0001D);
        assertEquals(config.performancePreset(), snapshot.performancePreset());
        assertEquals(config.textureUploadsPerFrame(), snapshot.textureUploadsPerFrame());
        assertEquals(config.regionCacheSize(), snapshot.regionCacheSize());
        assertEquals(config.caveCacheSize(), snapshot.caveCacheSize());
        assertEquals(config.saveIntervalTicks(), snapshot.saveIntervalTicks());
        assertEquals(config.themeId(), snapshot.themeId());
        assertEquals(config.deathColor(), snapshot.deathColor());
    }

    @Test
    void saveWritesVersionTwoAndEverySection() throws Exception
    {
        Path file = directory.resolve("saved.json");
        MinimapConfig config = MinimapConfig.load(file, ignored ->
        {
        });
        config.save();

        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals(2, root.get("version").getAsInt());
        assertTrue(root.has("hudShape"));
        assertTrue(root.has("rotationMode"));
        assertTrue(root.has("showBiome"));
        assertTrue(root.has("mapBackgroundOpacity"));
        assertTrue(root.has("showHostile"));
        assertTrue(root.has("temporaryWaypointMinutes"));
        assertTrue(root.has("themeId"));
        assertTrue(root.has("performancePreset"));
        assertTrue(root.has("safeMode"));
        assertTrue(root.has("caveTransparency"));
        assertEquals(160, root.get("size").getAsInt());
    }

    @Test
    void themeIdIsNormalized()
    {
        MinimapConfig config = load("theme.json");

        config.setThemeId("  Midnight  ");
        assertEquals("midnight", config.themeId());
        config.setThemeId("bad theme!");
        assertEquals("dark", config.themeId());
    }
}
