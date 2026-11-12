package net.weaver.minimap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.ModContext;
import net.weaver.api.client.ClientChunkContext;
import net.weaver.api.client.ClientChunkListener;
import net.weaver.api.client.ClientRegistry;
import net.weaver.api.client.ClientTickContext;
import net.weaver.api.client.ClientTickListener;
import net.weaver.api.client.ResourceReloadContext;
import net.weaver.api.client.ScreenFactory;
import net.weaver.api.client.TooltipContext;
import net.weaver.api.client.TooltipProvider;
import net.weaver.api.game.client.render.WorldRenderContext;
import net.weaver.api.game.client.render.WorldRenderListener;
import net.weaver.api.game.client.render.WorldRenderRegistry;
import net.weaver.minimap.api.MinimapApi;
import net.weaver.minimap.config.MinimapConfig;
import net.weaver.minimap.input.KeybindSpec;
import net.weaver.minimap.input.Keybinds;
import net.weaver.minimap.map.MapManager;
import net.weaver.minimap.map.MinimapColors;
import net.weaver.minimap.map.RegionKey;
import net.weaver.minimap.render.HudMapCompositor;
import net.weaver.minimap.render.MinimapHudLayer;
import net.weaver.minimap.render.RegionTextureCache;
import net.weaver.minimap.ui.ImportExportScreen;
import net.weaver.minimap.ui.MinimapScreen;
import net.weaver.minimap.ui.MinimapSettingsScreen;
import net.weaver.minimap.ui.WaypointEditorScreen;
import net.weaver.minimap.ui.theme.ThemeManager;
import net.weaver.minimap.waypoint.Waypoint;
import net.weaver.minimap.waypoint.WaypointManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client bootstrap and process-wide minimap services.
 *
 * <p>The HUD layer, screens, tick listener, tooltip provider, world render
 * listener, and chunk listener are registered during {@code onClientSetup}.
 * Map data and waypoints are created lazily on the first client tick, when the
 * client instance and world directories are reliably available.</p>
 */
public final class MinimapClient implements ClientTickListener
{
    public static final String MOD_ID = "weaver-minimap";
    public static final String MAP_SCREEN_ID = "weaver-minimap:map";
    public static final String SETTINGS_SCREEN_ID = "weaver-minimap:settings";

    private static MinimapClient instance;

    private final ModContext context;
    private final MinimapConfig config;
    private final RegionTextureCache textures = new RegionTextureCache();
    private final HudMapCompositor hudCompositor = new HudMapCompositor();
    private final net.weaver.minimap.render.HudChromeCache hudChrome = new net.weaver.minimap.render.HudChromeCache();
    private final MinimapHudLayer hudLayer;
    private final MinimapApi api = MinimapApi.shared();

    private MapManager maps;
    private WaypointManager waypoints;
    private String waypointWorldKey;
    private long textureEpoch = -1L;
    private long lastEpoch = -1L;
    private boolean keysAttempted;
    private boolean keysRegistered;
    private boolean hudRendered;
    private boolean proofOpened;
    private boolean deathHandled;
    private boolean configDirty;
    private boolean tooltipProofLogged;
    private boolean beamProofLogged;
    private boolean chunkProofLogged;
    private long hudHiddenUntil;
    private long lastSaveTick;
    private long lastDeathTick;
    private long tickCounter;
    private String lastThemeId;
    private int lastAccent;
    private boolean lastCustomAccent;
    private boolean focusWaypointsOnMap;

    private MinimapClient(ModContext context)
    {
        this.context = context;
        this.config = MinimapConfig.load(context.configDir().resolve("minimap.json"),
                context::log);
        this.hudLayer = new MinimapHudLayer(this);
    }

    /**
     * Installs the client services once.
     *
     * @param context the client mod context, never {@code null}
     */
    public static void install(ModContext context)
    {
        if (instance != null)
        {
            return;
        }
        MinimapClient client = new MinimapClient(context);
        instance = client;
        client.applyTheme(true);
        client.register();
    }

    /** Returns the installed client, or {@code null}. */
    public static MinimapClient instance()
    {
        return instance;
    }

    /** The live configuration. */
    public MinimapConfig config()
    {
        return config;
    }

    /** The HUD layer, never {@code null}. */
    public MinimapHudLayer hudLayer()
    {
        return hudLayer;
    }

    /** The HUD CPU compositor. */
    public HudMapCompositor hudCompositor()
    {
        return hudCompositor;
    }

    /** The cached HUD chrome texture. */
    public net.weaver.minimap.render.HudChromeCache hudChrome()
    {
        return hudChrome;
    }

    /** The fullscreen texture cache for the current world. */
    public RegionTextureCache textures()
    {
        return texturesForCurrentWorld();
    }

    /** The public mod API instance. */
    public MinimapApi api()
    {
        return api;
    }

    /** The map manager, possibly {@code null} before the first world tick. */
    public MapManager maps()
    {
        return maps;
    }

    /** The current waypoint manager, possibly {@code null} before a world. */
    public WaypointManager waypoints()
    {
        return waypoints;
    }

    /** The client instance, or {@code null}. */
    public Minecraft minecraft()
    {
        return Minecraft.getInstance();
    }

    /** Writes one line to the loader log. */
    public void log(String message)
    {
        context.log(message);
    }

    /** Whether the HUD is temporarily hidden by the hide keybind. */
    public boolean isHudTemporarilyHidden()
    {
        return System.currentTimeMillis() < hudHiddenUntil;
    }

    /** Whether a deterministic screenshot proof is driving the client. */
    public boolean isProofMode()
    {
        return !MinimapProof.mode().isBlank();
    }

    /**
     * Returns the texture cache, clearing it when the world generation changes.
     */
    public RegionTextureCache texturesForCurrentWorld()
    {
        if (maps != null && maps.epoch() != textureEpoch)
        {
            textures.clear();
            textureEpoch = maps.epoch();
        }
        return textures;
    }

    /** Records the first successfully rendered HUD pass. */
    public void markHudRendered()
    {
        if (!hudRendered)
        {
            hudRendered = true;
            context.log("MINIMAP HUD rendered");
        }
    }

    /** Applies the configured theme to the widget toolkit. */
    public void applyTheme(boolean force)
    {
        String themeId = config.themeId();
        int accent = config.accentColor();
        boolean custom = config.customAccent();
        if (!force && themeId.equals(lastThemeId) && accent == lastAccent
                && custom == lastCustomAccent)
        {
            return;
        }
        lastThemeId = themeId;
        lastAccent = accent;
        lastCustomAccent = custom;
        ThemeManager.get().setThemeId(themeId);
        if (custom || "custom".equalsIgnoreCase(themeId))
        {
            ThemeManager.get().setAccent(accent);
        }
    }

    /** Marks the configuration dirty; it is written on the next save tick. */
    public void saveConfigSoon()
    {
        configDirty = true;
    }

    /** Writes the configuration immediately. */
    public void saveConfig()
    {
        config.saveQuietly(context::log);
        configDirty = false;
        applyTheme(false);
    }

    /** Adds a waypoint at the player's current block position. */
    public void addWaypointAtPlayer()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || maps == null || maps.dimensionId() == null)
        {
            return;
        }
        ensureWaypoints();
        if (waypoints == null)
        {
            return;
        }
        LocalPlayer player = minecraft.player;
        String name = nextWaypointName();
        int color = nextWaypointColor();
        Waypoint waypoint = new Waypoint(UUID.randomUUID(), name, maps.dimensionId(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ(), color, "pin", "",
                true, false, false, System.currentTimeMillis(), System.currentTimeMillis(),
                Map.of());
        waypoints.add(waypoint);
        saveWaypoints(minecraft, "Waypoint saved");
    }

    /**
     * Adds a waypoint at a world position.
     *
     * @param worldX the block X
     * @param worldZ the block Z
     */
    public void addWaypointAt(double worldX, double worldZ)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || maps == null || maps.dimensionId() == null)
        {
            return;
        }
        ensureWaypoints();
        if (waypoints == null)
        {
            return;
        }
        int blockX = (int) Math.floor(worldX);
        int blockZ = (int) Math.floor(worldZ);
        int blockY = surfaceY(minecraft, blockX, blockZ);
        String name = nextWaypointName();
        Waypoint waypoint = new Waypoint(UUID.randomUUID(), name, maps.dimensionId(),
                blockX, blockY, blockZ, nextWaypointColor(), "pin", "", true, false, false,
                System.currentTimeMillis(), System.currentTimeMillis(), Map.of());
        waypoints.add(waypoint);
        saveWaypoints(minecraft, "Waypoint saved");
    }

    /**
     * Adds or updates a waypoint and saves the store.
     *
     * @param waypoint the waypoint, never {@code null}
     */
    public void saveWaypoint(Waypoint waypoint)
    {
        if (waypoint == null || waypoints == null)
        {
            return;
        }
        if (waypoints.contains(waypoint.id()))
        {
            waypoints.update(waypoint);
        }
        else
        {
            waypoints.add(waypoint);
        }
        saveWaypoints(Minecraft.getInstance(), "Waypoint saved");
    }

    /**
     * Removes a waypoint and saves the waypoint list.
     *
     * @param waypoint the waypoint to remove, never {@code null}
     * @return {@code true} when it was removed
     */
    public boolean removeWaypoint(Waypoint waypoint)
    {
        if (waypoint == null || waypoints == null)
        {
            return false;
        }
        boolean removed = waypoints.remove(waypoint.id());
        if (removed)
        {
            saveWaypoints(Minecraft.getInstance(), "Waypoint removed");
        }
        return removed;
    }

    /**
     * Moves a waypoint to a new block position without changing its other fields.
     *
     * @param id the waypoint id
     * @param x  the new block X
     * @param y  the new block Y
     * @param z  the new block Z
     * @return the updated waypoint, or empty when it does not exist
     */
    public java.util.Optional<Waypoint> moveWaypoint(UUID id, int x, int y, int z)
    {
        if (waypoints == null)
        {
            return java.util.Optional.empty();
        }
        java.util.Optional<Waypoint> moved = waypoints.move(id, x, y, z);
        if (moved.isPresent())
        {
            saveWaypoints(Minecraft.getInstance(), "Waypoint moved");
        }
        return moved;
    }

    /** Changes the zoom and persists the config. */
    public void zoom(double factor)
    {
        config.zoomBy(factor);
        saveConfigSoon();
    }

    /** Opens the fullscreen map. */
    public void openMap()
    {
        focusWaypointsOnMap = false;
        ClientRegistry.openScreen(MAP_SCREEN_ID);
    }

    /** Opens the fullscreen map focused on the waypoint list. */
    public void openWaypoints()
    {
        focusWaypointsOnMap = true;
        ClientRegistry.openScreen(MAP_SCREEN_ID);
    }

    /** Consumes the waypoint-focus request from the map screen. */
    public boolean consumeWaypointFocus()
    {
        boolean focus = focusWaypointsOnMap;
        focusWaypointsOnMap = false;
        return focus;
    }

    /** Opens the settings screen. */
    public void openSettings()
    {
        ClientRegistry.openScreen(SETTINGS_SCREEN_ID);
    }

    /** Opens the waypoint editor for a new or existing waypoint. */
    public void openWaypointEditor(Waypoint existing, int x, int y, int z)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null)
        {
            return;
        }
        Screen parent = minecraft.gui.screen();
        minecraft.setScreenAndShow(new WaypointEditorScreen(this, parent, existing, x, y, z));
    }

    /** Opens the waypoint import/export screen. */
    public void openImportExport()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null)
        {
            return;
        }
        minecraft.setScreenAndShow(new ImportExportScreen(this, minecraft.gui.screen()));
    }

    /** Cycles surface -> auto cave -> manual cave -> surface. */
    public void cycleCave()
    {
        if (!config.caveEnabled())
        {
            config.setCaveEnabled(true);
            config.setCaveAuto(true);
        }
        else if (config.caveAuto())
        {
            config.setCaveAuto(false);
            int auto = maps == null ? Integer.MIN_VALUE : maps.autoCaveY();
            if (auto == Integer.MIN_VALUE && minecraft().player != null)
            {
                auto = minecraft().player.getBlockY() - 24;
            }
            config.setCaveY(auto == Integer.MIN_VALUE ? -32 : auto);
        }
        else
        {
            config.setCaveEnabled(false);
        }
        saveConfigSoon();
        toast(caveLabel());
    }

    /** Toggles all entity categories together. */
    public void toggleEntities()
    {
        boolean enabled = !config.showEntities();
        config.setShowEntities(enabled);
        config.setShowHostile(enabled);
        config.setShowPassive(enabled);
        config.setShowItems(enabled);
        config.setShowOtherEntities(enabled);
        saveConfigSoon();
        toast(enabled ? "Entities shown" : "Entities hidden");
    }

    /** Toggles waypoint labels. */
    public void toggleWaypointLabels()
    {
        config.setShowWaypointLabels(!config.showWaypointLabels());
        saveConfigSoon();
        toast(config.showWaypointLabels() ? "Waypoint labels shown" : "Waypoint labels hidden");
    }

    /** Toggles the whole HUD. */
    public void toggleHud()
    {
        config.setEnabled(!config.enabled());
        saveConfigSoon();
        toast(config.enabled() ? "Minimap shown" : "Minimap hidden");
    }

    /** Temporarily hides the HUD for three seconds. */
    public void hideHudTemporarily()
    {
        hudHiddenUntil = System.currentTimeMillis() + 3000L;
    }

    /** Returns a short cave-mode label. */
    public String caveLabel()
    {
        if (!config.caveEnabled())
        {
            return "Cave mode off";
        }
        if (config.caveAuto())
        {
            return "Cave mode auto";
        }
        return "Cave mode Y " + config.caveY();
    }

    private void register()
    {
        try
        {
            ClientRegistry.registerHudLayer(hudLayer);
            ClientRegistry.registerScreenFactory(new ScreenFactory()
            {
                @Override
                public String id()
                {
                    return MAP_SCREEN_ID;
                }

                @Override
                public Object createScreen(Object minecraft, Object parentScreen)
                {
                    return new MinimapScreen(MinimapClient.this);
                }
            });
            ClientRegistry.registerScreenFactory(new ScreenFactory()
            {
                @Override
                public String id()
                {
                    return SETTINGS_SCREEN_ID;
                }

                @Override
                public Object createScreen(Object minecraft, Object parentScreen)
                {
                    return new MinimapSettingsScreen(MinimapClient.this);
                }
            });
            ClientRegistry.registerTickListener(this);
            registerTooltipProvider();
            registerWorldRenderListener();
            registerChunkListener();
            registerResourceReloadListener();
            context.log("MINIMAP client ready");
        }
        catch (Throwable failure)
        {
            context.log("MINIMAP client setup failed: " + failure);
        }
    }

    private void registerTooltipProvider()
    {
        try
        {
            ClientRegistry.registerTooltipProvider(new TooltipProvider()
            {
                @Override
                public String id()
                {
                    return "weaver-minimap:map_tooltip";
                }

                @Override
                public void append(TooltipContext tooltipContext, List<String> lines)
                {
                    Object itemStack = tooltipContext.itemStack();
                    if (!(itemStack instanceof ItemStack stack))
                    {
                        return;
                    }
                    if (!stack.getItem().getDescriptionId().contains("filled_map"))
                    {
                        return;
                    }
                    lines.add("Weaver Minimap: press M for the map");
                    if (!tooltipProofLogged)
                    {
                        tooltipProofLogged = true;
                        System.out.println("[weaver-minimap] TOOLTIP ok");
                    }
                }
            });
        }
        catch (Throwable failure)
        {
            context.log("MINIMAP tooltip provider unavailable: " + failure);
        }
    }

    private void registerWorldRenderListener()
    {
        try
        {
            WorldRenderRegistry.register(new WorldRenderListener()
            {
                @Override
                public String id()
                {
                    return "weaver-minimap:beams";
                }

                @Override
                public int order()
                {
                    return 500;
                }

                @Override
                public void render(WorldRenderContext renderContext)
                {
                    drawWaypointBeams(renderContext);
                }
            });
        }
        catch (Throwable failure)
        {
            context.log("MINIMAP world render listener unavailable: " + failure);
        }
    }

    private void drawWaypointBeams(WorldRenderContext renderContext)
    {
        if (!config.waypointBeamEnabled() || waypoints == null || maps == null)
        {
            return;
        }
        String dimension = maps.dimensionId();
        if (dimension == null || renderContext.level() == null)
        {
            return;
        }
        Vec3 camera = renderContext.cameraPosition();
        double maxDistance = config.waypointBeamDistance();
        double maxSquared = maxDistance * maxDistance;
        int drawn = 0;
        for (Waypoint waypoint : waypoints.inDimension(dimension))
        {
            if (!waypoint.visible() || drawn >= 64)
            {
                continue;
            }
            double dx = waypoint.x() + 0.5D - camera.x;
            double dz = waypoint.z() + 0.5D - camera.z;
            if (dx * dx + dz * dz > maxSquared)
            {
                continue;
            }
            int color = (waypoint.death() ? config.deathColor() : waypoint.color())
                    & 0xFFFFFF;
            int tinted = 0xD0000000 | color;
            Vec3 base = new Vec3(waypoint.x() + 0.5D, waypoint.y() + 1.0D,
                    waypoint.z() + 0.5D);
            Vec3 top = base.add(0.0D, 72.0D, 0.0D);
            renderContext.sink().line(base, top, tinted, 2.5F);
            renderContext.sink().line(base.add(-0.9D, 0.0D, 0.0D),
                    base.add(0.9D, 0.0D, 0.0D), tinted, 2.5F);
            renderContext.sink().line(base.add(0.0D, 0.0D, -0.9D),
                    base.add(0.0D, 0.0D, 0.9D), tinted, 2.5F);
            renderContext.sink().point(base, tinted, 4.0F);
            renderContext.sink().text(waypoint.name(), top, tinted, 1.0F);
            drawn++;
        }
        if (drawn > 0 && !beamProofLogged)
        {
            beamProofLogged = true;
            System.out.println("[weaver-minimap] WORLD RENDER ok listeners=1");
        }
    }

    private void registerChunkListener()
    {
        try
        {
            ClientRegistry.registerChunkListener(new ClientChunkListener()
            {
                @Override
                public void onChunkLoad(ClientChunkContext chunkContext)
                {
                    warmRegion(chunkContext.dimension(), chunkContext.chunkX(),
                            chunkContext.chunkZ());
                }

                @Override
                public void onChunkUnload(ClientChunkContext chunkContext)
                {
                    warmRegion(chunkContext.dimension(), chunkContext.chunkX(),
                            chunkContext.chunkZ());
                }

                @Override
                public void onBlockUpdate(net.weaver.api.client.ClientBlockContext blockContext)
                {
                    warmRegion(blockContext.dimension(), blockContext.x() >> 4,
                            blockContext.z() >> 4);
                }
            });
        }
        catch (Throwable failure)
        {
            context.log("MINIMAP chunk listener unavailable: " + failure);
        }
    }

    private void warmRegion(String dimension, int chunkX, int chunkZ)
    {
        if (maps == null || dimension == null || !dimension.equals(maps.dimensionId()))
        {
            return;
        }
        RegionKey key = RegionKey.ofBlock(dimension, chunkX << 4, chunkZ << 4);
        maps.requestRegion(key);
        if (!chunkProofLogged)
        {
            chunkProofLogged = true;
            System.out.println("[weaver-minimap] CHUNK EVENTS ok type=load");
        }
    }

    /**
     * Drops the texture-derived block colours when a resource pack changes.
     *
     * <p>A pack swap replaces every block texture, so the cached averages are
     * stale the moment the reload finishes; without this the map keeps the old
     * pack's palette until the game restarts. Regions are also queued for a
     * rescan so the change reaches chunks that are already on the map.</p>
     */
    private void registerResourceReloadListener()
    {
        try
        {
            ClientRegistry.registerResourceReloadListener(reloadContext ->
            {
                if (reloadContext.phase() != ResourceReloadContext.Phase.FINISHED)
                {
                    return;
                }
                MinimapColors.reset();
                if (maps != null)
                {
                    maps.invalidateSurface();
                }
            });
        }
        catch (Throwable failure)
        {
            context.log("MINIMAP resource reload listener unavailable: " + failure);
        }
    }

    @Override
    public void onTick(ClientTickContext tickContext)
    {
        if (!(tickContext.minecraft() instanceof Minecraft minecraft))
        {
            return;
        }
        if (maps == null)
        {
            maps = new MapManager(minecraft, config, context.dataDir());
        }
        tickCounter++;
        maps.tick();
        if (tickCounter % 200L == 0L && minecraft.gui != null
                && minecraft.gui.screen() == null)
        {
            System.out.println("[weaver-minimap] PERF hud_us="
                    + (hudLayer.lastRenderNanos() / 1000L) + " fps=" + minecraft.getFps()
                    + " regions=" + maps.stats().loadedRegions());
        }
        if (maps.epoch() != lastEpoch)
        {
            lastEpoch = maps.epoch();
            textures.clear();
            hudCompositor.close();
            hudLayer.resetSmoothing();
        }
        ensureWaypoints();
        registerKeys(minecraft);
        applyTheme(false);
        handleDeath(minecraft);
        expireTemporaryWaypoints();
        flushConfig(minecraft);
        openProofScreen();
        MinimapProof.tick(this, minecraft);
    }

    private void handleDeath(Minecraft minecraft)
    {
        LocalPlayer player = minecraft.player;
        if (player == null)
        {
            return;
        }
        Screen screen = minecraft.gui == null ? null : minecraft.gui.screen();
        boolean dead = player.isDeadOrDying() || screen instanceof DeathScreen;
        if (!dead)
        {
            deathHandled = false;
            return;
        }
        if (deathHandled || !config.deathWaypointEnabled() || maps == null
                || maps.dimensionId() == null || waypoints == null)
        {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDeathTick < 1500L)
        {
            return;
        }
        deathHandled = true;
        lastDeathTick = now;
        waypoints.setDeathWaypoint(maps.dimensionId(), player.getBlockX(), player.getBlockY(),
                player.getBlockZ(), config.deathColor());
        saveWaypoints(minecraft, "Death waypoint set");
    }

    private void expireTemporaryWaypoints()
    {
        if (waypoints == null || maps == null || tickCounter % 200L != 0L)
        {
            return;
        }
        int removed = waypoints.expireTemporaryMinutes(config.temporaryWaypointMinutes());
        if (removed > 0)
        {
            saveWaypoints(Minecraft.getInstance(), "Temporary waypoints expired");
        }
    }

    private void flushConfig(Minecraft minecraft)
    {
        if (!configDirty || tickCounter - lastSaveTick < 20L)
        {
            return;
        }
        lastSaveTick = tickCounter;
        saveConfig();
    }

    private void ensureWaypoints()
    {
        if (maps == null)
        {
            return;
        }
        String world = maps.worldKey();
        if (world == null || world.equals(waypointWorldKey))
        {
            return;
        }
        waypointWorldKey = world;
        Path directory = context.dataDir().resolve("worlds").resolve(world);
        WaypointManager manager = WaypointManager.forWorld(directory);
        try
        {
            manager.load();
        }
        catch (IOException failure)
        {
            context.log("MINIMAP waypoint load failed: " + failure);
            manager = new WaypointManager(new net.weaver.minimap.waypoint.WaypointStore(
                    directory.resolve("waypoints.json")));
            try
            {
                manager.save();
            }
            catch (IOException ignored)
            {
            }
        }
        waypoints = manager;
    }

    private void registerKeys(Minecraft minecraft)
    {
        if (keysRegistered || keysAttempted || minecraft.options == null
                || minecraft.level == null)
        {
            return;
        }
        keysAttempted = true;
        try
        {
            for (KeybindSpec spec : Keybinds.all())
            {
                registerKey(spec);
            }
            keysRegistered = true;
            context.log("MINIMAP keys registered");
        }
        catch (Throwable failure)
        {
            context.log("MINIMAP key registration failed: " + failure);
        }
    }

    private void registerKey(KeybindSpec spec)
    {
        Runnable action = switch (spec.id())
        {
            case Keybinds.OPEN_MAP -> this::openMap;
            case Keybinds.OPEN_SETTINGS -> this::openSettings;
            case Keybinds.ADD_WAYPOINT -> this::addWaypointAtPlayer;
            case Keybinds.TOGGLE_HUD -> this::toggleHud;
            case Keybinds.ZOOM_IN -> () -> zoom(0.8D);
            case Keybinds.ZOOM_OUT -> () -> zoom(1.25D);
            case Keybinds.CYCLE_CAVE -> this::cycleCave;
            case Keybinds.TOGGLE_ENTITIES -> this::toggleEntities;
            case Keybinds.TOGGLE_WAYPOINT_LABELS -> this::toggleWaypointLabels;
            case Keybinds.HIDE_HUD_TEMP -> this::hideHudTemporarily;
            case Keybinds.OPEN_WAYPOINTS -> this::openWaypoints;
            default -> () ->
            {
            };
        };
        ClientRegistry.registerKeyMapping(Keybinds.mappingSpec(spec), action);
    }

    private void saveWaypoints(Minecraft minecraft, String message)
    {
        if (waypoints == null)
        {
            return;
        }
        try
        {
            waypoints.save();
            System.out.println("[weaver-minimap] WAYPOINT ok");
            if (minecraft != null && minecraft.player != null && message != null)
            {
                minecraft.player.sendOverlayMessage(Component.literal(message));
            }
        }
        catch (IOException failure)
        {
            context.log("MINIMAP waypoint save failed: " + failure);
        }
    }

    private String nextWaypointName()
    {
        int count = waypoints == null ? 0 : waypoints.count();
        return "Waypoint " + (count + 1);
    }

    private int nextWaypointColor()
    {
        int count = waypoints == null ? 0 : waypoints.count();
        int[] palette = ThemeManager.get().current().waypointPalette();
        return palette[Math.floorMod(count, palette.length)];
    }

    private int surfaceY(Minecraft minecraft, int blockX, int blockZ)
    {
        if (minecraft.level == null)
        {
            return minecraft.player == null ? 64 : minecraft.player.getBlockY();
        }
        return minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - 1;
    }

    private void toast(String message)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null)
        {
            minecraft.player.sendOverlayMessage(Component.literal(message));
        }
    }

    /** Opens the proof screen once when the proof property is set. */
    void openProofScreen()
    {
        if (proofOpened || maps == null || maps.worldKey() == null)
        {
            return;
        }
        String proof = MinimapProof.mode();
        if (proof.isBlank())
        {
            return;
        }
        proofOpened = true;
        MinimapProof.start(this);
    }
}
