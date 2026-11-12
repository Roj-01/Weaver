package net.weaver.minimap.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.weaver.minimap.config.MinimapConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Scans loaded client chunks, keeps surface and cave data in memory, and
 * persists dirty regions and cave slices on background threads.
 *
 * <p>All methods except {@link #shutdown()} must be called on the client thread.
 * Surface chunks are scanned with a per-tick budget. Region and cave-slice
 * files are read on a daemon loader thread and written on a daemon writer
 * thread; completion is published back through {@link #tick()}. Rendering code
 * uses {@link #regionIfLoaded(RegionKey)}, {@link #requestRegion(RegionKey)},
 * {@link #caveSlice(RegionKey, int)}, and {@link #requestCaveSlice(RegionKey,
 * int)} so no disk I/O ever runs on the render thread.</p>
 */
public final class MapManager
{
    private static final int CHUNK_RESCAN_TICKS = 400;
    private static final int AUTO_RESCAN_TICKS = 200;
    private static final int AUTO_CANDIDATES_PER_TICK = 2;
    private static final int CAVE_REQUEST_INTERVAL = 20;
    private static final int CAVE_CELLS_PER_SETTING = 8;
    private static final int DEFAULT_REGION_CACHE = 96;

    private final Minecraft minecraft;
    private final MapSettings settings;
    private final Path dataRoot;
    private final ExecutorService writer;
    private final ExecutorService loader;
    private final ExecutorService caveLoader;
    private final LruCache<RegionKey, RegionData> regions;
    private final LruCache<CaveSliceKey, CaveSlice> caveSlices;
    private final Set<RegionKey> textureDirty = new LinkedHashSet<>();
    private final Set<RegionKey> saving = ConcurrentHashMap.newKeySet();
    private final Set<CaveSliceKey> caveSaving = ConcurrentHashMap.newKeySet();
    private final Set<Long> queuedChunks = new HashSet<>();
    private final ArrayDeque<Long> scanQueue = new ArrayDeque<>();
    private final Map<Long, Long> lastScan = new LinkedHashMap<>();
    private final Deque<CaveSliceKey> caveScanQueue = new ArrayDeque<>();
    private final Map<Holder<Biome>, String> biomeIds = new HashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final int[] scanHeights = new int[RegionData.CHUNK_PIXELS * RegionData.CHUNK_PIXELS];
    private final int[] scanColors = new int[RegionData.CHUNK_PIXELS * RegionData.CHUNK_PIXELS];
    private final int[] scanBiomes = new int[RegionData.CHUNK_PIXELS * RegionData.CHUNK_PIXELS];
    private final boolean[] scanWater = new boolean[RegionData.CHUNK_PIXELS * RegionData.CHUNK_PIXELS];

    /** Scratch array for the per-column light multipliers. */
    private final double[] shading = new double[3];

    /** How many blocks a column is walked down while compositing. */
    private static final int MAX_COLUMN_LAYERS = 24;

    /** Deepest water column measured for the shade gradient. */
    private static final int MAX_WATER_DEPTH = 32;
    private final MapCounters counters = new MapCounters();

    private ClientLevel level;
    private String worldKey;
    private String dimension;
    private RegionStorage storage;
    private CaveSliceStorage caveStorage;
    private AsyncStore<RegionKey, RegionData> regionStore;
    private AsyncStore<CaveSliceKey, CaveSlice> caveStore;
    private LevelCaveSource caveSource;
    private CaveSliceBuild caveBuild;
    private CaveSliceKey caveBuildKey;
    private CaveAutoY autoY;
    private long autoBuiltTick = -1000L;
    private int autoCenterX;
    private int autoCenterZ;
    private int autoPlayerY;
    private int caveTargetY = Integer.MIN_VALUE;
    private int lastCaveY = Integer.MIN_VALUE;
    private boolean caveReadyFlag;
    private long epoch;
    private long tick;
    private long lastQueueTick = -100L;
    private long lastCaveRequestTick = -100L;
    private boolean scanBoost;
    private boolean closed;
    private MapStats statsCache;
    private CaveState caveStateCache;

    /**
     * Creates a manager from the mod configuration.
     *
     * @param minecraft the client instance, never {@code null}
     * @param config    the live config, never {@code null}
     * @param dataRoot  the mod data directory, never {@code null}
     */
    public MapManager(Minecraft minecraft, MinimapConfig config, Path dataRoot)
    {
        this(minecraft, (MapSettings) config, dataRoot);
    }

    /**
     * Creates a manager from any settings source.
     *
     * @param minecraft the client instance, never {@code null}
     * @param settings  the live settings, never {@code null}
     * @param dataRoot  the mod data directory, never {@code null}
     */
    public MapManager(Minecraft minecraft, MapSettings settings, Path dataRoot)
    {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot");
        this.writer = Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, "weaver-minimap-writer");
            thread.setDaemon(true);
            return thread;
        });
        this.loader = Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, "weaver-minimap-loader");
            thread.setDaemon(true);
            return thread;
        });
        this.caveLoader = Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, "weaver-minimap-cave-loader");
            thread.setDaemon(true);
            return thread;
        });
        this.regions = new LruCache<>(regionBudget(), this::saveEvictedRegion);
        this.caveSlices = new LruCache<>(caveBudget(), this::forgetEvictedCaveSlice);
    }

    /**
     * Advances scanning, async load publishing, cave work, and save work by one
     * client tick.
     */
    public void tick()
    {
        if (closed)
        {
            return;
        }
        ClientLevel current = minecraft.level;
        if (current == null)
        {
            if (level != null)
            {
                leaveWorld();
            }
            return;
        }
        String currentDimension = MinimapWorlds.dimension(current);
        if (current != level || !currentDimension.equals(dimension))
        {
            joinWorld(current, currentDimension);
        }
        LocalPlayer player = minecraft.player;
        if (player == null)
        {
            return;
        }

        tick++;
        applyCacheBudgets();
        if (tick - lastQueueTick >= 20L || scanQueue.isEmpty())
        {
            queueAround(player);
            lastQueueTick = tick;
        }

        int budget = Math.max(1, settings.scanChunksPerTick()) * (scanBoost ? 4 : 1);
        for (int index = 0; index < budget; index++)
        {
            Long packed = scanQueue.poll();
            if (packed == null)
            {
                break;
            }
            queuedChunks.remove(packed);
            scanChunk((int) (packed >> 32), (int) (long) packed);
        }

        if (regionStore != null)
        {
            regionStore.pump(2);
            regionStore.drain(8, this::acceptLoadedRegion, this::rejectLoadedRegion);
        }
        tickCaves(player);

        if (tick % Math.max(1, settings.saveIntervalTicks()) == 0L)
        {
            flushDirty(2);
        }
        statsCache = null;
        caveStateCache = null;
    }

    /**
     * Returns the current world identifier, or {@code null} before a world is
     * active.
     */
    public String worldKey()
    {
        return worldKey;
    }

    /** Returns the current dimension id, or {@code null}. */
    public String dimensionId()
    {
        return dimension;
    }

    /** Returns the current world generation. */
    public long epoch()
    {
        return epoch;
    }

    /** Returns whether a world is active. */
    public boolean isActive()
    {
        return level != null;
    }

    /**
     * Raises the scan rate and radius while the fullscreen map is open.
     *
     * @param scanBoost whether the map screen is active
     */
    public void setScanBoost(boolean scanBoost)
    {
        this.scanBoost = scanBoost;
    }

    /**
     * Returns a region synchronously, loading it from disk on first use.
     *
     * <p>This method performs disk I/O and exists for compatibility. Rendering
     * code must use {@link #regionIfLoaded(RegionKey)} with
     * {@link #requestRegion(RegionKey)} instead.</p>
     *
     * @param key the region key, never {@code null}
     * @return the region data, or {@code null} when no world is active or the
     *         key belongs to another dimension
     */
    public RegionData region(RegionKey key)
    {
        Objects.requireNonNull(key, "key");
        if (!isCurrent(key))
        {
            return null;
        }
        RegionData existing = regions.get(key);
        if (existing != null)
        {
            counters.regionHit();
            return existing;
        }
        counters.regionMiss();
        RegionData loaded = loadRegion(key);
        regions.put(key, loaded);
        if (regionStore != null)
        {
            regionStore.forget(key);
        }
        return loaded;
    }

    /**
     * Returns a region only when it is already in memory. Never touches disk.
     *
     * @param key the region key, never {@code null}
     * @return the cached region, or {@code null} when it must be requested
     */
    public RegionData regionIfLoaded(RegionKey key)
    {
        Objects.requireNonNull(key, "key");
        if (!isCurrent(key))
        {
            return null;
        }
        RegionData existing = regions.get(key);
        if (existing != null)
        {
            counters.regionHit();
        }
        else
        {
            counters.regionMiss();
        }
        return existing;
    }

    /**
     * Queues an asynchronous region load. Safe to call every frame; duplicate
     * requests are ignored.
     *
     * @param key the region key, never {@code null}
     */
    public void requestRegion(RegionKey key)
    {
        Objects.requireNonNull(key, "key");
        if (!isCurrent(key) || regions.containsKey(key) || regionStore == null)
        {
            return;
        }
        regionStore.request(key);
    }

    /**
     * Returns the load state of a region for loading indicators.
     *
     * @param key the region key, never {@code null}
     * @return the state, never {@code null}
     */
    public RegionLoadState regionState(RegionKey key)
    {
        Objects.requireNonNull(key, "key");
        if (regions.containsKey(key))
        {
            return RegionLoadState.LOADED;
        }
        return regionStore == null ? RegionLoadState.UNLOADED : regionStore.state(key);
    }

    /**
     * Returns the region containing a block, loading it synchronously.
     *
     * @param blockX the world X coordinate
     * @param blockZ the world Z coordinate
     * @return the region data, or {@code null} when no world is active
     */
    public RegionData regionAtBlock(int blockX, int blockZ)
    {
        if (dimension == null)
        {
            return null;
        }
        return region(RegionKey.ofBlock(dimension, blockX, blockZ));
    }

    /**
     * Returns a snapshot of currently loaded regions in least-recently-used
     * order.
     *
     * @return an immutable list, never {@code null}
     */
    public List<RegionData> regions()
    {
        return List.copyOf(regions.values());
    }

    /**
     * Tests whether a region needs a GPU texture upload.
     *
     * @param key the region key
     * @return {@code true} when the texture is stale
     */
    public boolean isTextureDirty(RegionKey key)
    {
        return textureDirty.contains(key);
    }

    /**
     * Marks a region texture as uploaded.
     *
     * @param key the region key
     */
    public void clearTextureDirty(RegionKey key)
    {
        textureDirty.remove(key);
    }

    /**
     * Returns and clears the texture-dirty set.
     *
     * @return a snapshot of regions needing upload
     */
    public Set<RegionKey> takeTextureDirty()
    {
        Set<RegionKey> snapshot = Set.copyOf(textureDirty);
        textureDirty.clear();
        return snapshot;
    }

    /**
     * Returns a cave slice from memory without blocking.
     *
     * @param key the region key, never {@code null}
     * @param y   the sampled Y level
     * @return the cached slice, or {@code null} when it must be requested
     */
    public CaveSlice caveSlice(RegionKey key, int y)
    {
        Objects.requireNonNull(key, "key");
        if (!isCurrent(key))
        {
            return null;
        }
        CaveSlice slice = caveSlices.get(new CaveSliceKey(key, y));
        if (slice != null)
        {
            counters.caveHit();
        }
        else
        {
            counters.caveMiss();
        }
        return slice;
    }

    /**
     * Queues an asynchronous cave slice load or scan. Safe to call every frame;
     * duplicate requests are ignored.
     *
     * @param key the region key, never {@code null}
     * @param y   the sampled Y level
     */
    public void requestCaveSlice(RegionKey key, int y)
    {
        Objects.requireNonNull(key, "key");
        if (!isCurrent(key) || caveStore == null)
        {
            return;
        }
        CaveSliceKey sliceKey = new CaveSliceKey(key, y);
        if (caveSlices.containsKey(sliceKey))
        {
            return;
        }
        caveStore.request(sliceKey);
    }

    /**
     * Returns the selected cave Y, or {@link Integer#MIN_VALUE} when cave mode
     * is disabled or no world is active.
     */
    public int autoCaveY()
    {
        return caveTargetY;
    }

    /**
     * Returns how many blocks below the player the selected cave slice is, or
     * {@code 0} when cave mode is inactive.
     */
    public int caveDepth()
    {
        LocalPlayer player = minecraft.player;
        if (player == null || caveTargetY == Integer.MIN_VALUE)
        {
            return 0;
        }
        return player.blockPosition().getY() - caveTargetY;
    }

    /** Returns whether the player's current cave slice is loaded. */
    public boolean caveReady()
    {
        return caveReadyFlag;
    }

    /**
     * Returns a cave mode snapshot for the UI.
     *
     * @return a snapshot, never {@code null}
     */
    public CaveState caveState()
    {
        if (caveStateCache != null)
        {
            return caveStateCache;
        }
        boolean enabled = settings.caveEnabled();
        int pending = (caveStore == null ? 0 : caveStore.pending())
                + caveScanQueue.size() + (caveBuild == null ? 0 : 1);
        caveStateCache = new CaveState(enabled, settings.caveAuto(), caveTargetY,
                caveReadyFlag, caveDepth(), pending, caveSlices.size(),
                counters.caveScannedCount());
        return caveStateCache;
    }

    /**
     * Returns whether the cave layer is enabled in the settings.
     */
    public boolean caveEnabled()
    {
        return settings.caveEnabled();
    }

    /** Returns whether cave Y selection is automatic. */
    public boolean caveAuto()
    {
        return settings.caveAuto();
    }

    /** Returns whether the surface map is blended over cave slices. */
    public boolean caveSurfaceOverlay()
    {
        return settings.caveSurfaceOverlay();
    }

    /** Returns the surface overlay opacity, from 0 to 1. */
    public double caveSurfaceOpacity()
    {
        return settings.caveSurfaceOpacity();
    }

    /**
     * Forces every previously scanned chunk to be scanned again.
     *
     * <p>Used after a resource reload: the colour cache is rebuilt from the new
     * textures, and the chunks that are already on the map have to be walked
     * again before the new palette reaches them.</p>
     */
    public void invalidateSurface()
    {
        lastScan.clear();
        queuedChunks.clear();
        scanQueue.clear();
        textureDirty.addAll(regions().stream().map(RegionData::key).toList());
    }

    /**
     * Returns the latest statistics snapshot. The snapshot is rebuilt once per
     * client tick and reused for the frame.
     *
     * @return a snapshot, never {@code null}
     */
    public MapStats stats()
    {
        if (statsCache == null)
        {
            statsCache = counters.snapshot(regions.size(),
                    regionStore == null ? 0 : regionStore.pending(),
                    caveSlices.size(), caveStore == null ? 0 : caveStore.pending(),
                    scanQueue.size());
        }
        return statsCache;
    }

    /**
     * Flushes all dirty regions and stops the loader and writer threads.
     */
    public void shutdown()
    {
        if (closed)
        {
            return;
        }
        flushDirty(Integer.MAX_VALUE);
        closed = true;
        closeStores();
        writer.shutdown();
        loader.shutdown();
        caveLoader.shutdown();
        statsCache = null;
        caveStateCache = null;
    }

    private void joinWorld(ClientLevel next, String nextDimension)
    {
        if (level != null)
        {
            flushDirty(Integer.MAX_VALUE);
        }
        closeStores();
        regions.clear();
        caveSlices.clear();
        textureDirty.clear();
        queuedChunks.clear();
        scanQueue.clear();
        lastScan.clear();
        caveScanQueue.clear();
        biomeIds.clear();
        saving.clear();
        caveBuild = null;
        caveBuildKey = null;
        autoY = null;
        caveTargetY = Integer.MIN_VALUE;
        lastCaveY = Integer.MIN_VALUE;
        caveReadyFlag = false;
        counters.reset();
        statsCache = null;
        caveStateCache = null;
        level = next;
        dimension = nextDimension;
        worldKey = MinimapWorlds.worldKey(minecraft);
        Path dimensionRoot = dataRoot.resolve(worldKey)
                .resolve(MinimapWorlds.safe(nextDimension));
        storage = new RegionStorage(dimensionRoot.resolve("regions"));
        caveStorage = new CaveSliceStorage(dimensionRoot.resolve("caves"));
        RegionStorage regionStorage = storage;
        CaveSliceStorage sliceStorage = caveStorage;
        regionStore = new AsyncStore<>(loader, regionStorage::load, this::log);
        caveStore = new AsyncStore<>(caveLoader,
                key -> sliceStorage.load(key.region(), key.y()), this::log);
        caveSource = new LevelCaveSource(next);
        epoch++;
        tick = 0L;
        lastQueueTick = -100L;
        lastCaveRequestTick = -100L;
        log("world=" + worldKey + " dimension=" + nextDimension);
    }

    private void leaveWorld()
    {
        flushDirty(Integer.MAX_VALUE);
        closeStores();
        regions.clear();
        caveSlices.clear();
        textureDirty.clear();
        queuedChunks.clear();
        scanQueue.clear();
        lastScan.clear();
        caveScanQueue.clear();
        biomeIds.clear();
        caveBuild = null;
        caveBuildKey = null;
        autoY = null;
        caveTargetY = Integer.MIN_VALUE;
        lastCaveY = Integer.MIN_VALUE;
        caveReadyFlag = false;
        counters.reset();
        level = null;
        dimension = null;
        worldKey = null;
        storage = null;
        caveStorage = null;
        caveSource = null;
        statsCache = null;
        caveStateCache = null;
        epoch++;
        log("world left");
    }

    private void closeStores()
    {
        if (regionStore != null)
        {
            regionStore.close();
            regionStore = null;
        }
        if (caveStore != null)
        {
            caveStore.close();
            caveStore = null;
        }
    }

    private RegionData loadRegion(RegionKey key)
    {
        if (storage == null)
        {
            return new RegionData(key);
        }
        try
        {
            RegionData loaded = storage.load(key);
            if (loaded != null)
            {
                return loaded;
            }
        }
        catch (IOException failure)
        {
            log("region load failed " + key.fileName() + " reason=" + failure);
            quarantineRegion(key);
        }
        return new RegionData(key);
    }

    private void acceptLoadedRegion(RegionKey key, RegionData loaded)
    {
        if (!isCurrent(key))
        {
            return;
        }
        regions.put(key, loaded != null ? loaded : new RegionData(key));
    }

    private void rejectLoadedRegion(RegionKey key, IOException failure)
    {
        log("region load failed " + key.fileName() + " reason=" + failure);
        quarantineRegion(key);
        if (isCurrent(key))
        {
            regions.put(key, new RegionData(key));
        }
    }

    private void quarantineRegion(RegionKey key)
    {
        if (storage == null)
        {
            return;
        }
        try
        {
            Path moved = storage.quarantine(key);
            if (moved != null)
            {
                log("quarantined region " + moved.getFileName());
            }
        }
        catch (IOException failure)
        {
            log("could not quarantine region " + key.fileName() + " reason=" + failure);
        }
    }

    private void queueAround(LocalPlayer player)
    {
        BlockPos position = player.blockPosition();
        int centerChunkX = position.getX() >> 4;
        int centerChunkZ = position.getZ() >> 4;
        double viewBlocks = settings.size() * settings.zoom();
        int radius = Math.max(scanBoost ? 28 : 2, Math.min(scanBoost ? 40 : 16,
                (int) Math.ceil(viewBlocks / 32.0) + 2));
        long radiusSquared = (long) radius * radius;
        for (int dz = -radius; dz <= radius; dz++)
        {
            for (int dx = -radius; dx <= radius; dx++)
            {
                if ((long) dx * dx + (long) dz * dz > radiusSquared)
                {
                    continue;
                }
                long key = packChunk(centerChunkX + dx, centerChunkZ + dz);
                if (queuedChunks.contains(key))
                {
                    continue;
                }
                Long last = lastScan.get(key);
                if (last != null && tick - last < CHUNK_RESCAN_TICKS)
                {
                    continue;
                }
                queuedChunks.add(key);
                scanQueue.add(key);
            }
        }
    }

    private void scanChunk(int chunkX, int chunkZ)
    {
        if (level == null)
        {
            return;
        }
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ,
                ChunkStatus.FULL, false);
        if (chunk == null)
        {
            return;
        }

        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        RegionKey key = RegionKey.ofBlock(dimension, baseX, baseZ);
        RegionData region = regionIfLoaded(key);
        if (region == null)
        {
            requestRegion(key);
            return;
        }
        Arrays.fill(scanHeights, 0);
        Arrays.fill(scanColors, 0);
        Arrays.fill(scanBiomes, 0);
        Arrays.fill(scanWater, false);
        int minY = level.getMinY();
        int maxY = level.getMaxY() - 1;
        int seaLevel = level.getSeaLevel();
        double day = dayFactor();
        boolean overworld = level.dimension() == ClientLevel.OVERWORLD;
        boolean nether = level.dimension() == ClientLevel.NETHER;
        double shadowR = overworld ? 0.518D : 1.0D;
        double shadowG = overworld ? 0.678D : (nether ? 0.0D : 1.0D);
        double shadowB = overworld ? 1.0D : (nether ? 0.0D : 1.0D);

        for (int localZ = 0; localZ < 16; localZ++)
        {
            for (int localX = 0; localX < 16; localX++)
            {
                int worldX = baseX + localX;
                int worldZ = baseZ + localZ;
                int top = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1,
                        maxY);
                if (top < minY)
                {
                    continue;
                }
                int y = top;
                while (y >= minY && !isMapped(level.getBlockState(cursor.set(worldX, y, worldZ))))
                {
                    y--;
                }
                if (y < minY)
                {
                    continue;
                }

                int index = localZ * 16 + localX;
                BlockState state = level.getBlockState(cursor.set(worldX, y, worldZ));
                int surfaceY = y;
                scanHeights[index] = surfaceY;
                Holder<Biome> biomeHolder = level.getBiome(cursor.set(worldX, surfaceY, worldZ));
                scanBiomes[index] = region.biomeIndex(biomeId(biomeHolder));
                int sky = 15;
                FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty() && fluid.getType().is(FluidTags.WATER))
                {
                    scanWater[index] = true;
                    scanColors[index] = waterColumn(worldX, worldZ, surfaceY, minY, sky, day);
                }
                else
                {
                    scanColors[index] = landColumn(worldX, worldZ, surfaceY, minY, sky, day);
                }
            }
        }

        int localBaseX = Math.floorMod(baseX, RegionData.BLOCKS_PER_REGION);
        int localBaseZ = Math.floorMod(baseZ, RegionData.BLOCKS_PER_REGION);
        boolean changed = false;
        for (int localZ = 0; localZ < 16; localZ++)
        {
            for (int localX = 0; localX < 16; localX++)
            {
                int index = localZ * 16 + localX;
                int color = scanColors[index];
                if (color == 0)
                {
                    continue;
                }
                int height = scanHeights[index];
                int north = localZ > 0 ? scanHeights[index - 16]
                        : level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX + localX, baseZ - 1) - 1;
                int west = localX > 0 ? scanHeights[index - 1]
                        : level.getHeight(Heightmap.Types.WORLD_SURFACE, baseX - 1, baseZ + localZ) - 1;
                if (north < minY)
                {
                    north = height;
                }
                if (west < minY)
                {
                    west = height;
                }
                SurfaceShading.light(height - west, height - north,
                        shadowR, shadowG, shadowB, shading);
                double depth = SurfaceShading.depth(height, seaLevel);
                color = SurfaceShading.scale(color,
                        shading[0] * depth, shading[1] * depth, shading[2] * depth);

                int regionX = localBaseX + localX;
                int regionZ = localBaseZ + localZ;
                region.setColor(regionX, regionZ, color);
                region.setHeight(regionX, regionZ, (short) height);
                region.setBiomeIndex(regionX, regionZ, scanBiomes[index]);
                region.setChunkPresent(regionX / RegionData.CHUNK_PIXELS,
                        regionZ / RegionData.CHUNK_PIXELS, true);
                changed = true;
            }
        }
        if (changed)
        {
            region.markDirty();
            textureDirty.add(key);
        }
        lastScan.put(packChunk(chunkX, chunkZ), tick);
        counters.scannedChunk();
    }

    private String biomeId(Holder<Biome> holder)
    {
        return biomeIds.computeIfAbsent(holder, key -> key.unwrapKey()
                .map(resourceKey -> resourceKey.identifier().toString())
                .orElse(RegionData.UNKNOWN_BIOME));
    }

    /**
     * Returns the daylight factor of the level, from 0 at night to 1 at noon.
     *
     * <p>The map follows the sky, so a night-time minimap is dark the way the
     * world is. Dimension ambient light is folded in so the Nether and the End
     * are never dimmed by the overworld clock.</p>
     */
    private double dayFactor()
    {
        if (level == null)
        {
            return 1.0D;
        }
        try
        {
            float sky = level.environmentAttributes()
                    .getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, BlockPos.ZERO);
            double day = (sky - 0.24D) / 0.76D;
            double ambient = Math.min(1.0D, level.dimensionType().ambientLight() * 24.0D / 15.0D);
            return ambient + (1.0D - ambient) * clamp01(day);
        }
        catch (RuntimeException failure)
        {
            return 1.0D;
        }
    }

    /**
     * Returns whether a block hides everything under it.
     *
     * <p>Leaves count as opaque even though they do not occlude, because a tree
     * canopy is what the map should show from above; a leaf block that let the
     * trunk bleed through would read as noise.</p>
     */
    private static boolean isOpaque(BlockState state)
    {
        if (!state.getFluidState().isEmpty())
        {
            return false;
        }
        if (state.is(BlockTags.LEAVES))
        {
            return true;
        }
        return state.canOcclude() || state.isSolidRender();
    }

    private static double clamp01(double value)
    {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static int channel(double value)
    {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    /**
     * Composites one land column from its surface down.
     *
     * <p>A block that does not fully occlude (ice, glass, a lily pad, snow on
     * top of grass) lets part of the block under it through, and the remaining
     * transmittance shrinks with every layer. That is what keeps a frozen lake
     * looking like ice over water instead of a solid white disc, and what makes
     * a snow-dusted plain keep its grass tint underneath.</p>
     */
    private int landColumn(int worldX, int worldZ, int surfaceY, int minY, int sky, double day)
    {
        double red = 0.0D;
        double green = 0.0D;
        double blue = 0.0D;
        double remaining = 1.0D;
        int y = surfaceY;
        for (int layer = 0; layer < MAX_COLUMN_LAYERS && y >= minY; layer++)
        {
            BlockState current = level.getBlockState(cursor.set(worldX, y, worldZ));
            if (!isMapped(current))
            {
                y--;
                continue;
            }
            boolean opaque = isOpaque(current);
            int argb = MinimapColors.color(level, cursor, current);
            if (argb == 0)
            {
                if (opaque)
                {
                    break;
                }
                y--;
                continue;
            }
            double alpha = opaque ? 1.0D : ((argb >>> 24) & 0xFF) / 255.0D;
            if (!opaque && alpha <= 0.0D)
            {
                alpha = 0.5D;
            }
            int depth = surfaceY - y;
            double light = SurfaceShading.brightness(Math.max(0, sky - depth),
                    level.getBrightness(LightLayer.BLOCK, cursor), day);
            double weight = light * alpha * remaining;
            red += ((argb >> 16) & 0xFF) * weight;
            green += ((argb >> 8) & 0xFF) * weight;
            blue += (argb & 0xFF) * weight;
            remaining *= 1.0D - alpha;
            if (opaque || remaining <= 0.002D)
            {
                break;
            }
            y--;
        }
        if (remaining >= 0.999D)
        {
            return 0;
        }
        return 0xFF000000 | (channel(red) << 16) | (channel(green) << 8) | channel(blue);
    }

    /**
     * Composites one water column over its sea floor.
     *
     * <p>Water is the one block where a single flat colour is never right: a
     * one-block puddle should show sand through it, a deep ocean should be
     * water and nothing else, and the light reaching the floor drops with every
     * block of depth. Measuring the column lets all three fall out of one
     * blend, and gives the map the pale shallows along a coast that make the
     * coastline readable at a glance.</p>
     */
    private int waterColumn(int worldX, int worldZ, int surfaceY, int minY, int sky, double day)
    {
        int depth = 0;
        int floorY = surfaceY;
        while (floorY >= minY && depth < MAX_WATER_DEPTH)
        {
            BlockState current = level.getBlockState(cursor.set(worldX, floorY, worldZ));
            FluidState fluid = current.getFluidState();
            if (fluid.isEmpty() || !fluid.getType().is(FluidTags.WATER))
            {
                break;
            }
            depth++;
            floorY--;
        }

        BlockState water = level.getBlockState(cursor.set(worldX, surfaceY, worldZ));
        int waterArgb = MinimapColors.color(level, cursor, water);
        if (waterArgb == 0)
        {
            return 0;
        }
        double opacity = Math.max(0.25D, Math.min(0.9D, ((waterArgb >>> 24) & 0xFF) / 255.0D));
        double covered = 1.0D - Math.pow(1.0D - opacity, Math.max(1, depth));
        int deepSky = Math.max(0, sky - Math.max(0, depth - 1));
        double waterLight = SurfaceShading.brightness(deepSky,
                level.getBrightness(LightLayer.BLOCK, cursor.set(worldX, surfaceY, worldZ)), day);
        double red = ((waterArgb >> 16) & 0xFF) * waterLight;
        double green = ((waterArgb >> 8) & 0xFF) * waterLight;
        double blue = (waterArgb & 0xFF) * waterLight;
        if (floorY >= minY)
        {
            BlockState floor = level.getBlockState(cursor.set(worldX, floorY, worldZ));
            int floorArgb = isMapped(floor) ? MinimapColors.color(level, cursor, floor) : 0;
            if (floorArgb != 0)
            {
                double floorLight = SurfaceShading.brightness(Math.max(0, sky - depth),
                        level.getBrightness(LightLayer.BLOCK, cursor), day);
                red = red * covered + ((floorArgb >> 16) & 0xFF) * floorLight * (1.0D - covered);
                green = green * covered + ((floorArgb >> 8) & 0xFF) * floorLight * (1.0D - covered);
                blue = blue * covered + (floorArgb & 0xFF) * floorLight * (1.0D - covered);
            }
        }
        return 0xFF000000 | (channel(red) << 16) | (channel(green) << 8) | channel(blue);
    }

    /**
     * Returns whether a block should appear on the map at all.
     *
     * <p>Short grass, ferns, flowers, torches and the top half of a double
     * plant are one pixel of noise each: they cover the ground they grow on and
     * turn a meadow into static. The map skips them, so the pixel shows the
     * grass block underneath, which is what the eye expects from above.</p>
     */
    private static boolean isMapped(BlockState state)
    {
        if (state.isAir())
        {
            return false;
        }
        Block block = state.getBlock();
        if (block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS || block == Blocks.FERN
                || block == Blocks.LARGE_FERN || block == Blocks.DEAD_BUSH
                || block == Blocks.TORCH || block == Blocks.WALL_TORCH)
        {
            return false;
        }
        if (state.is(BlockTags.FLOWERS))
        {
            return false;
        }
        return !(block instanceof DoublePlantBlock);
    }

    private void tickCaves(LocalPlayer player)
    {
        if (!settings.caveEnabled() || caveStore == null || caveSource == null)
        {
            caveTargetY = Integer.MIN_VALUE;
            caveReadyFlag = false;
            caveBuild = null;
            caveBuildKey = null;
            caveScanQueue.clear();
            return;
        }
        int target = resolveCaveY(player);
        if (target != lastCaveY)
        {
            lastCaveY = target;
            caveScanQueue.clear();
            caveBuild = null;
            caveBuildKey = null;
        }
        if (target == Integer.MIN_VALUE)
        {
            caveReadyFlag = false;
            return;
        }

        if (tick - lastCaveRequestTick >= CAVE_REQUEST_INTERVAL)
        {
            lastCaveRequestTick = tick;
            queueCaveSlices(player, target);
        }
        caveStore.pump(2);
        caveStore.drain(4, this::acceptCaveSlice, this::rejectCaveSlice);

        if (caveBuild == null)
        {
            CaveSliceKey next = nextCaveScanKey(target);
            if (next != null)
            {
                caveBuildKey = next;
                caveBuild = new CaveSliceBuild(caveSource, next.region(), next.y(),
                        CavePalette.forDimension(dimension));
            }
        }
        if (caveBuild != null)
        {
            int cellBudget = Math.max(1, settings.caveScanChunksPerTick())
                    * CAVE_CELLS_PER_SETTING;
            if (caveBuild.step(cellBudget))
            {
                CaveSlice slice = caveBuild.slice();
                CaveSliceKey builtKey = caveBuildKey;
                caveSlices.put(builtKey, slice);
                counters.caveSlicesScanned();
                persistCaveSlice(builtKey, slice);
                caveBuild = null;
                caveBuildKey = null;
            }
        }

        BlockPos position = player.blockPosition();
        RegionKey playerRegion = RegionKey.ofBlock(dimension, position.getX(),
                position.getZ());
        caveReadyFlag = caveSlices.containsKey(new CaveSliceKey(playerRegion, target));
    }

    private int resolveCaveY(LocalPlayer player)
    {
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        int minimum = minY + 2;
        int maximum = Math.max(minimum, maxY - 2);
        if (!settings.caveAuto())
        {
            caveTargetY = clamp(settings.caveY(), minimum, maximum);
            return caveTargetY;
        }
        BlockPos position = player.blockPosition();
        int playerX = position.getX();
        int playerY = position.getY();
        int playerZ = position.getZ();
        boolean rebuild = autoY == null
                || tick - autoBuiltTick >= AUTO_RESCAN_TICKS
                || (autoY.candidateCount() == 0 && tick - autoBuiltTick >= 20L)
                || Math.abs(playerX - autoCenterX) > 16
                || Math.abs(playerZ - autoCenterZ) > 16
                || Math.abs(playerY - autoPlayerY) > 16;
        if (rebuild)
        {
            int surfaceMin = sampleSurfaceMin(playerX, playerZ, minY);
            autoY = new CaveAutoY(playerY, minY, surfaceMin);
            autoBuiltTick = tick;
            autoCenterX = playerX;
            autoCenterZ = playerZ;
            autoPlayerY = playerY;
            caveTargetY = clamp(autoY.fallbackY(), minimum, maximum);
        }
        if (!autoY.done())
        {
            autoY.tick(caveSource, playerX, playerZ, AUTO_CANDIDATES_PER_TICK);
        }
        if (autoY.done())
        {
            int result = autoY.result();
            caveTargetY = result == Integer.MIN_VALUE
                    ? Integer.MIN_VALUE : clamp(result, minimum, maximum);
        }
        return caveTargetY;
    }

    private int sampleSurfaceMin(int centerX, int centerZ, int fallback)
    {
        int minimum = Integer.MAX_VALUE;
        for (int dz = -CaveAutoY.SAMPLE_RADIUS; dz <= CaveAutoY.SAMPLE_RADIUS;
             dz += CaveAutoY.SAMPLE_STEP)
        {
            for (int dx = -CaveAutoY.SAMPLE_RADIUS; dx <= CaveAutoY.SAMPLE_RADIUS;
                 dx += CaveAutoY.SAMPLE_STEP)
            {
                int surface = caveSource.surfaceY(centerX + dx, centerZ + dz);
                if (surface != Integer.MIN_VALUE && surface < minimum)
                {
                    minimum = surface;
                }
            }
        }
        return minimum == Integer.MAX_VALUE ? fallback : minimum;
    }

    private void queueCaveSlices(LocalPlayer player, int target)
    {
        BlockPos position = player.blockPosition();
        int centerRegionX = position.getX() >> 8;
        int centerRegionZ = position.getZ() >> 8;
        int radius = Math.min(scanBoost ? 2 : 1, regionRadius());
        for (int dz = -radius; dz <= radius; dz++)
        {
            for (int dx = -radius; dx <= radius; dx++)
            {
                RegionKey regionKey = new RegionKey(dimension,
                        centerRegionX + dx, centerRegionZ + dz);
                CaveSliceKey sliceKey = new CaveSliceKey(regionKey, target);
                if (caveSlices.containsKey(sliceKey))
                {
                    continue;
                }
                caveStore.request(sliceKey);
            }
        }
    }

    private int regionRadius()
    {
        double viewBlocks = settings.size() * settings.zoom();
        return Math.max(1, Math.min(4, (int) Math.ceil(viewBlocks / 512.0) + 1));
    }

    private CaveSliceKey nextCaveScanKey(int target)
    {
        while (!caveScanQueue.isEmpty())
        {
            CaveSliceKey candidate = caveScanQueue.poll();
            if (candidate.y() != target || caveSlices.containsKey(candidate))
            {
                continue;
            }
            RegionLoadState state = caveStore.state(candidate);
            if (state == RegionLoadState.QUEUED || state == RegionLoadState.LOADING)
            {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private void acceptCaveSlice(CaveSliceKey key, CaveSlice slice)
    {
        if (!isCurrent(key.region()))
        {
            return;
        }
        if (slice == null)
        {
            caveScanQueue.add(key);
            return;
        }
        if (!slice.key().equals(key.region()) || slice.y() != key.y())
        {
            log("cave slice mismatch for " + key);
            return;
        }
        caveSlices.put(key, slice);
    }

    private void persistCaveSlice(CaveSliceKey key, CaveSlice slice)
    {
        CaveSliceStorage target = caveStorage;
        if (target == null || !caveSaving.add(key))
        {
            return;
        }
        try
        {
            writer.submit(() ->
            {
                try
                {
                    target.save(slice);
                }
                catch (IOException failure)
                {
                    log("cave slice save failed " + key + " reason=" + failure);
                }
                finally
                {
                    caveSaving.remove(key);
                }
            });
        }
        catch (RejectedExecutionException rejected)
        {
            caveSaving.remove(key);
        }
    }

    private void rejectCaveSlice(CaveSliceKey key, IOException failure)
    {
        log("cave slice load failed " + key + " reason=" + failure);
        if (caveStorage != null)
        {
            try
            {
                Path moved = caveStorage.quarantine(key.region(), key.y());
                if (moved != null)
                {
                    log("quarantined cave slice " + moved.getFileName());
                }
            }
            catch (IOException quarantineFailure)
            {
                log("could not quarantine cave slice " + key + " reason="
                        + quarantineFailure);
            }
        }
        caveScanQueue.add(key);
    }

    private void flushDirty(int maximum)
    {
        if (storage == null)
        {
            return;
        }
        int submitted = 0;
        for (RegionData data : new ArrayList<>(regions.values()))
        {
            if (submitted >= maximum)
            {
                break;
            }
            if (!data.isDirty() || saving.contains(data.key()))
            {
                continue;
            }
            RegionData snapshot = data.copy();
            data.clearDirty();
            saving.add(data.key());
            submitted++;
            RegionStorage target = storage;
            try
            {
                writer.submit(() ->
                {
                    try
                    {
                        target.save(snapshot);
                    }
                    catch (IOException failure)
                    {
                        log("region save failed " + snapshot.key().fileName()
                                + " reason=" + failure);
                    }
                    finally
                    {
                        saving.remove(snapshot.key());
                    }
                });
            }
            catch (RejectedExecutionException rejected)
            {
                saving.remove(snapshot.key());
                data.markDirty();
                log("region writer rejected " + snapshot.key().fileName());
            }
        }
    }

    private void forgetEvictedCaveSlice(CaveSlice slice)
    {
        if (caveStore != null)
        {
            caveStore.forget(new CaveSliceKey(slice.key(), slice.y()));
        }
    }

    private void saveEvictedRegion(RegionData data)
    {
        textureDirty.remove(data.key());
        if (regionStore != null)
        {
            regionStore.forget(data.key());
        }
        if (!data.isDirty() || storage == null)
        {
            return;
        }
        RegionData snapshot = data.copy();
        data.clearDirty();
        saving.add(data.key());
        RegionStorage target = storage;
        try
        {
            writer.submit(() ->
            {
                try
                {
                    target.save(snapshot);
                }
                catch (IOException failure)
                {
                    log("region eviction save failed " + snapshot.key().fileName()
                            + " reason=" + failure);
                }
                finally
                {
                    saving.remove(snapshot.key());
                }
            });
        }
        catch (RejectedExecutionException rejected)
        {
            saving.remove(data.key());
        }
    }

    private void applyCacheBudgets()
    {
        regions.setMaxEntries(regionBudget());
        caveSlices.setMaxEntries(caveBudget());
    }

    private int regionBudget()
    {
        int configured = settings.regionCacheSize();
        return configured > 0 ? configured : DEFAULT_REGION_CACHE;
    }

    private int caveBudget()
    {
        int configured = settings.caveCacheSize();
        return configured > 0 ? configured : 64;
    }

    private boolean isCurrent(RegionKey key)
    {
        return isActive() && key.dimension().equals(dimension);
    }

    private static int clamp(int value, int minimum, int maximum)
    {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long packChunk(int chunkX, int chunkZ)
    {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private void log(String message)
    {
        System.err.println("[weaver-minimap] " + message);
    }
}
