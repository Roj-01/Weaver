package net.weaver.minimap.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.MinimapClient;
import net.weaver.minimap.config.MinimapConfig;
import net.weaver.minimap.input.KeybindSpec;
import net.weaver.minimap.input.Keybinds;
import net.weaver.minimap.render.IconAtlas;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Colors;
import net.weaver.minimap.ui.theme.ScissorGuard;
import net.weaver.minimap.ui.theme.Theme;
import net.weaver.minimap.ui.theme.ThemeId;
import net.weaver.minimap.ui.theme.ThemeManager;
import net.weaver.minimap.ui.widget.UiButton;
import net.weaver.minimap.ui.widget.UiColorPicker;
import net.weaver.minimap.ui.widget.UiDropdown;
import net.weaver.minimap.ui.widget.UiIconButton;
import net.weaver.minimap.ui.widget.UiKeybindButton;
import net.weaver.minimap.ui.widget.UiScrollView;
import net.weaver.minimap.ui.widget.UiSlider;
import net.weaver.minimap.ui.widget.UiTextField;
import net.weaver.minimap.ui.widget.UiToggle;
import net.weaver.minimap.ui.widget.UiTooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Tabbed settings application with a live HUD preview.
 *
 * <p>The left rail selects a section, the centre column lists settings with
 * descriptions and controls, and the right column renders the real HUD layer
 * at a preview size. Changes are applied immediately and saved by the client's
 * debounced writer; the screen's close path forces a final save.</p>
 */
public final class MinimapSettingsScreen extends Screen
{
    private static final int MARGIN = 16;
    private static final int HEADER = 72;
    private static final int TAB_RAIL_WIDTH = 208;
    private static final int PREVIEW_WIDTH = 300;
    private static final int ROW_HEIGHT = 46;
    private static final int SECTION_HEIGHT = 30;

    private static final String[] TABS = {
            "General", "Appearance", "Minimap", "Fullscreen Map", "Waypoints",
            "Cave Mode", "Entities", "Performance", "Compatibility", "Advanced"
    };
    private static final String[] TAB_SECTIONS = {
            "general", "appearance", "minimap", "fullscreen", "waypoints", "cave",
            "entities", "performance", "compatibility", "advanced"
    };
    private static final IconAtlas.Icon[] TAB_ICONS = {
            IconAtlas.Icon.SETTINGS, IconAtlas.Icon.STAR, IconAtlas.Icon.COMPASS,
            IconAtlas.Icon.LAYERS, IconAtlas.Icon.WAYPOINT, IconAtlas.Icon.CAVE,
            IconAtlas.Icon.ENTITY_HOSTILE, IconAtlas.Icon.SHIELD,
            IconAtlas.Icon.WORLD, IconAtlas.Icon.INFO
    };

    private final MinimapClient client;
    private final List<Row> rows = new ArrayList<>();
    private final List<RowControl> controls = new ArrayList<>();
    private int tabIndex;
    private int rowCursor;
    private boolean rendered;
    private boolean rebuildPending;
    private String searchText = "";
    private String appliedSearch = "";
    private UiTextField searchField;
    private UiScrollView scrollView;
    private int hoveredRow = -1;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

    /**
     * Creates the settings screen.
     *
     * @param client the mod client, never {@code null}
     */
    public MinimapSettingsScreen(MinimapClient client)
    {
        super(Component.literal("Minimap Settings"));
        this.client = client;
    }

    @Override
    protected void init()
    {
        super.init();
        rebuildRows();
        System.out.println("[weaver-minimap] SETTINGS ok");
    }

    @Override
    public void tick()
    {
        super.tick();
        if (rebuildPending)
        {
            rebuildPending = false;
            rebuildRows();
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick)
    {
        Layout layout = layout();
        Theme theme = ThemeManager.get().current();
        drawBackdrop(graphics, theme);
        drawHeader(graphics, layout, theme);
        drawTabRail(graphics, layout, theme, mouseX, mouseY);
        drawContent(graphics, layout, theme, mouseX, mouseY);
        drawPreview(graphics, layout, theme);
        syncControls();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawDropdownOverlays(graphics, mouseX, mouseY);
        if (hoveredRow >= 0 && hoveredRow < rows.size() && !client.isProofMode())
        {
            Row row = rows.get(hoveredRow);
            if (row.description != null && !row.description.isBlank())
            {
                UiTooltip.draw(graphics, font, row.description, mouseX, mouseY, width, height);
            }
        }
        if (!rendered)
        {
            rendered = true;
            System.out.println("[weaver-minimap] SETTINGS rendered");
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (routeDropdowns(event))
        {
            return true;
        }
        Layout layout = layout();
        if (layout.railVisible && event.button() == 0
                && inside(layout.railX, layout.railY + 44, layout.railWidth,
                TABS.length * 40, event.x(), event.y()))
        {
            int index = (int) ((event.y() - layout.railY - 44) / 40);
            if (index >= 0 && index < TABS.length && index != tabIndex)
            {
                tabIndex = index;
                appliedSearch = "";
                searchText = "";
                rebuildPending = true;
            }
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        for (RowControl control : controls)
        {
            if (control.widget instanceof UiDropdown dropdown && dropdown.isOpen()
                    && dropdown.mouseScrolled(mouseX, mouseY, scrollX, scrollY))
            {
                return true;
            }
        }
        boolean handled = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        syncControls();
        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (searchField != null && searchField.isFocused())
        {
            if (event.key() == 257 || event.key() == 335)
            {
                appliedSearch = searchField.value().trim().toLowerCase(Locale.ROOT);
                rebuildPending = true;
                return true;
            }
            if (event.key() == 256 && !searchField.value().isEmpty())
            {
                searchField.setValue("");
                searchText = "";
                appliedSearch = "";
                rebuildPending = true;
                return true;
            }
            return super.keyPressed(event);
        }
        if (event.key() == 256)
        {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose()
    {
        client.saveConfig();
        super.onClose();
    }

    private void rebuildRows()
    {
        clearWidgets();
        rows.clear();
        controls.clear();
        rowCursor = 0;
        Layout layout = layout();
        contentX = layout.contentX;
        contentY = layout.contentY;
        contentWidth = layout.contentWidth;
        contentHeight = layout.contentHeight;

        int searchWidth = Math.min(300, Math.max(160, width - 480));
        searchField = new UiTextField(width - 64 - searchWidth, 20, searchWidth, 26,
                searchText, value ->
                {
                    searchText = value == null ? "" : value;
                });
        searchField.setHint("Filter this section, then press Enter");
        searchField.setMaxLength(64);
        addRenderableWidget(searchField);

        UiIconButton close = new UiIconButton(width - 48, 19, 28, IconAtlas.Icon.CLOSE,
                this::onClose);
        close.setTooltip(Tooltip.create(Component.literal("Close (Esc)")));
        addRenderableWidget(close);

        if (layout.railVisible)
        {
            UiButton resetSection = new UiButton(layout.railX + 12,
                    layout.railY + layout.railHeight - 74, layout.railWidth - 24, 26,
                    Component.literal("Reset section"), this::resetSection);
            resetSection.setVariant(UiButton.Variant.SECONDARY);
            addRenderableWidget(resetSection);
            UiButton resetAll = new UiButton(layout.railX + 12,
                    layout.railY + layout.railHeight - 42, layout.railWidth - 24, 26,
                    Component.literal("Reset all"), this::resetAll);
            resetAll.setVariant(UiButton.Variant.DANGER);
            addRenderableWidget(resetAll);
        }

        scrollView = new UiScrollView(layout.contentX + 16, layout.contentY + 54,
                layout.contentWidth - 32, layout.contentHeight - 66, 0);
        addRenderableWidget(scrollView);

        buildTab(tabIndex);
        scrollView.setContentHeight(rowCursor + 16);
        syncControls();
    }

    private void buildTab(int tab)
    {
        String query = appliedSearch;
        switch (tab)
        {
            case 0 -> buildGeneral(query);
            case 1 -> buildAppearance(query);
            case 2 -> buildMinimap(query);
            case 3 -> buildFullscreen(query);
            case 4 -> buildWaypoints(query);
            case 5 -> buildCave(query);
            case 6 -> buildEntities(query);
            case 7 -> buildPerformance(query);
            case 8 -> buildCompatibility(query);
            case 9 -> buildAdvanced(query);
            default -> buildGeneral(query);
        }
    }

    private void buildGeneral(String query)
    {
        section("HUD", query);
        toggle("Show minimap", "Turn the HUD minimap on or off.", query,
                client.config()::enabled, client.config()::setEnabled);
        dropdown("Corner", "Anchor the HUD to a screen corner.", query,
                List.of("Top left", "Top right", "Bottom left", "Bottom right"),
                client.config().corner().ordinal(), index ->
                        client.config().setCorner(MinimapConfig.Corner.values()[index]));
        slider("Size", "HUD minimap size in pixels.", query, 64, 320,
                client.config()::size, value -> client.config().setSize((int) Math.round(value)),
                value -> (int) Math.round(value) + " px");
        slider("HUD scale", "Scale the HUD without changing the stored size.", query, 0.75, 1.5,
                client.config()::hudScale, client.config()::setHudScale,
                value -> String.format(Locale.ROOT, "%.2fx", value));
        slider("Zoom", "Blocks shown per pixel. Lower values zoom in.", query, 0.5, 8.0,
                client.config()::zoom, client.config()::setZoom,
                value -> Math.round(100.0D / value) + "%");
        slider("Offset X", "Horizontal distance from the anchored corner.", query, 0, 256,
                client.config()::offsetX, value -> client.config().setOffsetX((int) Math.round(value)),
                value -> (int) Math.round(value) + " px");
        slider("Offset Y", "Vertical distance from the anchored corner.", query, 0, 256,
                client.config()::offsetY, value -> client.config().setOffsetY((int) Math.round(value)),
                value -> (int) Math.round(value) + " px");
        section("Visibility", query);
        toggle("Hide in chat", "Hide the HUD while the chat screen is open.", query,
                client.config()::hideInChat, client.config()::setHideInChat);
        toggle("Hide on debug screen", "Hide the HUD while the F3 debug screen is visible.",
                query, client.config()::hideInDebug, client.config()::setHideInDebug);
        toggle("Soft shadow", "Draw the layered panel shadow.", query,
                client.config()::hudShadow, client.config()::setHudShadow);
        slider("Border opacity", "Opacity of the HUD border.", query, 0, 1,
                client.config()::hudBorderOpacity, client.config()::setHudBorderOpacity,
                value -> Math.round(value * 100.0) + "%");
    }

    private void buildAppearance(String query)
    {
        section("Theme", query);
        dropdown("Theme", "Select the global colour theme.", query,
                List.of("Dark", "Light", "Midnight", "Emerald", "Amber", "Custom"),
                ThemeId.byName(client.config().themeId()).ordinal(), index ->
                {
                    client.config().setThemeId(TAB_THEMES[index]);
                    client.applyTheme(true);
                });
        toggle("Custom accent", "Override the theme accent with a custom colour.", query,
                client.config()::customAccent, value ->
                {
                    client.config().setCustomAccent(value);
                    client.applyTheme(true);
                });
        color("Accent colour", "Accent used by selected controls and markers.", query,
                client.config()::accentColor, value ->
                {
                    client.config().setAccentColor(value);
                    client.applyTheme(true);
                });
        section("Markers", query);
        color("Waypoint colour", "Default colour for new waypoints.", query,
                client.config()::waypointColor, client.config()::setWaypointColor);
        color("Player colour", "Player arrow colour.", query,
                client.config()::playerColor, client.config()::setPlayerColor);
        color("Death colour", "Death waypoint and marker colour.", query,
                client.config()::deathColor, client.config()::setDeathColor);
        color("Grid colour", "Chunk and region grid colour on the map.", query,
                client.config()::gridColor, client.config()::setGridColor);
        slider("Background opacity", "Opacity of the map panel behind the HUD.", query, 0, 1,
                client.config()::backgroundOpacity, client.config()::setBackgroundOpacity,
                value -> Math.round(value * 100.0) + "%");
    }

    private void buildMinimap(String query)
    {
        section("Shape and rotation", query);
        dropdown("Shape", "Circular or rounded square HUD.", query,
                List.of("Circle", "Rounded square"), client.config().hudShape().ordinal(),
                index -> client.config().setHudShape(MinimapConfig.Shape.values()[index]));
        dropdown("Rotation", "North-up or player-up map orientation.", query,
                List.of("North up", "Player up"), client.config().rotationMode().ordinal(),
                index -> client.config().setRotationMode(
                        MinimapConfig.Rotation.values()[index]));
        section("Readout", query);
        toggle("Coordinates", "Show the player coordinates.", query,
                client.config()::showCoordinates, client.config()::setShowCoordinates);
        toggle("Compass", "Show the compass ring and cardinal letters.", query,
                client.config()::showCompass, client.config()::setShowCompass);
        toggle("Biome", "Show the biome name in the readout.", query,
                client.config()::showBiome, client.config()::setShowBiome);
        toggle("Dimension", "Show the dimension name in the readout.", query,
                client.config()::showDimension, client.config()::setShowDimension);
        toggle("Zoom percentage", "Show the zoom percentage in the readout.", query,
                client.config()::showZoom, client.config()::setShowZoom);
        toggle("Clock", "Show the in-game clock in the readout.", query,
                client.config()::showTime, client.config()::setShowTime);
        section("Overlays", query);
        toggle("Off-screen arrows", "Point toward waypoints outside the HUD.", query,
                client.config()::showOffscreenArrows, client.config()::setShowOffscreenArrows);
        toggle("Chunk grid", "Draw the chunk grid on the HUD and map.", query,
                client.config()::showChunkGrid, client.config()::setShowChunkGrid);
        toggle("Slime chunks", "Highlight slime chunks in single-player worlds.", query,
                client.config()::showSlimeChunks, client.config()::setShowSlimeChunks);
        toggle("World border", "Draw the world border.", query,
                client.config()::showWorldBorder, client.config()::setShowWorldBorder);
        toggle("Spawn point", "Mark the world spawn.", query,
                client.config()::showSpawn, client.config()::setShowSpawn);
    }

    private void buildFullscreen(String query)
    {
        section("Canvas", query);
        slider("Background opacity", "Opacity of the fullscreen map backdrop.", query, 0, 1,
                client.config()::mapBackgroundOpacity,
                client.config()::setMapBackgroundOpacity,
                value -> Math.round(value * 100.0) + "%");
        toggle("Smooth sampling", "Use linear filtering for region textures.", query,
                client.config()::mapSmoothing, client.config()::setMapSmoothing);
        toggle("Grid overlay", "Draw the chunk grid on the fullscreen map.", query,
                client.config()::mapShowGrid, client.config()::setMapShowGrid);
        slider("Pan speed", "Multiplier for keyboard and drag panning.", query, 0.25, 3.0,
                client.config()::mapPanSpeed, client.config()::setMapPanSpeed,
                value -> String.format(Locale.ROOT, "%.2fx", value));
        slider("Zoom sensitivity", "Multiplier for wheel zoom steps.", query, 0.25, 3.0,
                client.config()::mapZoomSensitivity,
                client.config()::setMapZoomSensitivity,
                value -> String.format(Locale.ROOT, "%.2fx", value));
        section("Layers", query);
        toggle("Players", "Show other players on the map.", query,
                client.config()::showMapPlayers, client.config()::setShowMapPlayers);
        toggle("Entities", "Show entity markers on the map.", query,
                client.config()::showMapEntities, client.config()::setShowMapEntities);
        toggle("Waypoints", "Show waypoint markers on the map.", query,
                client.config()::showMapWaypoints, client.config()::setShowMapWaypoints);
        toggle("Death markers", "Show death waypoints on the map.", query,
                client.config()::showMapDeaths, client.config()::setShowMapDeaths);
        toggle("Cave layer", "Allow the cave layer to render on the map.", query,
                client.config()::showMapCaveLayer, value ->
                {
                    client.config().setShowMapCaveLayer(value);
                    rebuildPending = true;
                });
    }

    private void buildWaypoints(String query)
    {
        section("Behaviour", query);
        toggle("Death waypoint", "Create a death waypoint automatically.", query,
                client.config()::deathWaypointEnabled, client.config()::setDeathWaypointEnabled);
        slider("Temporary lifetime", "Minutes before a temporary waypoint expires.", query,
                1, 4320, client.config()::temporaryWaypointMinutes,
                value -> client.config().setTemporaryWaypointMinutes((int) Math.round(value)),
                value -> (int) Math.round(value) + " min");
        slider("Label distance", "Only show waypoint labels within this distance.", query,
                0, 2048, client.config()::waypointLabelDistance,
                client.config()::setWaypointLabelDistance,
                value -> (int) Math.round(value) + " m");
        section("World beams", query);
        toggle("Waypoint beams", "Draw vertical beams at waypoints in the world.", query,
                client.config()::waypointBeamEnabled, client.config()::setWaypointBeamEnabled);
        slider("Beam distance", "Maximum distance for world beams.", query, 16, 512,
                client.config()::waypointBeamDistance, client.config()::setWaypointBeamDistance,
                value -> (int) Math.round(value) + " m");
        section("Data", query);
        button("Waypoint list", "Open the fullscreen map focused on waypoints.", query,
                "Open map", client::openWaypoints);
        button("Import / export", "Move waypoints between worlds with JSON.", query,
                "Open", client::openImportExport);
    }

    private void buildCave(String query)
    {
        section("Cave mode", query);
        toggle("Enable cave mode", "Unlock the cave layer and cave controls.", query,
                client.config()::caveEnabled, value ->
                {
                    client.config().setCaveEnabled(value);
                    rebuildPending = true;
                });
        toggle("Automatic Y", "Choose the best cave layer below the player.", query,
                client.config()::caveAuto, value ->
                {
                    client.config().setCaveAuto(value);
                    rebuildPending = true;
                });
        slider("Manual Y", "The cave slice Y when automatic mode is off.", query, -64, 320,
                client.config()::caveY, value -> client.config().setCaveY((int) Math.round(value)),
                value -> "Y " + (int) Math.round(value));
        slider("Cave transparency", "Opacity of the cave layer over the surface.", query,
                0.2, 1.0, client.config()::caveTransparency,
                client.config()::setCaveTransparency,
                value -> Math.round(value * 100.0) + "%");
        toggle("Surface overlay", "Keep the surface map visible under the cave layer.", query,
                client.config()::caveSurfaceOverlay, client.config()::setCaveSurfaceOverlay);
        slider("Overlay opacity", "Surface visibility when the overlay is on.", query, 0, 1,
                client.config()::caveSurfaceOpacity, client.config()::setCaveSurfaceOpacity,
                value -> Math.round(value * 100.0) + "%");
        section("Performance", query);
        slider("Cave scan budget", "Cave cells scanned per client tick.", query, 1, 16,
                client.config()::caveScanChunksPerTick,
                value -> client.config().setCaveScanChunksPerTick((int) Math.round(value)),
                value -> (int) Math.round(value) + " / tick");
        label("Cave palette", "Blue-grey caves, warm Nether, violet End. The slice is sampled "
                + "one block per column at the selected Y.", query);
    }

    private void buildEntities(String query)
    {
        section("Categories", query);
        toggle("Entities", "Master switch for entity markers.", query,
                client.config()::showEntities, client.config()::setShowEntities);
        toggle("Hostile", "Monsters and hostile mobs.", query,
                client.config()::showHostile, client.config()::setShowHostile);
        toggle("Passive", "Animals, villagers, and ambient mobs.", query,
                client.config()::showPassive, client.config()::setShowPassive);
        toggle("Items", "Dropped item entities.", query,
                client.config()::showItems, client.config()::setShowItems);
        toggle("Other", "Minecarts, boats, and everything else.", query,
                client.config()::showOtherEntities, client.config()::setShowOtherEntities);
        section("Limits", query);
        slider("HUD limit", "Maximum entity markers drawn on the HUD.", query, 0, 256,
                client.config()::entityLimitHud,
                value -> client.config().setEntityLimitHud((int) Math.round(value)),
                value -> (int) Math.round(value) + " markers");
        slider("Map limit", "Maximum entity markers drawn on the fullscreen map.", query,
                0, 1024, client.config()::entityLimitFullscreen,
                value -> client.config().setEntityLimitFullscreen((int) Math.round(value)),
                value -> (int) Math.round(value) + " markers");
        dropdown("Labels", "When to draw entity names.", query,
                List.of("None", "Hover", "Always"), client.config().entityLabelMode().ordinal(),
                index -> client.config().setEntityLabelMode(
                        MinimapConfig.EntityLabels.values()[index]));
    }

    private void buildPerformance(String query)
    {
        section("Preset", query);
        dropdown("Quality preset", "A coherent set of performance defaults.", query,
                List.of("Low", "Balanced", "High", "Ultra"),
                client.config().performancePreset().ordinal(), index ->
                {
                    client.config().setPerformancePreset(
                            MinimapConfig.PerformancePreset.values()[index]);
                    rebuildPending = true;
                });
        section("Scanning", query);
        slider("Surface scan budget", "Chunks scanned per client tick.", query, 1, 16,
                client.config()::scanChunksPerTick,
                value -> client.config().setScanChunksPerTick((int) Math.round(value)),
                value -> (int) Math.round(value) + " chunks");
        slider("Cave scan budget", "Cave cells scanned per client tick.", query, 1, 16,
                client.config()::caveScanChunksPerTick,
                value -> client.config().setCaveScanChunksPerTick((int) Math.round(value)),
                value -> (int) Math.round(value) + " chunks");
        slider("Save interval", "Ticks between dirty region writes.", query, 20, 6000,
                client.config()::saveIntervalTicks,
                value -> client.config().setSaveIntervalTicks((int) Math.round(value)),
                value -> (int) Math.round(value) + " ticks");
        section("Caches", query);
        slider("Region cache", "Surface regions kept in memory.", query, 16, 512,
                client.config()::regionCacheSize,
                value -> client.config().setRegionCacheSize((int) Math.round(value)),
                value -> (int) Math.round(value) + " regions");
        slider("Cave cache", "Cave slices kept in memory.", query, 8, 256,
                client.config()::caveCacheSize,
                value -> client.config().setCaveCacheSize((int) Math.round(value)),
                value -> (int) Math.round(value) + " slices");
        section("Uploads", query);
        slider("Texture uploads", "Region texture uploads per frame.", query, 1, 8,
                client.config()::textureUploadsPerFrame,
                value -> client.config().setTextureUploadsPerFrame((int) Math.round(value)),
                value -> (int) Math.round(value) + " / frame");
        slider("Regions per frame", "Regions considered for rendering per frame.", query,
                32, 256, client.config()::maxRegionsPerFrame,
                value -> client.config().setMaxRegionsPerFrame((int) Math.round(value)),
                value -> (int) Math.round(value) + " regions");
        section("Safety", query);
        toggle("Safe mode", "Disable optional visual flourishes on weak hardware.", query,
                client.config()::safeMode, client.config()::setSafeMode);
        toggle("Low-memory guard", "Reduce caches when memory pressure is detected.", query,
                client.config()::disableOnLowMemory, client.config()::setDisableOnLowMemory);
    }

    private void buildCompatibility(String query)
    {
        section("Other mods", query);
        toggle("Respect other HUDs", "Avoid drawing over detected minimap-style HUDs.",
                query, client.config()::respectOtherHud, client.config()::setRespectOtherHud);
        toggle("Hide with debug", "Hide when the debug overlay is open.", query,
                client.config()::hideWithDebugScreen,
                client.config()::setHideWithDebugScreen);
        section("Stability", query);
        toggle("Safe mode", "Disable optional visual flourishes.", query,
                client.config()::safeMode, client.config()::setSafeMode);
        toggle("Low-memory guard", "Reduce caches under memory pressure.", query,
                client.config()::disableOnLowMemory, client.config()::setDisableOnLowMemory);
        label("Client-only", "The mod never adds a Mixin, never sends packet data, and "
                + "never touches server state. Waypoints are stored locally per world and "
                + "dimension.", query);
        label("Texture path", "Runtime textures use the loader's DynamicTextureHandle with "
                + "linear filtering and partial upload support.", query);
    }

    private void buildAdvanced(String query)
    {
        section("Keybinds", query);
        int conflicts = keybindConflicts();
        label("Conflicts", conflicts == 0 ? "No conflicting keybinds detected."
                : conflicts + " conflicting keybind(s) detected.", query);
        for (KeybindSpec spec : Keybinds.all())
        {
            keybind(spec, query);
        }
        section("Configuration", query);
        button("Export config", "Copy the full JSON configuration to the clipboard.", query,
                "Copy JSON", this::exportConfig);
        button("Import config", "Apply a JSON configuration from the clipboard.", query,
                "Import JSON", this::importConfig);
        button("Reset all settings", "Restore every setting to its default.", query,
                "Reset all", this::resetAll);
        button("Waypoint import / export", "Open the waypoint data transfer screen.", query,
                "Open", client::openImportExport);
        section("About", query);
        label("Version", "Weaver Minimap 2.0.0  \u00B7  Minecraft 26.3  \u00B7  "
                + "Loader API 2.2", query);
        label("Config file", client.config().file().toAbsolutePath().toString(), query);
    }

    private void section(String title, String query)
    {
        if (!query.isEmpty())
        {
            return;
        }
        rows.add(new Row(title, "", rowCursor, SECTION_HEIGHT, true));
        rowCursor += SECTION_HEIGHT;
    }

    private void label(String title, String description, String query)
    {
        if (!matches(query, title, description))
        {
            return;
        }
        rows.add(new Row(title, description, rowCursor, 34, false));
        rowCursor += 34;
    }

    private void toggle(String title, String description, String query, BooleanSupplier getter,
                        Consumer<Boolean> setter)
    {
        toggle(title, description, query, getter, setter, false);
    }

    private void toggle(String title, String description, String query, BooleanSupplier getter,
                        Consumer<Boolean> setter, boolean rebuild)
    {
        if (!matches(query, title, description))
        {
            return;
        }
        int rowY = rowCursor;
        rowCursor += ROW_HEIGHT;
        UiToggle widget = new UiToggle(controlX(38), 0, 38, 18, getter.getAsBoolean(), value ->
        {
            setter.accept(value);
            client.saveConfigSoon();
            if (rebuild)
            {
                rebuildPending = true;
            }
        });
        addRenderableWidget(widget);
        rows.add(new Row(title, description, rowY, ROW_HEIGHT, false));
        controls.add(new RowControl(rowY, ROW_HEIGHT, widget));
    }

    private void slider(String title, String description, String query, double min, double max,
                        DoubleSupplier getter, DoubleConsumer setter,
                        java.util.function.Function<Double, String> label)
    {
        if (!matches(query, title, description))
        {
            return;
        }
        int rowY = rowCursor;
        rowCursor += ROW_HEIGHT;
        UiSlider widget = new UiSlider(controlX(200), 0, 200, 18, min, max, getter.getAsDouble(),
                value ->
                {
                    setter.accept(value);
                    client.saveConfigSoon();
                }, label);
        addRenderableWidget(widget);
        rows.add(new Row(title, description, rowY, ROW_HEIGHT, false));
        controls.add(new RowControl(rowY, ROW_HEIGHT, widget));
    }

    private void dropdown(String title, String description, String query, List<String> options,
                          int selected, java.util.function.IntConsumer onChange)
    {
        if (!matches(query, title, description))
        {
            return;
        }
        int rowY = rowCursor;
        rowCursor += ROW_HEIGHT;
        UiDropdown widget = new UiDropdown(controlX(200), 0, 200, 20, options, selected,
                index ->
                {
                    onChange.accept(index);
                    client.saveConfigSoon();
                });
        addRenderableWidget(widget);
        rows.add(new Row(title, description, rowY, ROW_HEIGHT, false));
        controls.add(new RowControl(rowY, ROW_HEIGHT, widget));
    }

    private void color(String title, String description, String query,
                       java.util.function.IntSupplier getter,
                       java.util.function.IntConsumer setter)
    {
        if (!matches(query, title, description))
        {
            return;
        }
        int rowY = rowCursor;
        rowCursor += ROW_HEIGHT;
        UiColorPicker widget = new UiColorPicker(controlX(200), 0, 200, 40, getter.getAsInt(),
                value ->
                {
                    setter.accept(value);
                    client.saveConfigSoon();
                });
        addRenderableWidget(widget);
        rows.add(new Row(title, description, rowY, ROW_HEIGHT, false));
        controls.add(new RowControl(rowY, ROW_HEIGHT, widget));
    }

    private void button(String title, String description, String query, String label,
                        Runnable action)
    {
        if (!matches(query, title, description))
        {
            return;
        }
        int rowY = rowCursor;
        rowCursor += ROW_HEIGHT;
        UiButton widget = new UiButton(controlX(160), 0, 160, 24, Component.literal(label),
                action);
        widget.setVariant(UiButton.Variant.SECONDARY);
        addRenderableWidget(widget);
        rows.add(new Row(title, description, rowY, ROW_HEIGHT, false));
        controls.add(new RowControl(rowY, ROW_HEIGHT, widget));
    }

    private void keybind(KeybindSpec spec, String query)
    {
        String title = keybindTitle(spec.id());
        String description = "Click, then press a key to rebind.";
        if (!matches(query, title, description))
        {
            return;
        }
        int rowY = rowCursor;
        rowCursor += ROW_HEIGHT;
        KeyMapping mapping = KeyMapping.get(spec.translationKey());
        String keyName = mapping == null ? "Unregistered"
                : mapping.getTranslatedKeyMessage().getString();
        UiKeybindButton widget = new UiKeybindButton(controlX(160), 0, 160, 20, keyName,
                null);
        widget.setOnCapture(keyCode ->
        {
            KeyMapping live = KeyMapping.get(spec.translationKey());
            if (live != null)
            {
                live.setKey(InputConstants.Type.KEYBOARD.getOrCreate(keyCode));
                widget.setKeyName(live.getTranslatedKeyMessage().getString());
                saveOptions();
            }
        });
        addRenderableWidget(widget);
        rows.add(new Row(title, description, rowY, ROW_HEIGHT, false));
        controls.add(new RowControl(rowY, ROW_HEIGHT, widget));
    }

    private void saveOptions()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null)
        {
            try
            {
                minecraft.options.save();
            }
            catch (RuntimeException ignored)
            {
            }
        }
    }

    private void resetSection()
    {
        client.config().resetSection(TAB_SECTIONS[tabIndex]);
        client.applyTheme(true);
        client.saveConfig();
        rebuildPending = true;
    }

    private void resetAll()
    {
        client.config().resetAll();
        client.applyTheme(true);
        client.saveConfig();
        rebuildPending = true;
    }

    private void exportConfig()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.keyboardHandler != null)
        {
            minecraft.keyboardHandler.setClipboard(client.config().exportJson());
        }
    }

    private void importConfig()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.keyboardHandler == null)
        {
            return;
        }
        String json = minecraft.keyboardHandler.getClipboard();
        if (json == null || json.isBlank())
        {
            return;
        }
        try
        {
            client.config().importJson(json);
            client.applyTheme(true);
            client.saveConfig();
            rebuildPending = true;
        }
        catch (RuntimeException ignored)
        {
        }
    }

    private int keybindConflicts()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null)
        {
            return 0;
        }
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (KeyMapping mapping : minecraft.options.keyMappings)
        {
            if (mapping == null || mapping.isUnbound())
            {
                continue;
            }
            counts.merge(mapping.saveString(), 1, Integer::sum);
        }
        int conflicts = 0;
        for (int count : counts.values())
        {
            if (count > 1)
            {
                conflicts += count - 1;
            }
        }
        return conflicts;
    }

    private void drawBackdrop(GuiGraphicsExtractor graphics, Theme theme)
    {
        graphics.fillGradient(0, 0, width, height,
                Colors.withAlpha(theme.background(), 0xF4),
                Colors.withAlpha(Colors.darken(theme.background(), 0.25D), 0xFA));
        UiDraw.circle(graphics, width / 6, height / 5, Math.max(120, width / 6),
                Colors.withAlpha(theme.accent(), 0x0C));
    }

    private void drawHeader(GuiGraphicsExtractor graphics, Layout layout, Theme theme)
    {
        graphics.fill(0, 0, width, HEADER, Colors.withAlpha(theme.surface(), 0xE8));
        UiDraw.dividerH(graphics, 0, HEADER - 1, width, Colors.withAlpha(theme.border(), 0x80));
        UiDraw.roundRect(graphics, 20, 20, 24, 24, 7, theme.accent());
        IconAtlas.draw(graphics, IconAtlas.Icon.SETTINGS, 24, 24, 16,
                theme.accentContrast());
        graphics.text(font, "Minimap Settings", 54, 18, theme.text(), false);
        String subtitle = "10 sections  \u00B7  live preview  \u00B7  saved automatically";
        graphics.text(font, subtitle, 54, 34, theme.textFaint(), false);
    }

    private void drawTabRail(GuiGraphicsExtractor graphics, Layout layout, Theme theme,
                             int mouseX, int mouseY)
    {
        if (!layout.railVisible)
        {
            return;
        }
        UiDraw.roundRect(graphics, layout.railX, layout.railY, layout.railWidth,
                layout.railHeight, 16, Colors.withAlpha(theme.surface(), 0xE8));
        UiDraw.roundRectBorder(graphics, layout.railX, layout.railY, layout.railWidth,
                layout.railHeight, 16, 1, theme.border());
        graphics.text(font, "SECTIONS", layout.railX + 16, layout.railY + 16,
                theme.textFaint(), false);
        for (int index = 0; index < TABS.length; index++)
        {
            int y = layout.railY + 44 + index * 40;
            boolean selected = index == tabIndex;
            boolean hover = inside(layout.railX + 8, y, layout.railWidth - 16, 34,
                    mouseX, mouseY);
            if (selected)
            {
                UiDraw.roundRect(graphics, layout.railX + 8, y, layout.railWidth - 16, 34, 9,
                        Colors.withAlpha(theme.accent(), 0x26));
                UiDraw.roundRect(graphics, layout.railX + 8, y + 7, 3, 20, 2, theme.accent());
            }
            else if (hover)
            {
                UiDraw.roundRect(graphics, layout.railX + 8, y, layout.railWidth - 16, 34, 9,
                        Colors.withAlpha(theme.text(), 0x10));
            }
            IconAtlas.draw(graphics, TAB_ICONS[index], layout.railX + 20, y + 10, 13,
                    selected ? theme.accent() : theme.textMuted());
            graphics.text(font, TABS[index], layout.railX + 42, y + 11,
                    selected ? theme.text() : theme.textMuted(), false);
        }
    }

    private void drawContent(GuiGraphicsExtractor graphics, Layout layout, Theme theme,
                             int mouseX, int mouseY)
    {
        UiDraw.roundRect(graphics, layout.contentX, layout.contentY, layout.contentWidth,
                layout.contentHeight, 16, Colors.withAlpha(theme.surface(), 0xE8));
        UiDraw.roundRectBorder(graphics, layout.contentX, layout.contentY,
                layout.contentWidth, layout.contentHeight, 16, 1, theme.border());
        graphics.text(font, TABS[Math.max(0, Math.min(TABS.length - 1, tabIndex))],
                layout.contentX + 24, layout.contentY + 16, theme.text(), false);
        String description = tabDescription();
        UiDraw.textClipped(graphics, font, description, layout.contentX + 24,
                layout.contentY + 32, layout.contentWidth - 48, theme.textFaint(), false);
        UiDraw.dividerH(graphics, layout.contentX + 16, layout.contentY + 48,
                layout.contentWidth - 32, Colors.withAlpha(theme.border(), 0x80));
        if (scrollView == null)
        {
            return;
        }
        hoveredRow = -1;
        int labelX = layout.contentX + 24;
        int controlLeft = layout.contentX + layout.contentWidth - 24 - 200;
        int labelWidth = Math.max(80, controlLeft - labelX - 18);
        try (ScissorGuard guard = scrollView.scissor(graphics))
        {
            for (int index = 0; index < rows.size(); index++)
            {
                Row row = rows.get(index);
                int rowY = scrollView.contentY() + row.y;
                if (rowY + row.height < scrollView.getY() || rowY > scrollView.getBottom())
                {
                    continue;
                }
                boolean hover = inside(layout.contentX + 16, rowY,
                        layout.contentWidth - 32, row.height, mouseX, mouseY);
                if (row.section)
                {
                    graphics.text(font, row.label, labelX, rowY + 10, theme.accent(), false);
                    UiDraw.dividerH(graphics, labelX, rowY + 25,
                            Math.max(24, layout.contentWidth - 48),
                            Colors.withAlpha(theme.border(), 0x50));
                    continue;
                }
                if (hover)
                {
                    hoveredRow = index;
                    UiDraw.roundRect(graphics, layout.contentX + 14, rowY + 2,
                            layout.contentWidth - 28, row.height - 4, 8,
                            Colors.withAlpha(theme.text(), 0x08));
                }
                UiDraw.textClipped(graphics, font, row.label, labelX, rowY + 9, labelWidth,
                        theme.text(), false);
                UiDraw.textClipped(graphics, font, row.description, labelX, rowY + 24,
                        labelWidth, theme.textFaint(), false);
            }
        }
    }

    private void drawPreview(GuiGraphicsExtractor graphics, Layout layout, Theme theme)
    {
        if (!layout.previewVisible)
        {
            return;
        }
        UiDraw.shadow(graphics, layout.previewX, layout.previewY, layout.previewWidth,
                layout.previewHeight, 16, theme.shadow(), 3);
        UiDraw.roundRect(graphics, layout.previewX, layout.previewY, layout.previewWidth,
                layout.previewHeight, 16, Colors.withAlpha(theme.surface(), 0xF2));
        UiDraw.roundRectBorder(graphics, layout.previewX, layout.previewY,
                layout.previewWidth, layout.previewHeight, 16, 1, theme.border());
        graphics.text(font, "LIVE PREVIEW", layout.previewX + 20, layout.previewY + 16,
                theme.textFaint(), false);
        int previewSize = Math.min(220, layout.previewWidth - 40);
        int previewX = layout.previewX + (layout.previewWidth - previewSize) / 2;
        int previewY = layout.previewY + 44;
        client.hudLayer().renderPreview(graphics, previewX, previewY, previewSize);
        int swatchY = previewY + previewSize + 52;
        graphics.text(font, "Theme palette", layout.previewX + 20, swatchY,
                theme.textMuted(), false);
        int[] palette = theme.waypointPalette();
        for (int index = 0; index < palette.length; index++)
        {
            int x = layout.previewX + 20 + (index % 6) * 42;
            int y = swatchY + 18 + (index / 6) * 30;
            UiDraw.circle(graphics, x + 8, y + 8, 8, palette[index]);
            UiDraw.ring(graphics, x + 8, y + 8, 9, 8,
                    Colors.withAlpha(theme.border(), 0xC0));
        }
        int summaryY = swatchY + 84;
        graphics.text(font, "Shape", layout.previewX + 20, summaryY, theme.textFaint(), false);
        graphics.text(font, client.config().hudShape() == MinimapConfig.Shape.CIRCLE
                ? "Circle" : "Rounded", layout.previewX + 110, summaryY, theme.text(), false);
        graphics.text(font, "Size", layout.previewX + 20, summaryY + 18,
                theme.textFaint(), false);
        graphics.text(font, client.config().size() + " px", layout.previewX + 110,
                summaryY + 18, theme.text(), false);
        graphics.text(font, "Theme", layout.previewX + 20, summaryY + 36,
                theme.textFaint(), false);
        graphics.text(font, theme.id().displayName(), layout.previewX + 110,
                summaryY + 36, theme.text(), false);
        graphics.text(font, "Opacity", layout.previewX + 20, summaryY + 54,
                theme.textFaint(), false);
        graphics.text(font, Math.round(client.config().backgroundOpacity() * 100.0) + "%",
                layout.previewX + 110, summaryY + 54, theme.text(), false);
        UiDraw.textClipped(graphics, font, "Changes apply instantly.",
                layout.previewX + 20, layout.previewY + layout.previewHeight - 30,
                layout.previewWidth - 40, theme.textFaint(), false);
    }

    private boolean routeDropdowns(MouseButtonEvent event)
    {
        for (RowControl control : controls)
        {
            if (control.widget instanceof UiDropdown dropdown && dropdown.isOpen())
            {
                if (dropdown.mouseClicked(event, false))
                {
                    return true;
                }
                dropdown.close();
            }
        }
        return false;
    }

    private void drawDropdownOverlays(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        for (RowControl control : controls)
        {
            if (control.widget instanceof UiDropdown dropdown && dropdown.isOpen())
            {
                dropdown.drawOverlay(graphics, mouseX, mouseY);
            }
        }
    }

    private void syncControls()
    {
        if (scrollView == null)
        {
            return;
        }
        for (RowControl control : controls)
        {
            int y = scrollView.contentY() + control.rowY
                    + (control.rowHeight - control.widget.getHeight()) / 2;
            control.widget.setY(y);
            control.widget.visible = y >= scrollView.getY()
                    && y + control.widget.getHeight() <= scrollView.getBottom();
        }
    }

    private int controlX(int controlWidth)
    {
        return contentX + contentWidth - 24 - controlWidth;
    }

    private boolean matches(String query, String title, String description)
    {
        if (query == null || query.isEmpty())
        {
            return true;
        }
        return title.toLowerCase(Locale.ROOT).contains(query)
                || (description != null
                && description.toLowerCase(Locale.ROOT).contains(query));
    }

    private String keybindTitle(String id)
    {
        return switch (id)
        {
            case Keybinds.OPEN_MAP -> "Open fullscreen map";
            case Keybinds.OPEN_SETTINGS -> "Open settings";
            case Keybinds.ADD_WAYPOINT -> "Add waypoint";
            case Keybinds.TOGGLE_HUD -> "Toggle HUD";
            case Keybinds.ZOOM_IN -> "Zoom in";
            case Keybinds.ZOOM_OUT -> "Zoom out";
            case Keybinds.CYCLE_CAVE -> "Cycle cave mode";
            case Keybinds.TOGGLE_ENTITIES -> "Toggle entities";
            case Keybinds.TOGGLE_WAYPOINT_LABELS -> "Toggle waypoint labels";
            case Keybinds.HIDE_HUD_TEMP -> "Temporarily hide HUD";
            case Keybinds.OPEN_WAYPOINTS -> "Open waypoint list";
            default -> id;
        };
    }

    private String tabDescription()
    {
        return switch (tabIndex)
        {
            case 0 -> "HUD placement, visibility, and readout.";
            case 1 -> "Themes, accents, and marker colours.";
            case 2 -> "Shape, rotation, overlays, and the readout.";
            case 3 -> "Canvas, layers, and navigation feel.";
            case 4 -> "Death waypoints, temporary markers, beams, and data.";
            case 5 -> "Automatic and manual cave layers with surface blending.";
            case 6 -> "Entity categories, limits, and labels.";
            case 7 -> "Presets, scan budgets, caches, and upload limits.";
            case 8 -> "Other HUD mods, debug screens, and stability guards.";
            case 9 -> "Keybinds, configuration transfer, and resets.";
            default -> "";
        };
    }

    private Layout layout()
    {
        boolean previewVisible = width >= 1180;
        boolean railVisible = width >= 760;
        int railWidth = railVisible ? TAB_RAIL_WIDTH : 0;
        int railX = MARGIN;
        int railY = HEADER + MARGIN;
        int railHeight = height - railY - MARGIN;
        int previewWidth = previewVisible ? PREVIEW_WIDTH : 0;
        int previewX = width - MARGIN - previewWidth;
        int previewY = HEADER + MARGIN;
        int previewHeight = height - previewY - MARGIN;
        int contentX = railVisible ? railX + railWidth + MARGIN : MARGIN;
        int contentY = HEADER + MARGIN;
        int contentWidth = width - contentX - MARGIN
                - (previewVisible ? previewWidth + MARGIN : 0);
        int contentHeight = height - contentY - MARGIN;
        return new Layout(railVisible, railX, railY, railWidth, railHeight, contentX,
                contentY, Math.max(280, contentWidth), contentHeight, previewVisible,
                previewX, previewY, previewWidth, previewHeight);
    }

    private static boolean inside(int x, int y, int width, int height, double px, double py)
    {
        return width > 0 && height > 0 && px >= x && px < x + width && py >= y
                && py < y + height;
    }

    private record Row(String label, String description, int y, int height, boolean section)
    {
    }

    private record RowControl(int rowY, int rowHeight, AbstractWidget widget)
    {
    }

    private record Layout(boolean railVisible, int railX, int railY, int railWidth,
                          int railHeight, int contentX, int contentY, int contentWidth,
                          int contentHeight, boolean previewVisible, int previewX,
                          int previewY, int previewWidth, int previewHeight)
    {
    }

    private static final String[] TAB_THEMES = {
            "dark", "light", "midnight", "emerald", "amber", "custom"
    };
}
