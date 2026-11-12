package net.weaver.minimap.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.weaver.minimap.map.MapSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Client-side minimap settings, stored as JSON.
 *
 * <p>The file is deliberately separate from the Weaver typed config API so the
 * mod can compile against the public API only and still expose a small,
 * self-contained settings file. Missing fields use defaults; invalid values
 * are clamped and logged. Writes are atomic and corrupt files are moved to a
 * {@code .corrupt} sibling before defaults are used.</p>
 *
 * <p>Version 1 files remain readable; version 2 adds the HUD, fullscreen,
 * entity, waypoint, theme, performance, compatibility, and cave settings
 * required by the v2 UI. Every existing version 1 key and accessor is kept.</p>
 */
public final class MinimapConfig implements MapSettings
{
    /**
     * The screen corner that anchors the HUD minimap.
     */
    public enum Corner
    {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    /** The HUD mask shape. */
    public enum Shape
    {
        CIRCLE,
        ROUNDED_SQUARE
    }

    /** How the HUD map is rotated. */
    public enum Rotation
    {
        NORTH_UP,
        PLAYER_UP
    }

    /** When entity labels are drawn. */
    public enum EntityLabels
    {
        NONE,
        HOVER,
        ALWAYS
    }

    /** The coherent performance preset. */
    public enum PerformancePreset
    {
        LOW,
        BALANCED,
        HIGH,
        ULTRA
    }

    public static final int MIN_SIZE = 64;
    public static final int MAX_SIZE = 320;
    public static final double MIN_ZOOM = 0.5;
    public static final double MAX_ZOOM = 8.0;
    public static final double MIN_HUD_SCALE = 0.75;
    public static final double MAX_HUD_SCALE = 1.5;

    /** The default theme id. */
    public static final String DEFAULT_THEME_ID = "dark";

    private static final int VERSION = 2;
    private static final Pattern THEME_ID = Pattern.compile("[a-z0-9_.-]{1,32}");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path file;
    private boolean enabled = true;
    private Corner corner = Corner.TOP_RIGHT;
    private int offsetX = 8;
    private int offsetY = 8;
    private int size = 160;
    private double zoom = 2.0;
    private boolean showCoordinates = true;
    private boolean showCompass = true;
    private boolean showWaypoints = true;
    private boolean showWaypointLabels = true;
    private boolean showPlayers = true;
    private boolean showEntities;
    private double backgroundOpacity = 0.62;
    private int scanChunksPerTick = 4;
    private int saveIntervalTicks = 200;
    private int waypointColor = 0xFFFFAA00;
    private int regionCacheSize = 96;
    private int caveCacheSize = 64;
    private boolean caveEnabled = false;
    private boolean caveAuto = true;
    private int caveY = -32;
    private boolean caveSurfaceOverlay = true;
    private double caveSurfaceOpacity = 0.35;
    private int caveScanChunksPerTick = 2;
    private double caveTransparency = 0.85;

    private Shape hudShape = Shape.CIRCLE;
    private Rotation rotationMode = Rotation.NORTH_UP;
    private boolean showBiome = true;
    private boolean showDimension = true;
    private boolean showZoom = true;
    private boolean showTime;
    private boolean showOffscreenArrows = true;
    private boolean showChunkGrid;
    private boolean showSlimeChunks;
    private boolean showWorldBorder = true;
    private boolean showSpawn = true;
    private boolean hideInChat = true;
    private boolean hideInDebug = true;
    private boolean hudFadeWhenChat = true;
    private double hudBorderOpacity = 0.35;
    private boolean hudShadow = true;
    private double hudScale = 1.0;

    private double mapBackgroundOpacity = 0.86;
    private boolean mapSmoothing = true;
    private boolean mapShowGrid = false;
    private boolean showMapPlayers = true;
    private boolean showMapEntities = false;
    private boolean showMapWaypoints = true;
    private boolean showMapDeaths = true;
    private boolean showMapBiomeLayer = true;
    private boolean showMapHeightLayer;
    private boolean showMapCaveLayer = true;
    private double mapPanSpeed = 1.0;
    private double mapZoomSensitivity = 1.0;

    private boolean showHostile = true;
    private boolean showPassive = true;
    private boolean showItems = true;
    private boolean showOtherEntities;
    private int entityLimitHud = 64;
    private int entityLimitFullscreen = 256;
    private EntityLabels entityLabelMode = EntityLabels.NONE;

    private boolean deathWaypointEnabled = true;
    private int temporaryWaypointMinutes = 30;
    private double waypointLabelDistance = 128.0;
    private boolean waypointBeamEnabled = true;
    private double waypointBeamDistance = 96.0;

    private String themeId = DEFAULT_THEME_ID;
    private int accentColor = 0xFF5CC8FF;
    private boolean customAccent;
    private int playerColor = 0xFF5CC8FF;
    private int gridColor = 0x33FFFFFF;
    private int deathColor = 0xFFFF5C6C;

    private PerformancePreset performancePreset = PerformancePreset.BALANCED;
    private int textureUploadsPerFrame = 2;
    private int maxRegionsPerFrame = 64;

    private boolean hideWithDebugScreen = true;
    private boolean respectOtherHud = true;
    private boolean disableOnLowMemory;
    private boolean safeMode;

    private MinimapConfig(Path file)
    {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Loads a configuration file, creating defaults when it is absent.
     *
     * @param file the JSON file, never {@code null}
     * @param log  a log sink, never {@code null}
     * @return the loaded configuration, never {@code null}
     */
    public static MinimapConfig load(Path file, Consumer<String> log)
    {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(log, "log");
        MinimapConfig config = new MinimapConfig(file);
        if (Files.notExists(file))
        {
            config.saveQuietly(log);
            return config;
        }
        try
        {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank())
            {
                return config;
            }
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject())
            {
                throw new IllegalArgumentException("root must be a JSON object");
            }
            config.read(parsed.getAsJsonObject(), log);
        }
        catch (RuntimeException | IOException failure)
        {
            log.accept("[weaver-minimap] config load failed: " + failure);
            config.backupCorrupt(log);
        }
        return config;
    }

    /** The backing file. */
    public Path file()
    {
        return file;
    }

    /** Whether the HUD minimap is visible. */
    public boolean enabled()
    {
        return enabled;
    }

    /** Sets whether the HUD minimap is visible. */
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /** The anchor corner. */
    public Corner corner()
    {
        return corner;
    }

    /** Sets the anchor corner. */
    public void setCorner(Corner corner)
    {
        this.corner = Objects.requireNonNull(corner, "corner");
    }

    /** The horizontal offset from the anchored corner. */
    public int offsetX()
    {
        return offsetX;
    }

    /** The vertical offset from the anchored corner. */
    public int offsetY()
    {
        return offsetY;
    }

    /** Sets the horizontal offset, clamped to a sensible range. */
    public void setOffsetX(int offsetX)
    {
        this.offsetX = clamp(offsetX, 0, 4096);
    }

    /** Sets the vertical offset, clamped to a sensible range. */
    public void setOffsetY(int offsetY)
    {
        this.offsetY = clamp(offsetY, 0, 4096);
    }

    /** The HUD map width and height in pixels. */
    @Override
    public int size()
    {
        return size;
    }

    /** Sets the HUD map size, clamped to the supported range. */
    public void setSize(int size)
    {
        this.size = clamp(size, MIN_SIZE, MAX_SIZE);
    }

    /** Blocks shown per pixel. */
    @Override
    public double zoom()
    {
        return zoom;
    }

    /** Sets the zoom, clamped to the supported range. */
    public void setZoom(double zoom)
    {
        this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    }

    /** Multiplies the zoom by a factor. */
    public void zoomBy(double factor)
    {
        setZoom(zoom * factor);
    }

    /** Whether the coordinate readout is shown. */
    public boolean showCoordinates()
    {
        return showCoordinates;
    }

    /** Sets whether the coordinate readout is shown. */
    public void setShowCoordinates(boolean showCoordinates)
    {
        this.showCoordinates = showCoordinates;
    }

    /** Whether the compass letters are shown. */
    public boolean showCompass()
    {
        return showCompass;
    }

    /** Sets whether the compass letters are shown. */
    public void setShowCompass(boolean showCompass)
    {
        this.showCompass = showCompass;
    }

    /** Whether waypoints are shown. */
    public boolean showWaypoints()
    {
        return showWaypoints;
    }

    /** Sets whether waypoints are shown. */
    public void setShowWaypoints(boolean showWaypoints)
    {
        this.showWaypoints = showWaypoints;
    }

    /** Whether waypoint labels are shown. */
    public boolean showWaypointLabels()
    {
        return showWaypointLabels;
    }

    /** Sets whether waypoint labels are shown. */
    public void setShowWaypointLabels(boolean showWaypointLabels)
    {
        this.showWaypointLabels = showWaypointLabels;
    }

    /** Whether other players are shown. */
    public boolean showPlayers()
    {
        return showPlayers;
    }

    /** Sets whether other players are shown. */
    public void setShowPlayers(boolean showPlayers)
    {
        this.showPlayers = showPlayers;
    }

    /** Whether non-player entities are shown. */
    public boolean showEntities()
    {
        return showEntities;
    }

    /** Sets whether non-player entities are shown. */
    public void setShowEntities(boolean showEntities)
    {
        this.showEntities = showEntities;
    }

    /** The background panel opacity, from 0 to 1. */
    public double backgroundOpacity()
    {
        return backgroundOpacity;
    }

    /** Sets the background panel opacity, clamped to 0..1. */
    public void setBackgroundOpacity(double backgroundOpacity)
    {
        this.backgroundOpacity = clamp(backgroundOpacity, 0.0, 1.0);
    }

    /** The number of chunks scanned per client tick. */
    @Override
    public int scanChunksPerTick()
    {
        return scanChunksPerTick;
    }

    /** Sets the surface scan budget per tick, clamped to 1..16. */
    public void setScanChunksPerTick(int scanChunksPerTick)
    {
        this.scanChunksPerTick = clamp(scanChunksPerTick, 1, 16);
    }

    /** The number of ticks between save passes. */
    @Override
    public int saveIntervalTicks()
    {
        return saveIntervalTicks;
    }

    /** Sets the save interval, clamped to 20..6000 ticks. */
    public void setSaveIntervalTicks(int saveIntervalTicks)
    {
        this.saveIntervalTicks = clamp(saveIntervalTicks, 20, 6000);
    }

    /** The default ARGB color for new waypoints. */
    public int waypointColor()
    {
        return waypointColor;
    }

    /** Sets the default ARGB color for new waypoints. */
    public void setWaypointColor(int waypointColor)
    {
        this.waypointColor = waypointColor;
    }

    @Override
    public int regionCacheSize()
    {
        return regionCacheSize;
    }

    /** Sets the surface region cache size, clamped to 16..512. */
    public void setRegionCacheSize(int regionCacheSize)
    {
        this.regionCacheSize = clamp(regionCacheSize, 16, 512);
    }

    @Override
    public int caveCacheSize()
    {
        return caveCacheSize;
    }

    /** Sets the cave slice cache size, clamped to 8..256. */
    public void setCaveCacheSize(int caveCacheSize)
    {
        this.caveCacheSize = clamp(caveCacheSize, 8, 256);
    }

    @Override
    public boolean caveEnabled()
    {
        return caveEnabled;
    }

    /** Sets whether the cave layer is available. */
    public void setCaveEnabled(boolean caveEnabled)
    {
        this.caveEnabled = caveEnabled;
    }

    @Override
    public boolean caveAuto()
    {
        return caveAuto;
    }

    /** Sets whether the cave Y is chosen automatically. */
    public void setCaveAuto(boolean caveAuto)
    {
        this.caveAuto = caveAuto;
    }

    @Override
    public int caveY()
    {
        return caveY;
    }

    /** Sets the manual cave Y, clamped to -256..320. */
    public void setCaveY(int caveY)
    {
        this.caveY = clamp(caveY, -256, 320);
    }

    @Override
    public boolean caveSurfaceOverlay()
    {
        return caveSurfaceOverlay;
    }

    /** Sets whether the surface map is blended over cave slices. */
    public void setCaveSurfaceOverlay(boolean caveSurfaceOverlay)
    {
        this.caveSurfaceOverlay = caveSurfaceOverlay;
    }

    @Override
    public double caveSurfaceOpacity()
    {
        return caveSurfaceOpacity;
    }

    /** Sets the surface overlay opacity, clamped to 0..1. */
    public void setCaveSurfaceOpacity(double caveSurfaceOpacity)
    {
        this.caveSurfaceOpacity = clamp(caveSurfaceOpacity, 0.0, 1.0);
    }

    @Override
    public int caveScanChunksPerTick()
    {
        return caveScanChunksPerTick;
    }

    /** Sets the number of cave slices scanned per client tick, clamped to 1..16. */
    public void setCaveScanChunksPerTick(int caveScanChunksPerTick)
    {
        this.caveScanChunksPerTick = clamp(caveScanChunksPerTick, 1, 16);
    }

    /** The cave slice transparency, from 0 to 1. */
    public double caveTransparency()
    {
        return caveTransparency;
    }

    /** Sets the cave slice transparency, clamped to 0..1. */
    public void setCaveTransparency(double caveTransparency)
    {
        this.caveTransparency = clamp(caveTransparency, 0.0, 1.0);
    }

    /** The HUD mask shape. */
    public Shape hudShape()
    {
        return hudShape;
    }

    /** Sets the HUD mask shape. */
    public void setHudShape(Shape hudShape)
    {
        this.hudShape = Objects.requireNonNull(hudShape, "hudShape");
    }

    /** The HUD rotation mode. */
    public Rotation rotationMode()
    {
        return rotationMode;
    }

    /** Sets the HUD rotation mode. */
    public void setRotationMode(Rotation rotationMode)
    {
        this.rotationMode = Objects.requireNonNull(rotationMode, "rotationMode");
    }

    /** Whether the biome readout is shown. */
    public boolean showBiome()
    {
        return showBiome;
    }

    /** Sets whether the biome readout is shown. */
    public void setShowBiome(boolean showBiome)
    {
        this.showBiome = showBiome;
    }

    /** Whether the dimension readout is shown. */
    public boolean showDimension()
    {
        return showDimension;
    }

    /** Sets whether the dimension readout is shown. */
    public void setShowDimension(boolean showDimension)
    {
        this.showDimension = showDimension;
    }

    /** Whether the zoom readout is shown. */
    public boolean showZoom()
    {
        return showZoom;
    }

    /** Sets whether the zoom readout is shown. */
    public void setShowZoom(boolean showZoom)
    {
        this.showZoom = showZoom;
    }

    /** Whether the in-game time readout is shown. */
    public boolean showTime()
    {
        return showTime;
    }

    /** Sets whether the in-game time readout is shown. */
    public void setShowTime(boolean showTime)
    {
        this.showTime = showTime;
    }

    /** Whether off-screen waypoint arrows are shown. */
    public boolean showOffscreenArrows()
    {
        return showOffscreenArrows;
    }

    /** Sets whether off-screen waypoint arrows are shown. */
    public void setShowOffscreenArrows(boolean showOffscreenArrows)
    {
        this.showOffscreenArrows = showOffscreenArrows;
    }

    /** Whether the chunk grid is shown. */
    public boolean showChunkGrid()
    {
        return showChunkGrid;
    }

    /** Sets whether the chunk grid is shown. */
    public void setShowChunkGrid(boolean showChunkGrid)
    {
        this.showChunkGrid = showChunkGrid;
    }

    /** Whether slime chunks are highlighted. */
    public boolean showSlimeChunks()
    {
        return showSlimeChunks;
    }

    /** Sets whether slime chunks are highlighted. */
    public void setShowSlimeChunks(boolean showSlimeChunks)
    {
        this.showSlimeChunks = showSlimeChunks;
    }

    /** Whether the world border is shown. */
    public boolean showWorldBorder()
    {
        return showWorldBorder;
    }

    /** Sets whether the world border is shown. */
    public void setShowWorldBorder(boolean showWorldBorder)
    {
        this.showWorldBorder = showWorldBorder;
    }

    /** Whether the spawn point is shown. */
    public boolean showSpawn()
    {
        return showSpawn;
    }

    /** Sets whether the spawn point is shown. */
    public void setShowSpawn(boolean showSpawn)
    {
        this.showSpawn = showSpawn;
    }

    /** Whether the HUD hides while a chat screen is open. */
    public boolean hideInChat()
    {
        return hideInChat;
    }

    /** Sets whether the HUD hides while a chat screen is open. */
    public void setHideInChat(boolean hideInChat)
    {
        this.hideInChat = hideInChat;
    }

    /** Whether the HUD hides while the debug screen is open. */
    public boolean hideInDebug()
    {
        return hideInDebug;
    }

    /** Sets whether the HUD hides while the debug screen is open. */
    public void setHideInDebug(boolean hideInDebug)
    {
        this.hideInDebug = hideInDebug;
    }

    /** Whether the HUD fades instead of disappearing in chat. */
    public boolean hudFadeWhenChat()
    {
        return hudFadeWhenChat;
    }

    /** Sets whether the HUD fades instead of disappearing in chat. */
    public void setHudFadeWhenChat(boolean hudFadeWhenChat)
    {
        this.hudFadeWhenChat = hudFadeWhenChat;
    }

    /** The HUD border opacity, from 0 to 1. */
    public double hudBorderOpacity()
    {
        return hudBorderOpacity;
    }

    /** Sets the HUD border opacity, clamped to 0..1. */
    public void setHudBorderOpacity(double hudBorderOpacity)
    {
        this.hudBorderOpacity = clamp(hudBorderOpacity, 0.0, 1.0);
    }

    /** Whether the HUD draws a soft shadow. */
    public boolean hudShadow()
    {
        return hudShadow;
    }

    /** Sets whether the HUD draws a soft shadow. */
    public void setHudShadow(boolean hudShadow)
    {
        this.hudShadow = hudShadow;
    }

    /** The HUD scale multiplier, from 0.75 to 1.5. */
    public double hudScale()
    {
        return hudScale;
    }

    /** Sets the HUD scale multiplier, clamped to 0.75..1.5. */
    public void setHudScale(double hudScale)
    {
        this.hudScale = clamp(hudScale, MIN_HUD_SCALE, MAX_HUD_SCALE);
    }

    /** The fullscreen map backdrop opacity, from 0 to 1. */
    public double mapBackgroundOpacity()
    {
        return mapBackgroundOpacity;
    }

    /** Sets the fullscreen map backdrop opacity, clamped to 0..1. */
    public void setMapBackgroundOpacity(double mapBackgroundOpacity)
    {
        this.mapBackgroundOpacity = clamp(mapBackgroundOpacity, 0.0, 1.0);
    }

    /** Whether fullscreen map textures use smooth filtering. */
    public boolean mapSmoothing()
    {
        return mapSmoothing;
    }

    /** Sets whether fullscreen map textures use smooth filtering. */
    public void setMapSmoothing(boolean mapSmoothing)
    {
        this.mapSmoothing = mapSmoothing;
    }

    /** Whether the fullscreen map grid is shown. */
    public boolean mapShowGrid()
    {
        return mapShowGrid;
    }

    /** Sets whether the fullscreen map grid is shown. */
    public void setMapShowGrid(boolean mapShowGrid)
    {
        this.mapShowGrid = mapShowGrid;
    }

    /** Whether players are shown on the fullscreen map. */
    public boolean showMapPlayers()
    {
        return showMapPlayers;
    }

    /** Sets whether players are shown on the fullscreen map. */
    public void setShowMapPlayers(boolean showMapPlayers)
    {
        this.showMapPlayers = showMapPlayers;
    }

    /** Whether entities are shown on the fullscreen map. */
    public boolean showMapEntities()
    {
        return showMapEntities;
    }

    /** Sets whether entities are shown on the fullscreen map. */
    public void setShowMapEntities(boolean showMapEntities)
    {
        this.showMapEntities = showMapEntities;
    }

    /** Whether waypoints are shown on the fullscreen map. */
    public boolean showMapWaypoints()
    {
        return showMapWaypoints;
    }

    /** Sets whether waypoints are shown on the fullscreen map. */
    public void setShowMapWaypoints(boolean showMapWaypoints)
    {
        this.showMapWaypoints = showMapWaypoints;
    }

    /** Whether death markers are shown on the fullscreen map. */
    public boolean showMapDeaths()
    {
        return showMapDeaths;
    }

    /** Sets whether death markers are shown on the fullscreen map. */
    public void setShowMapDeaths(boolean showMapDeaths)
    {
        this.showMapDeaths = showMapDeaths;
    }

    /** Whether the biome layer is shown on the fullscreen map. */
    public boolean showMapBiomeLayer()
    {
        return showMapBiomeLayer;
    }

    /** Sets whether the biome layer is shown on the fullscreen map. */
    public void setShowMapBiomeLayer(boolean showMapBiomeLayer)
    {
        this.showMapBiomeLayer = showMapBiomeLayer;
    }

    /** Whether the height layer is shown on the fullscreen map. */
    public boolean showMapHeightLayer()
    {
        return showMapHeightLayer;
    }

    /** Sets whether the height layer is shown on the fullscreen map. */
    public void setShowMapHeightLayer(boolean showMapHeightLayer)
    {
        this.showMapHeightLayer = showMapHeightLayer;
    }

    /** Whether the cave layer is available on the fullscreen map. */
    public boolean showMapCaveLayer()
    {
        return showMapCaveLayer;
    }

    /** Sets whether the cave layer is available on the fullscreen map. */
    public void setShowMapCaveLayer(boolean showMapCaveLayer)
    {
        this.showMapCaveLayer = showMapCaveLayer;
    }

    /** The fullscreen map pan speed multiplier, from 0.25 to 4. */
    public double mapPanSpeed()
    {
        return mapPanSpeed;
    }

    /** Sets the fullscreen map pan speed, clamped to 0.25..4. */
    public void setMapPanSpeed(double mapPanSpeed)
    {
        this.mapPanSpeed = clamp(mapPanSpeed, 0.25, 4.0);
    }

    /** The fullscreen map zoom sensitivity, from 0.25 to 4. */
    public double mapZoomSensitivity()
    {
        return mapZoomSensitivity;
    }

    /** Sets the fullscreen map zoom sensitivity, clamped to 0.25..4. */
    public void setMapZoomSensitivity(double mapZoomSensitivity)
    {
        this.mapZoomSensitivity = clamp(mapZoomSensitivity, 0.25, 4.0);
    }

    /** Whether hostile entities are shown. */
    public boolean showHostile()
    {
        return showHostile;
    }

    /** Sets whether hostile entities are shown. */
    public void setShowHostile(boolean showHostile)
    {
        this.showHostile = showHostile;
    }

    /** Whether passive entities are shown. */
    public boolean showPassive()
    {
        return showPassive;
    }

    /** Sets whether passive entities are shown. */
    public void setShowPassive(boolean showPassive)
    {
        this.showPassive = showPassive;
    }

    /** Whether item entities are shown. */
    public boolean showItems()
    {
        return showItems;
    }

    /** Sets whether item entities are shown. */
    public void setShowItems(boolean showItems)
    {
        this.showItems = showItems;
    }

    /** Whether other entities are shown. */
    public boolean showOtherEntities()
    {
        return showOtherEntities;
    }

    /** Sets whether other entities are shown. */
    public void setShowOtherEntities(boolean showOtherEntities)
    {
        this.showOtherEntities = showOtherEntities;
    }

    /** The maximum entity markers drawn on the HUD. */
    public int entityLimitHud()
    {
        return entityLimitHud;
    }

    /** Sets the HUD entity limit, clamped to 0..1024. */
    public void setEntityLimitHud(int entityLimitHud)
    {
        this.entityLimitHud = clamp(entityLimitHud, 0, 1024);
    }

    /** The maximum entity markers drawn on the fullscreen map. */
    public int entityLimitFullscreen()
    {
        return entityLimitFullscreen;
    }

    /** Sets the fullscreen entity limit, clamped to 0..4096. */
    public void setEntityLimitFullscreen(int entityLimitFullscreen)
    {
        this.entityLimitFullscreen = clamp(entityLimitFullscreen, 0, 4096);
    }

    /** When entity labels are drawn. */
    public EntityLabels entityLabelMode()
    {
        return entityLabelMode;
    }

    /** Sets when entity labels are drawn. */
    public void setEntityLabelMode(EntityLabels entityLabelMode)
    {
        this.entityLabelMode = Objects.requireNonNull(entityLabelMode, "entityLabelMode");
    }

    /** Whether a death waypoint is created on death. */
    public boolean deathWaypointEnabled()
    {
        return deathWaypointEnabled;
    }

    /** Sets whether a death waypoint is created on death. */
    public void setDeathWaypointEnabled(boolean deathWaypointEnabled)
    {
        this.deathWaypointEnabled = deathWaypointEnabled;
    }

    /** The temporary waypoint lifetime in minutes, from 1 to 43200. */
    public int temporaryWaypointMinutes()
    {
        return temporaryWaypointMinutes;
    }

    /** Sets the temporary waypoint lifetime, clamped to 1..43200 minutes. */
    public void setTemporaryWaypointMinutes(int temporaryWaypointMinutes)
    {
        this.temporaryWaypointMinutes = clamp(temporaryWaypointMinutes, 1, 43200);
    }

    /** The maximum distance for waypoint labels, in blocks. */
    public double waypointLabelDistance()
    {
        return waypointLabelDistance;
    }

    /** Sets the waypoint label distance, clamped to 8..512 blocks. */
    public void setWaypointLabelDistance(double waypointLabelDistance)
    {
        this.waypointLabelDistance = clamp(waypointLabelDistance, 8.0, 512.0);
    }

    /** Whether world-space waypoint beams are drawn. */
    public boolean waypointBeamEnabled()
    {
        return waypointBeamEnabled;
    }

    /** Sets whether world-space waypoint beams are drawn. */
    public void setWaypointBeamEnabled(boolean waypointBeamEnabled)
    {
        this.waypointBeamEnabled = waypointBeamEnabled;
    }

    /** The maximum waypoint beam distance, in blocks. */
    public double waypointBeamDistance()
    {
        return waypointBeamDistance;
    }

    /** Sets the waypoint beam distance, clamped to 8..512 blocks. */
    public void setWaypointBeamDistance(double waypointBeamDistance)
    {
        this.waypointBeamDistance = clamp(waypointBeamDistance, 8.0, 512.0);
    }

    /** The theme id. */
    public String themeId()
    {
        return themeId;
    }

    /** Sets the theme id, normalized to lower case. */
    public void setThemeId(String themeId)
    {
        this.themeId = normalizeThemeId(themeId, DEFAULT_THEME_ID);
    }

    /** The accent color. */
    public int accentColor()
    {
        return accentColor;
    }

    /** Sets the accent color. */
    public void setAccentColor(int accentColor)
    {
        this.accentColor = accentColor;
    }

    /** Whether the custom accent overrides the theme accent. */
    public boolean customAccent()
    {
        return customAccent;
    }

    /** Sets whether the custom accent overrides the theme accent. */
    public void setCustomAccent(boolean customAccent)
    {
        this.customAccent = customAccent;
    }

    /** The player marker color. */
    public int playerColor()
    {
        return playerColor;
    }

    /** Sets the player marker color. */
    public void setPlayerColor(int playerColor)
    {
        this.playerColor = playerColor;
    }

    /** The grid line color. */
    public int gridColor()
    {
        return gridColor;
    }

    /** Sets the grid line color. */
    public void setGridColor(int gridColor)
    {
        this.gridColor = gridColor;
    }

    /** The death marker color. */
    public int deathColor()
    {
        return deathColor;
    }

    /** Sets the death marker color. */
    public void setDeathColor(int deathColor)
    {
        this.deathColor = deathColor;
    }

    /** The selected performance preset. */
    public PerformancePreset performancePreset()
    {
        return performancePreset;
    }

    /**
     * Selects a performance preset and applies its coherent defaults.
     *
     * <p>Selecting a preset immediately updates the texture upload budget,
     * region draw budget, scan budgets, cache sizes, and save interval. Any of
     * those values may be edited individually afterwards.</p>
     *
     * @param performancePreset the preset, never {@code null}
     */
    public void setPerformancePreset(PerformancePreset performancePreset)
    {
        this.performancePreset = Objects.requireNonNull(performancePreset,
                "performancePreset");
        applyPreset(this.performancePreset);
    }

    /** The maximum full texture uploads per frame, from 1 to 16. */
    public int textureUploadsPerFrame()
    {
        return textureUploadsPerFrame;
    }

    /** Sets the texture upload budget, clamped to 1..16. */
    public void setTextureUploadsPerFrame(int textureUploadsPerFrame)
    {
        this.textureUploadsPerFrame = clamp(textureUploadsPerFrame, 1, 16);
    }

    /** The maximum map regions drawn per frame, from 1 to 512. */
    public int maxRegionsPerFrame()
    {
        return maxRegionsPerFrame;
    }

    /** Sets the region draw budget, clamped to 1..512. */
    public void setMaxRegionsPerFrame(int maxRegionsPerFrame)
    {
        this.maxRegionsPerFrame = clamp(maxRegionsPerFrame, 1, 512);
    }

    /** Whether the HUD hides while the debug screen is open. */
    public boolean hideWithDebugScreen()
    {
        return hideWithDebugScreen;
    }

    /** Sets whether the HUD hides while the debug screen is open. */
    public void setHideWithDebugScreen(boolean hideWithDebugScreen)
    {
        this.hideWithDebugScreen = hideWithDebugScreen;
    }

    /** Whether other HUD mods are allowed to reserve space. */
    public boolean respectOtherHud()
    {
        return respectOtherHud;
    }

    /** Sets whether other HUD mods are allowed to reserve space. */
    public void setRespectOtherHud(boolean respectOtherHud)
    {
        this.respectOtherHud = respectOtherHud;
    }

    /** Whether the minimap disables itself on low memory. */
    public boolean disableOnLowMemory()
    {
        return disableOnLowMemory;
    }

    /** Sets whether the minimap disables itself on low memory. */
    public void setDisableOnLowMemory(boolean disableOnLowMemory)
    {
        this.disableOnLowMemory = disableOnLowMemory;
    }

    /** Whether safe mode is enabled. */
    public boolean safeMode()
    {
        return safeMode;
    }

    /** Sets whether safe mode is enabled. */
    public void setSafeMode(boolean safeMode)
    {
        this.safeMode = safeMode;
    }

    /**
     * Returns the section names accepted by {@link #resetSection(String)}.
     *
     * @return an unmodifiable list of canonical section names
     */
    public static List<String> sections()
    {
        return List.of("general", "hud", "fullscreen", "entities", "waypoints",
                "cave", "theme", "performance", "compatibility", "advanced");
    }

    /**
     * Resets one settings section to defaults.
     *
     * <p>Recognized sections are {@code general}, {@code hud} (alias
     * {@code minimap}), {@code fullscreen} (alias {@code map}),
     * {@code entities}, {@code waypoints}, {@code cave}, {@code theme} (alias
     * {@code appearance}), {@code performance}, {@code compatibility}, and
     * {@code advanced} (performance plus compatibility). Section names are
     * case-insensitive. The reset is not written to disk until
     * {@link #save()} is called.</p>
     *
     * @param section the section name, never {@code null}
     * @throws NullPointerException     when {@code section} is {@code null}
     * @throws IllegalArgumentException when the section is unknown
     */
    public void resetSection(String section)
    {
        Objects.requireNonNull(section, "section");
        MinimapConfig defaults = new MinimapConfig(file);
        switch (normalizeSection(section))
        {
            case "general" -> copyGeneral(defaults);
            case "hud" -> copyHud(defaults);
            case "minimap" ->
            {
                copyGeneral(defaults);
                copyHud(defaults);
            }
            case "fullscreen", "fullscreen_map", "map" -> copyFullscreen(defaults);
            case "entities" -> copyEntities(defaults);
            case "waypoints" -> copyWaypoints(defaults);
            case "cave" -> copyCave(defaults);
            case "theme", "appearance" -> copyTheme(defaults);
            case "performance" -> copyPerformance(defaults);
            case "compatibility" -> copyCompatibility(defaults);
            case "advanced" ->
            {
                copyPerformance(defaults);
                copyCompatibility(defaults);
            }
            default -> throw new IllegalArgumentException(
                    "unknown config section: " + section);
        }
    }

    /**
     * Resets every setting to its default value. The reset is not written to
     * disk until {@link #save()} is called.
     */
    public void resetAll()
    {
        copyAll(new MinimapConfig(file));
    }

    /**
     * Serializes the current settings to a version 2 JSON string.
     *
     * @return the JSON text, never {@code null}
     */
    public String exportJson()
    {
        return GSON.toJson(toJsonObject());
    }

    /**
     * Applies settings from a JSON string without touching the file.
     *
     * <p>Recognized keys are applied and clamped; missing keys retain their
     * current values. The import is not written to disk until {@link #save()}
     * is called.</p>
     *
     * @param json the JSON text, never {@code null}
     * @throws NullPointerException     when {@code json} is {@code null}
     * @throws IllegalArgumentException when the text is not a JSON object
     */
    public void importJson(String json)
    {
        Objects.requireNonNull(json, "json");
        JsonElement parsed;
        try
        {
            parsed = JsonParser.parseString(json);
        }
        catch (RuntimeException failure)
        {
            throw new IllegalArgumentException("invalid config JSON", failure);
        }
        if (!parsed.isJsonObject())
        {
            throw new IllegalArgumentException("config root must be a JSON object");
        }
        read(parsed.getAsJsonObject(), message ->
        {
        });
    }

    /**
     * Returns an immutable snapshot of every value for live preview.
     *
     * @return the snapshot, never {@code null}
     */
    public Snapshot snapshot()
    {
        return new Snapshot(enabled, corner, offsetX, offsetY, size, zoom,
                showCoordinates, showCompass, showWaypoints, showWaypointLabels,
                showPlayers, showEntities, backgroundOpacity, scanChunksPerTick,
                saveIntervalTicks, waypointColor, regionCacheSize, caveCacheSize,
                caveEnabled, caveAuto, caveY, caveSurfaceOverlay, caveSurfaceOpacity,
                caveScanChunksPerTick, caveTransparency, hudShape, rotationMode,
                showBiome, showDimension, showZoom, showTime, showOffscreenArrows,
                showChunkGrid, showSlimeChunks, showWorldBorder, showSpawn, hideInChat,
                hideInDebug, hudFadeWhenChat, hudBorderOpacity, hudShadow, hudScale,
                mapBackgroundOpacity, mapSmoothing, mapShowGrid, showMapPlayers,
                showMapEntities, showMapWaypoints, showMapDeaths, showMapBiomeLayer,
                showMapHeightLayer, showMapCaveLayer, mapPanSpeed, mapZoomSensitivity,
                showHostile, showPassive, showItems, showOtherEntities, entityLimitHud,
                entityLimitFullscreen, entityLabelMode, deathWaypointEnabled,
                temporaryWaypointMinutes, waypointLabelDistance, waypointBeamEnabled,
                waypointBeamDistance, themeId, accentColor, customAccent, playerColor,
                gridColor, deathColor, performancePreset, textureUploadsPerFrame,
                maxRegionsPerFrame, hideWithDebugScreen, respectOtherHud,
                disableOnLowMemory, safeMode);
    }

    /**
     * An immutable snapshot of every configuration value.
     *
     * <p>Field names mirror the accessor names on {@link MinimapConfig}.</p>
     */
    public record Snapshot(boolean enabled, Corner corner, int offsetX, int offsetY,
                           int size, double zoom, boolean showCoordinates,
                           boolean showCompass, boolean showWaypoints,
                           boolean showWaypointLabels, boolean showPlayers,
                           boolean showEntities, double backgroundOpacity,
                           int scanChunksPerTick, int saveIntervalTicks, int waypointColor,
                           int regionCacheSize, int caveCacheSize, boolean caveEnabled,
                           boolean caveAuto, int caveY, boolean caveSurfaceOverlay,
                           double caveSurfaceOpacity, int caveScanChunksPerTick,
                           double caveTransparency, Shape hudShape, Rotation rotationMode,
                           boolean showBiome, boolean showDimension, boolean showZoom,
                           boolean showTime, boolean showOffscreenArrows,
                           boolean showChunkGrid, boolean showSlimeChunks,
                           boolean showWorldBorder, boolean showSpawn, boolean hideInChat,
                           boolean hideInDebug, boolean hudFadeWhenChat,
                           double hudBorderOpacity, boolean hudShadow, double hudScale,
                           double mapBackgroundOpacity, boolean mapSmoothing,
                           boolean mapShowGrid, boolean showMapPlayers,
                           boolean showMapEntities, boolean showMapWaypoints,
                           boolean showMapDeaths, boolean showMapBiomeLayer,
                           boolean showMapHeightLayer, boolean showMapCaveLayer,
                           double mapPanSpeed, double mapZoomSensitivity,
                           boolean showHostile, boolean showPassive, boolean showItems,
                           boolean showOtherEntities, int entityLimitHud,
                           int entityLimitFullscreen, EntityLabels entityLabelMode,
                           boolean deathWaypointEnabled, int temporaryWaypointMinutes,
                           double waypointLabelDistance, boolean waypointBeamEnabled,
                           double waypointBeamDistance, String themeId, int accentColor,
                           boolean customAccent, int playerColor, int gridColor,
                           int deathColor, PerformancePreset performancePreset,
                           int textureUploadsPerFrame, int maxRegionsPerFrame,
                           boolean hideWithDebugScreen, boolean respectOtherHud,
                           boolean disableOnLowMemory, boolean safeMode)
    {
    }

    /**
     * Saves the current values atomically.
     *
     * @throws IOException when the file cannot be written
     */
    public void save() throws IOException
    {
        writeAtomically(exportJson() + "\n");
    }

    /**
     * Saves without propagating a failure.
     *
     * @param log the log sink
     */
    public void saveQuietly(Consumer<String> log)
    {
        try
        {
            save();
        }
        catch (IOException failure)
        {
            log.accept("[weaver-minimap] config save failed: " + failure);
        }
    }

    private void read(JsonObject root, Consumer<String> log)
    {
        JsonElement presetElement = root.get("performancePreset");
        if (presetElement != null && presetElement.isJsonPrimitive()
                && presetElement.getAsJsonPrimitive().isString())
        {
            performancePreset = enumValue(root, "performancePreset",
                    PerformancePreset.class, performancePreset, log);
            applyPreset(performancePreset);
        }
        enabled = bool(root, "enabled", enabled);
        corner = enumValue(root, "corner", Corner.class, corner, log);
        offsetX = clamp(integer(root, "offsetX", offsetX, log), 0, 4096);
        offsetY = clamp(integer(root, "offsetY", offsetY, log), 0, 4096);
        size = clamp(integer(root, "size", size, log), MIN_SIZE, MAX_SIZE);
        zoom = clamp(number(root, "zoom", zoom, log), MIN_ZOOM, MAX_ZOOM);
        showCoordinates = bool(root, "showCoordinates", showCoordinates);
        showCompass = bool(root, "showCompass", showCompass);
        showWaypoints = bool(root, "showWaypoints", showWaypoints);
        showWaypointLabels = bool(root, "showWaypointLabels", showWaypointLabels);
        showPlayers = bool(root, "showPlayers", showPlayers);
        showEntities = bool(root, "showEntities", showEntities);
        backgroundOpacity = clamp(number(root, "backgroundOpacity", backgroundOpacity, log),
                0.0, 1.0);
        scanChunksPerTick = clamp(integer(root, "scanChunksPerTick", scanChunksPerTick, log),
                1, 16);
        saveIntervalTicks = clamp(integer(root, "saveIntervalTicks", saveIntervalTicks, log),
                20, 6000);
        waypointColor = integer(root, "waypointColor", waypointColor, log);
        regionCacheSize = clamp(integer(root, "regionCacheSize", regionCacheSize, log),
                16, 512);
        caveCacheSize = clamp(integer(root, "caveCacheSize", caveCacheSize, log),
                8, 256);
        caveEnabled = bool(root, "caveEnabled", caveEnabled);
        caveAuto = bool(root, "caveAuto", caveAuto);
        caveY = clamp(integer(root, "caveY", caveY, log), -256, 320);
        caveSurfaceOverlay = bool(root, "caveSurfaceOverlay", caveSurfaceOverlay);
        caveSurfaceOpacity = clamp(number(root, "caveSurfaceOpacity", caveSurfaceOpacity, log),
                0.0, 1.0);
        caveScanChunksPerTick = clamp(integer(root, "caveScanChunksPerTick",
                caveScanChunksPerTick, log), 1, 16);
        caveTransparency = clamp(number(root, "caveTransparency", caveTransparency, log),
                0.0, 1.0);

        hudShape = enumValue(root, "hudShape", Shape.class, hudShape, log);
        rotationMode = enumValue(root, "rotationMode", Rotation.class, rotationMode, log);
        showBiome = bool(root, "showBiome", showBiome);
        showDimension = bool(root, "showDimension", showDimension);
        showZoom = bool(root, "showZoom", showZoom);
        showTime = bool(root, "showTime", showTime);
        showOffscreenArrows = bool(root, "showOffscreenArrows", showOffscreenArrows);
        showChunkGrid = bool(root, "showChunkGrid", showChunkGrid);
        showSlimeChunks = bool(root, "showSlimeChunks", showSlimeChunks);
        showWorldBorder = bool(root, "showWorldBorder", showWorldBorder);
        showSpawn = bool(root, "showSpawn", showSpawn);
        hideInChat = bool(root, "hideInChat", hideInChat);
        hideInDebug = bool(root, "hideInDebug", hideInDebug);
        hudFadeWhenChat = bool(root, "hudFadeWhenChat", hudFadeWhenChat);
        hudBorderOpacity = clamp(number(root, "hudBorderOpacity", hudBorderOpacity, log),
                0.0, 1.0);
        hudShadow = bool(root, "hudShadow", hudShadow);
        hudScale = clamp(number(root, "hudScale", hudScale, log),
                MIN_HUD_SCALE, MAX_HUD_SCALE);

        mapBackgroundOpacity = clamp(number(root, "mapBackgroundOpacity",
                mapBackgroundOpacity, log), 0.0, 1.0);
        mapSmoothing = bool(root, "mapSmoothing", mapSmoothing);
        mapShowGrid = bool(root, "mapShowGrid", mapShowGrid);
        showMapPlayers = bool(root, "showMapPlayers", showMapPlayers);
        showMapEntities = bool(root, "showMapEntities", showMapEntities);
        showMapWaypoints = bool(root, "showMapWaypoints", showMapWaypoints);
        showMapDeaths = bool(root, "showMapDeaths", showMapDeaths);
        showMapBiomeLayer = bool(root, "showMapBiomeLayer", showMapBiomeLayer);
        showMapHeightLayer = bool(root, "showMapHeightLayer", showMapHeightLayer);
        showMapCaveLayer = bool(root, "showMapCaveLayer", showMapCaveLayer);
        mapPanSpeed = clamp(number(root, "mapPanSpeed", mapPanSpeed, log), 0.25, 4.0);
        mapZoomSensitivity = clamp(number(root, "mapZoomSensitivity",
                mapZoomSensitivity, log), 0.25, 4.0);

        showHostile = bool(root, "showHostile", showHostile);
        showPassive = bool(root, "showPassive", showPassive);
        showItems = bool(root, "showItems", showItems);
        showOtherEntities = bool(root, "showOtherEntities", showOtherEntities);
        entityLimitHud = clamp(integer(root, "entityLimitHud", entityLimitHud, log),
                0, 1024);
        entityLimitFullscreen = clamp(integer(root, "entityLimitFullscreen",
                entityLimitFullscreen, log), 0, 4096);
        entityLabelMode = enumValue(root, "entityLabelMode", EntityLabels.class,
                entityLabelMode, log);

        deathWaypointEnabled = bool(root, "deathWaypointEnabled", deathWaypointEnabled);
        temporaryWaypointMinutes = clamp(integer(root, "temporaryWaypointMinutes",
                temporaryWaypointMinutes, log), 1, 43200);
        waypointLabelDistance = clamp(number(root, "waypointLabelDistance",
                waypointLabelDistance, log), 8.0, 512.0);
        waypointBeamEnabled = bool(root, "waypointBeamEnabled", waypointBeamEnabled);
        waypointBeamDistance = clamp(number(root, "waypointBeamDistance",
                waypointBeamDistance, log), 8.0, 512.0);

        String configuredTheme = string(root, "themeId");
        if (configuredTheme != null)
        {
            String normalized = normalizeThemeId(configuredTheme, themeId);
            if (!normalized.equals(configuredTheme))
            {
                log.accept("[weaver-minimap] config themeId normalized to " + normalized);
            }
            themeId = normalized;
        }
        accentColor = integer(root, "accentColor", accentColor, log);
        customAccent = bool(root, "customAccent", customAccent);
        playerColor = integer(root, "playerColor", playerColor, log);
        gridColor = integer(root, "gridColor", gridColor, log);
        deathColor = integer(root, "deathColor", deathColor, log);

        textureUploadsPerFrame = clamp(integer(root, "textureUploadsPerFrame",
                textureUploadsPerFrame, log), 1, 16);
        maxRegionsPerFrame = clamp(integer(root, "maxRegionsPerFrame",
                maxRegionsPerFrame, log), 1, 512);

        hideWithDebugScreen = bool(root, "hideWithDebugScreen", hideWithDebugScreen);
        respectOtherHud = bool(root, "respectOtherHud", respectOtherHud);
        disableOnLowMemory = bool(root, "disableOnLowMemory", disableOnLowMemory);
        safeMode = bool(root, "safeMode", safeMode);
    }

    private JsonObject toJsonObject()
    {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("enabled", enabled);
        root.addProperty("corner", corner.name());
        root.addProperty("offsetX", offsetX);
        root.addProperty("offsetY", offsetY);
        root.addProperty("size", size);
        root.addProperty("zoom", zoom);
        root.addProperty("showCoordinates", showCoordinates);
        root.addProperty("showCompass", showCompass);
        root.addProperty("showWaypoints", showWaypoints);
        root.addProperty("showWaypointLabels", showWaypointLabels);
        root.addProperty("showPlayers", showPlayers);
        root.addProperty("showEntities", showEntities);
        root.addProperty("backgroundOpacity", backgroundOpacity);
        root.addProperty("scanChunksPerTick", scanChunksPerTick);
        root.addProperty("saveIntervalTicks", saveIntervalTicks);
        root.addProperty("waypointColor", waypointColor);
        root.addProperty("regionCacheSize", regionCacheSize);
        root.addProperty("caveCacheSize", caveCacheSize);
        root.addProperty("caveEnabled", caveEnabled);
        root.addProperty("caveAuto", caveAuto);
        root.addProperty("caveY", caveY);
        root.addProperty("caveSurfaceOverlay", caveSurfaceOverlay);
        root.addProperty("caveSurfaceOpacity", caveSurfaceOpacity);
        root.addProperty("caveScanChunksPerTick", caveScanChunksPerTick);
        root.addProperty("caveTransparency", caveTransparency);
        root.addProperty("hudShape", hudShape.name());
        root.addProperty("rotationMode", rotationMode.name());
        root.addProperty("showBiome", showBiome);
        root.addProperty("showDimension", showDimension);
        root.addProperty("showZoom", showZoom);
        root.addProperty("showTime", showTime);
        root.addProperty("showOffscreenArrows", showOffscreenArrows);
        root.addProperty("showChunkGrid", showChunkGrid);
        root.addProperty("showSlimeChunks", showSlimeChunks);
        root.addProperty("showWorldBorder", showWorldBorder);
        root.addProperty("showSpawn", showSpawn);
        root.addProperty("hideInChat", hideInChat);
        root.addProperty("hideInDebug", hideInDebug);
        root.addProperty("hudFadeWhenChat", hudFadeWhenChat);
        root.addProperty("hudBorderOpacity", hudBorderOpacity);
        root.addProperty("hudShadow", hudShadow);
        root.addProperty("hudScale", hudScale);
        root.addProperty("mapBackgroundOpacity", mapBackgroundOpacity);
        root.addProperty("mapSmoothing", mapSmoothing);
        root.addProperty("mapShowGrid", mapShowGrid);
        root.addProperty("showMapPlayers", showMapPlayers);
        root.addProperty("showMapEntities", showMapEntities);
        root.addProperty("showMapWaypoints", showMapWaypoints);
        root.addProperty("showMapDeaths", showMapDeaths);
        root.addProperty("showMapBiomeLayer", showMapBiomeLayer);
        root.addProperty("showMapHeightLayer", showMapHeightLayer);
        root.addProperty("showMapCaveLayer", showMapCaveLayer);
        root.addProperty("mapPanSpeed", mapPanSpeed);
        root.addProperty("mapZoomSensitivity", mapZoomSensitivity);
        root.addProperty("showHostile", showHostile);
        root.addProperty("showPassive", showPassive);
        root.addProperty("showItems", showItems);
        root.addProperty("showOtherEntities", showOtherEntities);
        root.addProperty("entityLimitHud", entityLimitHud);
        root.addProperty("entityLimitFullscreen", entityLimitFullscreen);
        root.addProperty("entityLabelMode", entityLabelMode.name());
        root.addProperty("deathWaypointEnabled", deathWaypointEnabled);
        root.addProperty("temporaryWaypointMinutes", temporaryWaypointMinutes);
        root.addProperty("waypointLabelDistance", waypointLabelDistance);
        root.addProperty("waypointBeamEnabled", waypointBeamEnabled);
        root.addProperty("waypointBeamDistance", waypointBeamDistance);
        root.addProperty("themeId", themeId);
        root.addProperty("accentColor", accentColor);
        root.addProperty("customAccent", customAccent);
        root.addProperty("playerColor", playerColor);
        root.addProperty("gridColor", gridColor);
        root.addProperty("deathColor", deathColor);
        root.addProperty("performancePreset", performancePreset.name());
        root.addProperty("textureUploadsPerFrame", textureUploadsPerFrame);
        root.addProperty("maxRegionsPerFrame", maxRegionsPerFrame);
        root.addProperty("hideWithDebugScreen", hideWithDebugScreen);
        root.addProperty("respectOtherHud", respectOtherHud);
        root.addProperty("disableOnLowMemory", disableOnLowMemory);
        root.addProperty("safeMode", safeMode);
        return root;
    }

    private void applyPreset(PerformancePreset preset)
    {
        switch (preset)
        {
            case LOW ->
            {
                textureUploadsPerFrame = 1;
                maxRegionsPerFrame = 32;
                scanChunksPerTick = 2;
                caveScanChunksPerTick = 1;
                regionCacheSize = 48;
                caveCacheSize = 32;
                saveIntervalTicks = 400;
            }
            case BALANCED ->
            {
                textureUploadsPerFrame = 2;
                maxRegionsPerFrame = 64;
                scanChunksPerTick = 4;
                caveScanChunksPerTick = 2;
                regionCacheSize = 96;
                caveCacheSize = 64;
                saveIntervalTicks = 200;
            }
            case HIGH ->
            {
                textureUploadsPerFrame = 4;
                maxRegionsPerFrame = 128;
                scanChunksPerTick = 8;
                caveScanChunksPerTick = 4;
                regionCacheSize = 192;
                caveCacheSize = 128;
                saveIntervalTicks = 100;
            }
            case ULTRA ->
            {
                textureUploadsPerFrame = 8;
                maxRegionsPerFrame = 256;
                scanChunksPerTick = 16;
                caveScanChunksPerTick = 8;
                regionCacheSize = 384;
                caveCacheSize = 224;
                saveIntervalTicks = 60;
            }
        }
    }

    private void copyAll(MinimapConfig defaults)
    {
        copyGeneral(defaults);
        copyHud(defaults);
        copyFullscreen(defaults);
        copyEntities(defaults);
        copyWaypoints(defaults);
        copyCave(defaults);
        copyTheme(defaults);
        copyPerformance(defaults);
        copyCompatibility(defaults);
    }

    private void copyGeneral(MinimapConfig defaults)
    {
        enabled = defaults.enabled;
        corner = defaults.corner;
        offsetX = defaults.offsetX;
        offsetY = defaults.offsetY;
        size = defaults.size;
        zoom = defaults.zoom;
        backgroundOpacity = defaults.backgroundOpacity;
    }

    private void copyHud(MinimapConfig defaults)
    {
        showCoordinates = defaults.showCoordinates;
        showCompass = defaults.showCompass;
        showWaypoints = defaults.showWaypoints;
        showWaypointLabels = defaults.showWaypointLabels;
        hudShape = defaults.hudShape;
        rotationMode = defaults.rotationMode;
        showBiome = defaults.showBiome;
        showDimension = defaults.showDimension;
        showZoom = defaults.showZoom;
        showTime = defaults.showTime;
        showOffscreenArrows = defaults.showOffscreenArrows;
        showChunkGrid = defaults.showChunkGrid;
        showSlimeChunks = defaults.showSlimeChunks;
        showWorldBorder = defaults.showWorldBorder;
        showSpawn = defaults.showSpawn;
        hideInChat = defaults.hideInChat;
        hideInDebug = defaults.hideInDebug;
        hudFadeWhenChat = defaults.hudFadeWhenChat;
        hudBorderOpacity = defaults.hudBorderOpacity;
        hudShadow = defaults.hudShadow;
        hudScale = defaults.hudScale;
    }

    private void copyFullscreen(MinimapConfig defaults)
    {
        mapBackgroundOpacity = defaults.mapBackgroundOpacity;
        mapSmoothing = defaults.mapSmoothing;
        mapShowGrid = defaults.mapShowGrid;
        showMapPlayers = defaults.showMapPlayers;
        showMapEntities = defaults.showMapEntities;
        showMapWaypoints = defaults.showMapWaypoints;
        showMapDeaths = defaults.showMapDeaths;
        showMapBiomeLayer = defaults.showMapBiomeLayer;
        showMapHeightLayer = defaults.showMapHeightLayer;
        showMapCaveLayer = defaults.showMapCaveLayer;
        mapPanSpeed = defaults.mapPanSpeed;
        mapZoomSensitivity = defaults.mapZoomSensitivity;
    }

    private void copyEntities(MinimapConfig defaults)
    {
        showPlayers = defaults.showPlayers;
        showEntities = defaults.showEntities;
        showHostile = defaults.showHostile;
        showPassive = defaults.showPassive;
        showItems = defaults.showItems;
        showOtherEntities = defaults.showOtherEntities;
        entityLimitHud = defaults.entityLimitHud;
        entityLimitFullscreen = defaults.entityLimitFullscreen;
        entityLabelMode = defaults.entityLabelMode;
    }

    private void copyWaypoints(MinimapConfig defaults)
    {
        waypointColor = defaults.waypointColor;
        deathWaypointEnabled = defaults.deathWaypointEnabled;
        temporaryWaypointMinutes = defaults.temporaryWaypointMinutes;
        waypointLabelDistance = defaults.waypointLabelDistance;
        waypointBeamEnabled = defaults.waypointBeamEnabled;
        waypointBeamDistance = defaults.waypointBeamDistance;
    }

    private void copyCave(MinimapConfig defaults)
    {
        caveEnabled = defaults.caveEnabled;
        caveAuto = defaults.caveAuto;
        caveY = defaults.caveY;
        caveSurfaceOverlay = defaults.caveSurfaceOverlay;
        caveSurfaceOpacity = defaults.caveSurfaceOpacity;
        caveScanChunksPerTick = defaults.caveScanChunksPerTick;
        caveTransparency = defaults.caveTransparency;
    }

    private void copyTheme(MinimapConfig defaults)
    {
        themeId = defaults.themeId;
        accentColor = defaults.accentColor;
        customAccent = defaults.customAccent;
        playerColor = defaults.playerColor;
        gridColor = defaults.gridColor;
        deathColor = defaults.deathColor;
    }

    private void copyPerformance(MinimapConfig defaults)
    {
        performancePreset = defaults.performancePreset;
        textureUploadsPerFrame = defaults.textureUploadsPerFrame;
        maxRegionsPerFrame = defaults.maxRegionsPerFrame;
        scanChunksPerTick = defaults.scanChunksPerTick;
        caveScanChunksPerTick = defaults.caveScanChunksPerTick;
        regionCacheSize = defaults.regionCacheSize;
        caveCacheSize = defaults.caveCacheSize;
        saveIntervalTicks = defaults.saveIntervalTicks;
    }

    private void copyCompatibility(MinimapConfig defaults)
    {
        hideWithDebugScreen = defaults.hideWithDebugScreen;
        respectOtherHud = defaults.respectOtherHud;
        disableOnLowMemory = defaults.disableOnLowMemory;
        safeMode = defaults.safeMode;
    }

    private void backupCorrupt(Consumer<String> log)
    {
        try
        {
            Path backup = file.resolveSibling(file.getFileName() + ".corrupt");
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            log.accept("[weaver-minimap] moved corrupt config to " + backup);
        }
        catch (IOException failure)
        {
            log.accept("[weaver-minimap] could not back up corrupt config: " + failure);
        }
    }

    private void writeAtomically(String json) throws IOException
    {
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null)
        {
            throw new IOException("config file has no parent: " + file);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, file.getFileName() + ".", ".tmp");
        try
        {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try
            {
                Files.move(temporary, file,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException unsupported)
            {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    private static String normalizeSection(String section)
    {
        return section.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String normalizeThemeId(String value, String fallback)
    {
        if (value == null)
        {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return THEME_ID.matcher(normalized).matches() ? normalized : fallback;
    }

    private static String string(JsonObject root, String key)
    {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString())
        {
            return null;
        }
        return value.getAsString();
    }

    private static boolean bool(JsonObject root, String key, boolean fallback)
    {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
    }

    private static int integer(JsonObject root, String key, int fallback, Consumer<String> log)
    {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber())
        {
            return fallback;
        }
        try
        {
            return value.getAsBigDecimal().intValueExact();
        }
        catch (RuntimeException failure)
        {
            log.accept("[weaver-minimap] config " + key + " is not a 32-bit integer");
            return fallback;
        }
    }

    private static double number(JsonObject root, String key, double fallback,
                                 Consumer<String> log)
    {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber())
        {
            return fallback;
        }
        try
        {
            double number = value.getAsDouble();
            if (!Double.isFinite(number))
            {
                throw new NumberFormatException("not finite");
            }
            return number;
        }
        catch (RuntimeException failure)
        {
            log.accept("[weaver-minimap] config " + key + " is not a finite number");
            return fallback;
        }
    }

    private static <E extends Enum<E>> E enumValue(JsonObject root, String key, Class<E> type,
                                                   E fallback, Consumer<String> log)
    {
        JsonElement value = root.get(key);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString())
        {
            return fallback;
        }
        try
        {
            return Enum.valueOf(type, value.getAsString());
        }
        catch (RuntimeException failure)
        {
            log.accept("[weaver-minimap] config " + key + " has an unknown value: "
                    + value.getAsString());
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
