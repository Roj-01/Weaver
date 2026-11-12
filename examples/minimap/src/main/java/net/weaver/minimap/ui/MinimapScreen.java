package net.weaver.minimap.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.border.WorldBorder;
import net.weaver.minimap.MinimapClient;
import net.weaver.minimap.api.HudAnchor;
import net.weaver.minimap.api.WaypointMarker;
import net.weaver.minimap.api.WaypointQuery;
import net.weaver.minimap.config.MinimapConfig;
import net.weaver.minimap.input.MinimapKeys;
import net.weaver.minimap.map.CaveState;
import net.weaver.minimap.map.MapManager;
import net.weaver.minimap.map.MapStats;
import net.weaver.minimap.map.RegionKey;
import net.weaver.minimap.render.IconAtlas;
import net.weaver.minimap.render.MapMarkerRenderer;
import net.weaver.minimap.render.MapView;
import net.weaver.minimap.render.RegionTextureCache;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Colors;
import net.weaver.minimap.ui.theme.ScissorGuard;
import net.weaver.minimap.ui.theme.Theme;
import net.weaver.minimap.ui.theme.ThemeManager;
import net.weaver.minimap.ui.widget.UiContextMenu;
import net.weaver.minimap.ui.widget.UiDropdown;
import net.weaver.minimap.ui.widget.UiIconButton;
import net.weaver.minimap.ui.widget.UiScrollView;
import net.weaver.minimap.ui.widget.UiSlider;
import net.weaver.minimap.ui.widget.UiTextField;
import net.weaver.minimap.ui.widget.UiToggle;
import net.weaver.minimap.ui.widget.UiTooltip;
import net.weaver.minimap.waypoint.Waypoint;
import net.weaver.minimap.waypoint.WaypointManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fullscreen map application.
 *
 * <p>The screen is deliberately laid out like a desktop map: a toolbar, a
 * searchable waypoint sidebar, a pannable map canvas, a layers/cave panel, and
 * a status bar. Waypoints can be added, edited, hidden, deleted, dragged, and
 * imported or exported without leaving the map.</p>
 */
public final class MinimapScreen extends Screen
{
    private static final int MARGIN = 12;
    private static final int TOOLBAR_HEIGHT = 56;
    private static final int STATUS_HEIGHT = 32;
    private static final int ROW_HEIGHT = 60;
    private static final double MIN_ZOOM = 0.25D;
    private static final double MAX_ZOOM = 16.0D;

    private final MinimapClient client;
    private final UiContextMenu contextMenu = new UiContextMenu();
    private final List<Waypoint> listSnapshot = new ArrayList<>();

    private double centerX;
    private double centerZ;
    private double zoom;
    private boolean initializedCenter;
    private boolean draggingMap;
    private boolean draggingWaypoint;
    private boolean rendered;
    private int frameCounter;
    private long lastFrameNanos;
    private double lastMouseX;
    private double lastMouseY;
    private UUID draggedWaypoint;
    private int draggedX;
    private int draggedZ;
    private int filterIndex;
    private int sortIndex;
    private int hoveredRow = -1;
    private Waypoint hoveredWaypoint;
    private boolean layersOpen = true;
    private boolean rebuildPending;
    private String searchQuery = "";

    private UiTextField searchField;
    private UiDropdown filterDropdown;
    private UiDropdown sortDropdown;
    private UiScrollView listScroll;
    private UiDropdown caveModeDropdown;
    private UiSlider caveYSlider;
    private UiSlider caveTransparencySlider;
    private UiToggle surfaceOverlayToggle;

    /**
     * Creates the screen.
     *
     * @param client the mod client, never {@code null}
     */
    public MinimapScreen(MinimapClient client)
    {
        super(Component.literal("Weaver Map"));
        this.client = client;
        this.zoom = Math.max(MIN_ZOOM, Math.min(1.0D, client.config().zoom()));
    }

    @Override
    protected void init()
    {
        super.init();
        if (!initializedCenter && minecraft != null && minecraft.player != null)
        {
            centerX = minecraft.player.getX();
            centerZ = minecraft.player.getZ();
            initializedCenter = true;
        }
        if (client.consumeWaypointFocus())
        {
            layersOpen = false;
        }
        if (client.maps() != null)
        {
            client.maps().setScanBoost(true);
        }
        rebuildUi();
        System.out.println("[weaver-minimap] SCREEN ok");
    }

    @Override
    public void tick()
    {
        super.tick();
        if (client.maps() != null)
        {
            client.maps().setScanBoost(true);
        }
        if (rebuildPending)
        {
            rebuildPending = false;
            rebuildUi();
        }
    }

    @Override
    public void removed()
    {
        if (client.maps() != null)
        {
            client.maps().setScanBoost(false);
        }
        client.saveConfig();
        super.removed();
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
        long frameStart = System.nanoTime();
        Layout layout = layout();
        Theme theme = ThemeManager.get().current();
        drawBackdrop(graphics, layout, theme);
        drawToolbar(graphics, layout, theme);
        drawSidebar(graphics, layout, theme, mouseX, mouseY);
        drawMap(graphics, layout, theme, mouseX, mouseY, partialTick);
        if (layout.layersWidth > 0)
        {
            drawLayers(graphics, layout, theme, mouseX, mouseY);
        }
        drawStatus(graphics, layout, theme, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawOverlays(graphics, mouseX, mouseY);
        if (hoveredWaypoint != null && !contextMenu.isOpen())
        {
            drawWaypointTooltip(graphics, mouseX, mouseY);
        }
        if (!rendered)
        {
            rendered = true;
            System.out.println("[weaver-minimap] SCREEN rendered");
        }
        lastFrameNanos = System.nanoTime() - frameStart;
        frameCounter++;
        if (frameCounter % 120 == 0)
        {
            System.out.println("[weaver-minimap] PERF screen_us="
                    + (lastFrameNanos / 1000L) + " fps=" + minecraft.getFps());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (contextMenu.isOpen())
        {
            if (contextMenu.mouseClicked(event, doubled))
            {
                return true;
            }
            contextMenu.close();
            return true;
        }
        if (routeOpenDropdowns(event))
        {
            return true;
        }
        Layout layout = layout();
        if (inside(layout.layersX, layout.layersY, layout.layersWidth, layout.layersHeight,
                event.x(), event.y()))
        {
            return super.mouseClicked(event, doubled);
        }
        if (inside(layout.sidebarX, layout.sidebarY, layout.sidebarWidth,
                layout.sidebarHeight, event.x(), event.y()))
        {
            if (event.button() == MinimapKeys.MOUSE_LEFT && handleSidebarClick(event, doubled))
            {
                return true;
            }
            return super.mouseClicked(event, doubled);
        }
        if (inside(layout.mapX, layout.mapY, layout.mapWidth, layout.mapHeight, event.x(),
                event.y()))
        {
            if (event.button() == MinimapKeys.MOUSE_RIGHT)
            {
                openContextMenu(event.x(), event.y());
                return true;
            }
            if (event.button() == MinimapKeys.MOUSE_LEFT)
            {
                if (doubled)
                {
                    zoomAt(event.x(), event.y(), 0.75D);
                    return true;
                }
                Waypoint marker = waypointAt(event.x(), event.y());
                if (marker != null)
                {
                    draggingWaypoint = true;
                    draggedWaypoint = marker.id();
                    draggedX = marker.x();
                    draggedZ = marker.z();
                }
                else
                {
                    draggingMap = true;
                }
                lastMouseX = event.x();
                lastMouseY = event.y();
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        if (draggingWaypoint)
        {
            draggingWaypoint = false;
            if (draggedWaypoint != null)
            {
                client.moveWaypoint(draggedWaypoint, draggedX,
                        surfaceY(minecraft, draggedX, draggedZ), draggedZ);
            }
            draggedWaypoint = null;
            return true;
        }
        if (draggingMap)
        {
            draggingMap = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
    {
        if (draggingWaypoint)
        {
            MapView view = mapView(layout());
            draggedX = (int) Math.floor(view.worldX(event.x(), event.y()));
            draggedZ = (int) Math.floor(view.worldZ(event.x(), event.y()));
            return true;
        }
        if (draggingMap)
        {
            centerX -= dragX * zoom;
            centerZ -= dragY * zoom;
            lastMouseX = event.x();
            lastMouseY = event.y();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        Layout layout = layout();
        if (inside(layout.mapX, layout.mapY, layout.mapWidth, layout.mapHeight, mouseX, mouseY)
                && scrollY != 0.0D)
        {
            zoomAt(mouseX, mouseY, scrollY > 0.0D ? 0.82D : 1.22D);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (searchField != null && searchField.isFocused())
        {
            return super.keyPressed(event);
        }
        if (event.key() == MinimapKeys.KEY_ESCAPE || event.key() == MinimapKeys.KEY_M)
        {
            onClose();
            return true;
        }
        if (event.key() == MinimapKeys.KEY_B)
        {
            client.addWaypointAtPlayer();
            return true;
        }
        if (event.key() == MinimapKeys.KEY_HOME)
        {
            centerOnPlayer();
            return true;
        }
        if (event.key() == MinimapKeys.KEY_LEFT)
        {
            centerX -= 64.0D * zoom;
            return true;
        }
        if (event.key() == MinimapKeys.KEY_RIGHT)
        {
            centerX += 64.0D * zoom;
            return true;
        }
        if (event.key() == MinimapKeys.KEY_UP)
        {
            centerZ -= 64.0D * zoom;
            return true;
        }
        if (event.key() == MinimapKeys.KEY_DOWN)
        {
            centerZ += 64.0D * zoom;
            return true;
        }
        if (event.key() == MinimapKeys.KEY_EQUAL || event.key() == MinimapKeys.KEY_RIGHT_BRACKET)
        {
            zoom = Math.max(MIN_ZOOM, zoom * 0.82D);
            return true;
        }
        if (event.key() == MinimapKeys.KEY_MINUS || event.key() == MinimapKeys.KEY_LEFT_BRACKET)
        {
            zoom = Math.min(MAX_ZOOM, zoom * 1.22D);
            return true;
        }
        if (event.key() == MinimapKeys.KEY_J)
        {
            client.cycleCave();
            rebuildUi();
            return true;
        }
        if (event.key() == MinimapKeys.KEY_K)
        {
            client.toggleEntities();
            return true;
        }
        if (event.key() == MinimapKeys.KEY_L)
        {
            client.toggleWaypointLabels();
            return true;
        }
        if (event.key() == MinimapKeys.KEY_H)
        {
            client.toggleHud();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose()
    {
        client.saveConfig();
        if (client.maps() != null)
        {
            client.maps().setScanBoost(false);
        }
        super.onClose();
    }

    private void rebuildUi()
    {
        clearWidgets();
        Layout layout = layout();
        addToolbarWidgets(layout);
        if (layout.sidebarWidth > 0)
        {
            addSidebarWidgets(layout);
        }
        if (layout.layersWidth > 0)
        {
            addLayersWidgets(layout);
        }
    }

    private void addToolbarWidgets(Layout layout)
    {
        int y = 14;
        int size = 28;
        int right = width - 16;
        addIconButton(right - size, y, size, IconAtlas.Icon.CLOSE, "Close (M)",
                this::onClose);
        addIconButton(right - size * 2 - 6, y, size, IconAtlas.Icon.SETTINGS,
                "Settings (O)", client::openSettings);
        addIconButton(right - size * 3 - 12, y, size, IconAtlas.Icon.LAYERS,
                "Layers", () ->
                {
                    layersOpen = !layersOpen;
                    rebuildPending = true;
                });
        addIconButton(right - size * 4 - 18, y, size, IconAtlas.Icon.TARGET,
                "Center on player (Home)", this::centerOnPlayer);
        addIconButton(right - size * 5 - 24, y, size, IconAtlas.Icon.PLUS,
                "Add waypoint (B)", client::addWaypointAtPlayer);
        addIconButton(right - size * 6 - 30, y, size, IconAtlas.Icon.SEARCH,
                "Search waypoints", this::focusSearch);
    }

    private void addIconButton(int x, int y, int size, IconAtlas.Icon icon, String tooltip,
                               Runnable action)
    {
        UiIconButton button = new UiIconButton(x, y, size, icon, action);
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        addRenderableWidget(button);
    }

    private void addSidebarWidgets(Layout layout)
    {
        int x = layout.sidebarX + 12;
        int width = layout.sidebarWidth - 24;
        searchField = new UiTextField(x, layout.sidebarY + 50, width, 26, searchQuery,
                value ->
                {
                    searchQuery = value == null ? "" : value.trim();
                });
        searchField.setHint("Search waypoints");
        searchField.setMaxLength(64);
        addRenderableWidget(searchField);

        List<String> groups = groupOptions();
        filterIndex = Math.min(filterIndex, Math.max(0, groups.size() - 1));
        filterDropdown = new UiDropdown(x, layout.sidebarY + 84, (width - 6) / 2, 22,
                groups, filterIndex, index ->
                {
                    filterIndex = index;
                });
        addRenderableWidget(filterDropdown);

        sortDropdown = new UiDropdown(x + (width + 6) / 2, layout.sidebarY + 84,
                (width - 6) / 2, 22, List.of("Name", "Distance", "Color"), sortIndex,
                index -> sortIndex = index);
        addRenderableWidget(sortDropdown);

        int listTop = layout.sidebarY + 116;
        int listHeight = Math.max(40, layout.sidebarHeight - 128);
        listScroll = new UiScrollView(layout.sidebarX + 8, listTop,
                layout.sidebarWidth - 16, listHeight, 0);
        addRenderableWidget(listScroll);
    }

    private void addLayersWidgets(Layout layout)
    {
        int x = layout.layersX + 16;
        int width = layout.layersWidth - 32;
        int y = layout.layersY + 44;
        List<LayerRow> rows = layerRows();
        for (LayerRow row : rows)
        {
            UiToggle toggle = new UiToggle(x + width - 42, y + 2, 38, 18, row.get(),
                    value ->
                    {
                        row.set(value);
                        client.saveConfigSoon();
                        if (row.cave())
                        {
                            rebuildPending = true;
                        }
                    });
            addRenderableWidget(toggle);
            row.widget = toggle;
            y += 28;
        }
        y += 10;
        List<String> modes = List.of("Off", "Auto", "Manual");
        int mode = !client.config().caveEnabled() ? 0 : client.config().caveAuto() ? 1 : 2;
        caveModeDropdown = new UiDropdown(x, y, width, 22, modes, mode, index ->
        {
            MinimapConfig config = client.config();
            config.setCaveEnabled(index != 0);
            config.setCaveAuto(index != 0 && index != 2);
            if (index == 2 && config.caveY() == -32)
            {
                config.setCaveY(minecraft.player == null ? -32
                        : minecraft.player.getBlockY() - 24);
            }
            client.saveConfigSoon();
            rebuildPending = true;
        });
        addRenderableWidget(caveModeDropdown);
        y += 30;
        if (client.config().caveEnabled() && !client.config().caveAuto())
        {
            caveYSlider = new UiSlider(x, y, width, 18, -64.0D, 320.0D,
                    client.config().caveY(), value ->
            {
                client.config().setCaveY((int) Math.round(value));
                client.saveConfigSoon();
            }, value -> "Y " + (int) Math.round(value));
            caveYSlider.setStep(1.0D);
            addRenderableWidget(caveYSlider);
            y += 26;
        }
        caveTransparencySlider = new UiSlider(x, y, width, 18, 0.2D, 1.0D,
                client.config().caveTransparency(), value ->
        {
            client.config().setCaveTransparency(value);
            client.saveConfigSoon();
        }, value -> Math.round(value * 100.0D) + "%");
        caveTransparencySlider.setStep(0.01D);
        addRenderableWidget(caveTransparencySlider);
        y += 26;
        surfaceOverlayToggle = new UiToggle(x + width - 42, y, 38, 18,
                client.config().caveSurfaceOverlay(), value ->
        {
            client.config().setCaveSurfaceOverlay(value);
            client.saveConfigSoon();
        });
        addRenderableWidget(surfaceOverlayToggle);
    }

    private List<LayerRow> layerRows()
    {
        MinimapConfig config = client.config();
        return List.of(
                new LayerRow("Cave layer", IconAtlas.Icon.CAVE, config::caveEnabled,
                        config::setCaveEnabled, true),
                new LayerRow("Waypoints", IconAtlas.Icon.WAYPOINT, config::showMapWaypoints,
                        config::setShowMapWaypoints),
                new LayerRow("Deaths", IconAtlas.Icon.DEATH, config::showMapDeaths,
                        config::setShowMapDeaths),
                new LayerRow("Players", IconAtlas.Icon.PLAYER, config::showMapPlayers,
                        config::setShowMapPlayers),
                new LayerRow("Entities", IconAtlas.Icon.ENTITY_HOSTILE,
                        config::showMapEntities, config::setShowMapEntities),
                new LayerRow("Chunk grid", IconAtlas.Icon.GRID, config::mapShowGrid,
                        config::setMapShowGrid),
                new LayerRow("Slime chunks", IconAtlas.Icon.CHECK, config::showSlimeChunks,
                        config::setShowSlimeChunks),
                new LayerRow("World border", IconAtlas.Icon.WORLD, config::showWorldBorder,
                        config::setShowWorldBorder),
                new LayerRow("Spawn point", IconAtlas.Icon.HOME, config::showSpawn,
                        config::setShowSpawn));
    }

    private void drawBackdrop(GuiGraphicsExtractor graphics, Layout layout, Theme theme)
    {
        graphics.fillGradient(0, 0, width, height,
                Colors.withAlpha(theme.background(), 0xF2),
                Colors.withAlpha(Colors.darken(theme.background(), 0.28D), 0xFA));
        UiDraw.circle(graphics, width / 5, height / 6, Math.max(120, width / 5),
                Colors.withAlpha(theme.accent(), 0x0C));
        UiDraw.circle(graphics, width - width / 6, height / 2, Math.max(140, width / 6),
                Colors.withAlpha(theme.accent(), 0x08));
    }

    private void drawToolbar(GuiGraphicsExtractor graphics, Layout layout, Theme theme)
    {
        graphics.fill(0, 0, width, TOOLBAR_HEIGHT, Colors.withAlpha(theme.surface(), 0xE8));
        UiDraw.dividerH(graphics, 0, TOOLBAR_HEIGHT - 1, width,
                Colors.withAlpha(theme.border(), 0x80));
        int brandX = 20;
        int brandY = 17;
        UiDraw.roundRect(graphics, brandX, brandY, 22, 22, 6, theme.accent());
        IconAtlas.draw(graphics, IconAtlas.Icon.COMPASS, brandX + 3, brandY + 3, 16,
                theme.accentContrast());
        graphics.text(font, "WEAVER", brandX + 32, brandY + 2, theme.text(), false);
        graphics.text(font, "MAP", brandX + 32 + font.width("WEAVER") + 4, brandY + 2,
                theme.accent(), false);
        String subtitle = subtitle();
        graphics.text(font, subtitle, brandX + 32, brandY + 14, theme.textMuted(), false);
        if (layout.sidebarWidth > 0 && searchField != null)
        {
            String hint = "M  map   N  waypoints   O  settings";
            int hintWidth = font.width(hint);
            graphics.text(font, hint, (width - hintWidth) / 2, 22, theme.textFaint(), false);
        }
    }

    private void drawSidebar(GuiGraphicsExtractor graphics, Layout layout, Theme theme,
                             int mouseX, int mouseY)
    {
        if (layout.sidebarWidth <= 0)
        {
            return;
        }
        UiDraw.shadow(graphics, layout.sidebarX, layout.sidebarY, layout.sidebarWidth,
                layout.sidebarHeight, 16, theme.shadow(), 3);
        UiDraw.roundRect(graphics, layout.sidebarX, layout.sidebarY, layout.sidebarWidth,
                layout.sidebarHeight, 16, Colors.withAlpha(theme.surface(), 0xF0));
        UiDraw.roundRectBorder(graphics, layout.sidebarX, layout.sidebarY,
                layout.sidebarWidth, layout.sidebarHeight, 16, 1, theme.border());
        int titleX = layout.sidebarX + 16;
        int titleY = layout.sidebarY + 14;
        UiDraw.roundRect(graphics, titleX, titleY + 2, 4, 14, 2, theme.accent());
        graphics.text(font, "Waypoints", titleX + 10, titleY, theme.text(), false);
        List<Waypoint> waypoints = filteredWaypoints();
        String count = Integer.toString(waypoints.size());
        int badgeWidth = font.width(count) + 12;
        UiDraw.pill(graphics, layout.sidebarX + layout.sidebarWidth - badgeWidth - 14,
                titleY - 1, badgeWidth, 14, Colors.withAlpha(theme.accent(), 0x33));
        graphics.text(font, count,
                layout.sidebarX + layout.sidebarWidth - badgeWidth - 8, titleY + 1,
                theme.accent(), false);

        if (listScroll == null)
        {
            return;
        }
        listScroll.setContentHeight(Math.max(listScroll.visibleHeight(),
                waypoints.size() * ROW_HEIGHT + 8));
        if (waypoints.isEmpty())
        {
            drawEmptyState(graphics, font, theme, layout);
            return;
        }
        listSnapshot.clear();
        listSnapshot.addAll(waypoints);
        int rowX = layout.sidebarX + 12;
        int rowWidth = layout.sidebarWidth - 28;
        hoveredRow = -1;
        Waypoint selected = null;
        try (ScissorGuard guard = listScroll.scissor(graphics))
        {
            for (int index = 0; index < waypoints.size(); index++)
            {
                Waypoint waypoint = waypoints.get(index);
                int rowY = listScroll.contentY() + index * ROW_HEIGHT;
                if (rowY + ROW_HEIGHT < listScroll.getY() || rowY > listScroll.getBottom())
                {
                    continue;
                }
                boolean hover = inside(rowX, rowY, rowWidth, ROW_HEIGHT - 6, mouseX, mouseY);
                if (hover)
                {
                    hoveredRow = index;
                    hoveredWaypoint = waypoint;
                    if (draggingWaypoint && waypoint.id().equals(draggedWaypoint))
                    {
                        selected = waypoint;
                    }
                }
                int background = hover ? Colors.withAlpha(theme.surfaceElevated(), 0xE8)
                        : Colors.withAlpha(theme.surfaceAlt(), 0x66);
                UiDraw.roundRect(graphics, rowX, rowY, rowWidth, ROW_HEIGHT - 6, 10,
                        background);
                if (hover)
                {
                    UiDraw.roundRectBorder(graphics, rowX, rowY, rowWidth, ROW_HEIGHT - 6,
                            10, 1, Colors.withAlpha(theme.accent(), 0x70));
                }
                int iconX = rowX + 14;
                int iconY = rowY + 14;
                int color = waypoint.death() ? theme.death() : opaque(waypoint.color());
                UiDraw.circle(graphics, iconX, iconY, 14, Colors.withAlpha(color, 0x33));
                UiDraw.circle(graphics, iconX, iconY, 11, color);
                IconAtlas.draw(graphics, MapMarkerRenderer.iconFor(
                        waypoint.death() ? "death" : waypoint.icon()), iconX - 6, iconY - 6,
                        12, Colors.contrastText(color));
                int textX = rowX + 36;
                int textWidth = rowWidth - 98;
                UiDraw.textClipped(graphics, font, waypoint.name(), textX, rowY + 9,
                        textWidth, theme.text(), false);
                UiDraw.textClipped(graphics, font,
                        waypoint.x() + " " + waypoint.y() + " " + waypoint.z(), textX,
                        rowY + 24, textWidth, theme.textMuted(), false);
                String group = waypoint.group().isBlank() ? "Ungrouped" : waypoint.group();
                UiDraw.textClipped(graphics, font, group, textX, rowY + 37, textWidth,
                        theme.textFaint(), false);
                double distance = minecraft.player == null ? 0.0D
                        : Math.hypot(waypoint.x() + 0.5D - minecraft.player.getX(),
                                waypoint.z() + 0.5D - minecraft.player.getZ());
                String distanceText = MapMarkerRenderer.distance(distance) + "m";
                UiDraw.textRight(graphics, font, distanceText, rowX + rowWidth - 12,
                        rowY + 9, theme.textFaint(), false);
                drawRowAction(graphics, theme,
                        waypoint.visible() ? IconAtlas.Icon.EYE : IconAtlas.Icon.EYE_OFF,
                        rowX + rowWidth - 58, rowY + 28, mouseX, mouseY);
                drawRowAction(graphics, theme, IconAtlas.Icon.TRASH,
                        rowX + rowWidth - 34, rowY + 28, mouseX, mouseY);
            }
        }
        if (selected != null)
        {
            hoveredWaypoint = selected;
        }
        if (hoveredRow < 0)
        {
            hoveredWaypoint = null;
        }
    }

    private void drawRowAction(GuiGraphicsExtractor graphics, Theme theme,
                               IconAtlas.Icon icon, int x, int y, int mouseX, int mouseY)
    {
        boolean hover = inside(x - 4, y - 4, 22, 22, mouseX, mouseY);
        if (hover)
        {
            UiDraw.roundRect(graphics, x - 4, y - 4, 22, 22, 6,
                    Colors.withAlpha(theme.surfaceElevated(), 0xD0));
        }
        IconAtlas.draw(graphics, icon, x, y, 12,
                hover ? theme.accent() : theme.textMuted());
    }

    private void drawEmptyState(GuiGraphicsExtractor graphics, Font font, Theme theme,
                                Layout layout)
    {
        int centerX = layout.sidebarX + layout.sidebarWidth / 2;
        int centerY = layout.sidebarY + layout.sidebarHeight / 2 - 20;
        UiDraw.circle(graphics, centerX, centerY - 12, 18,
                Colors.withAlpha(theme.accent(), 0x18));
        IconAtlas.draw(graphics, IconAtlas.Icon.WAYPOINT, centerX - 9, centerY - 21, 18,
                theme.accent());
        UiDraw.textCentered(graphics, font, "No waypoints yet", centerX, centerY + 14,
                theme.textMuted(), false);
        UiDraw.textCentered(graphics, font, "Press B to add one at your position", centerX,
                centerY + 28, theme.textFaint(), false);
    }

    private void drawMap(GuiGraphicsExtractor graphics, Layout layout, Theme theme,
                         int mouseX, int mouseY, float partialTick)
    {
        UiDraw.shadow(graphics, layout.mapPanelX, layout.mapPanelY, layout.mapPanelWidth,
                layout.mapPanelHeight, 18, theme.shadow(), 4);
        UiDraw.roundRect(graphics, layout.mapPanelX, layout.mapPanelY,
                layout.mapPanelWidth, layout.mapPanelHeight, 18,
                Colors.withAlpha(theme.surface(), 0xF0));
        UiDraw.roundRectBorder(graphics, layout.mapPanelX, layout.mapPanelY,
                layout.mapPanelWidth, layout.mapPanelHeight, 18, 1, theme.borderStrong());
        graphics.fill(layout.mapX, layout.mapY, layout.mapX + layout.mapWidth,
                layout.mapY + layout.mapHeight, Colors.withAlpha(theme.background(), 0xF0));
        int dot = Colors.withAlpha(theme.text(), 0x16);
        for (int dotY = layout.mapY + 16; dotY < layout.mapY + layout.mapHeight; dotY += 32)
        {
            for (int dotX = layout.mapX + 16; dotX < layout.mapX + layout.mapWidth;
                 dotX += 32)
            {
                graphics.fill(dotX, dotY, dotX + 1, dotY + 1, dot);
            }
        }
        MapManager maps = client.maps();
        if (maps == null || !maps.isActive() || maps.dimensionId() == null)
        {
            drawMapLoading(graphics, layout, theme, "Loading world");
            return;
        }
        MapView view = mapView(layout);
        RegionTextureCache textures = client.textures();
        MapStats stats = maps.stats();
        boolean cave = maps.caveEnabled() && client.config().showMapCaveLayer();
        int caveY = cave ? (client.config().caveAuto() ? maps.autoCaveY()
                : client.config().caveY()) : Integer.MIN_VALUE;
        boolean caveReady = cave && caveY != Integer.MIN_VALUE;
        textures.beginFrame(client.config().textureUploadsPerFrame());
        graphics.enableScissor(layout.mapX, layout.mapY, layout.mapX + layout.mapWidth,
                layout.mapY + layout.mapHeight);
        try
        {
            if (cave)
            {
                graphics.fill(layout.mapX, layout.mapY, layout.mapX + layout.mapWidth,
                        layout.mapY + layout.mapHeight,
                        Colors.withAlpha(theme.background(), 0xFF));
                if (client.config().caveSurfaceOverlay())
                {
                    drawSurfaceOverlay(graphics, maps, view, textures, layout,
                            client.config().caveSurfaceOpacity());
                }
                if (caveReady)
                {
                    drawCave(graphics, maps, view, textures, layout, caveY);
                }
            }
            else
            {
                drawSurface(graphics, maps, view, textures, layout);
            }
            drawGrid(graphics, view, layout, theme);
            drawMarkers(graphics, maps, view, layout, theme, mouseX, mouseY, partialTick);
            drawMapChrome(graphics, layout, theme, maps, cave, caveY, stats);
        }
        finally
        {
            graphics.disableScissor();
            textures.endFrame();
        }
        if (stats.scannedChunks() == 0L && stats.loadedRegions() == 0)
        {
            drawMapLoading(graphics, layout, theme, "Scanning terrain");
        }
    }

    private void drawSurface(GuiGraphicsExtractor graphics, MapManager maps, MapView view,
                             RegionTextureCache textures, Layout layout)
    {
        int size = view.regionScreenSize();
        int minRegionX = (int) Math.floor(view.minWorldX() / 256.0D);
        int maxRegionX = (int) Math.floor(view.maxWorldX() / 256.0D);
        int minRegionZ = (int) Math.floor(view.minWorldZ() / 256.0D);
        int maxRegionZ = (int) Math.floor(view.maxWorldZ() / 256.0D);
        for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++)
        {
            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++)
            {
                RegionKey key = new RegionKey(maps.dimensionId(), regionX, regionZ);
                double worldX = regionX * 256.0D;
                double worldZ = regionZ * 256.0D;
                int screenX = (int) Math.round(view.screenX(worldX, worldZ));
                int screenY = (int) Math.round(view.screenY(worldX, worldZ));
                if (screenX + size < layout.mapX || screenX > layout.mapX + layout.mapWidth
                        || screenY + size < layout.mapY
                        || screenY > layout.mapY + layout.mapHeight)
                {
                    continue;
                }
                textures.drawSurface(graphics, maps, key, screenX, screenY, size);
            }
        }
    }

    private void drawSurfaceOverlay(GuiGraphicsExtractor graphics, MapManager maps,
                                    MapView view, RegionTextureCache textures, Layout layout,
                                    double opacity)
    {
        int size = view.regionScreenSize();
        int minRegionX = (int) Math.floor(view.minWorldX() / 256.0D);
        int maxRegionX = (int) Math.floor(view.maxWorldX() / 256.0D);
        int minRegionZ = (int) Math.floor(view.minWorldZ() / 256.0D);
        int maxRegionZ = (int) Math.floor(view.maxWorldZ() / 256.0D);
        for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++)
        {
            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++)
            {
                RegionKey key = new RegionKey(maps.dimensionId(), regionX, regionZ);
                double worldX = regionX * 256.0D;
                double worldZ = regionZ * 256.0D;
                int screenX = (int) Math.round(view.screenX(worldX, worldZ));
                int screenY = (int) Math.round(view.screenY(worldX, worldZ));
                if (screenX + size < layout.mapX || screenX > layout.mapX + layout.mapWidth
                        || screenY + size < layout.mapY
                        || screenY > layout.mapY + layout.mapHeight)
                {
                    continue;
                }
                textures.drawSurfaceOverlay(graphics, maps, key, screenX, screenY, size,
                        opacity);
            }
        }
    }

    private void drawCave(GuiGraphicsExtractor graphics, MapManager maps, MapView view,
                          RegionTextureCache textures, Layout layout, int caveY)
    {
        int size = view.regionScreenSize();
        int minRegionX = (int) Math.floor(view.minWorldX() / 256.0D);
        int maxRegionX = (int) Math.floor(view.maxWorldX() / 256.0D);
        int minRegionZ = (int) Math.floor(view.minWorldZ() / 256.0D);
        int maxRegionZ = (int) Math.floor(view.maxWorldZ() / 256.0D);
        double alpha = client.config().caveTransparency();
        for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++)
        {
            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++)
            {
                RegionKey key = new RegionKey(maps.dimensionId(), regionX, regionZ);
                double worldX = regionX * 256.0D;
                double worldZ = regionZ * 256.0D;
                int screenX = (int) Math.round(view.screenX(worldX, worldZ));
                int screenY = (int) Math.round(view.screenY(worldX, worldZ));
                if (screenX + size < layout.mapX || screenX > layout.mapX + layout.mapWidth
                        || screenY + size < layout.mapY
                        || screenY > layout.mapY + layout.mapHeight)
                {
                    continue;
                }
                textures.drawCave(graphics, maps, key, caveY, screenX, screenY, size, alpha);
            }
        }
    }

    private void drawGrid(GuiGraphicsExtractor graphics, MapView view, Layout layout,
                          Theme theme)
    {
        MinimapConfig config = client.config();
        double pixelsPerChunk = view.screenLength(16.0D);
        if (config.mapShowGrid() && pixelsPerChunk >= 8.0D)
        {
            int minChunkX = (int) Math.floor(view.minWorldX() / 16.0D);
            int maxChunkX = (int) Math.floor(view.maxWorldX() / 16.0D);
            int minChunkZ = (int) Math.floor(view.minWorldZ() / 16.0D);
            int maxChunkZ = (int) Math.floor(view.maxWorldZ() / 16.0D);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
            {
                int x = (int) Math.round(view.screenX(chunkX * 16.0D, view.centerZ()));
                if (x < layout.mapX || x >= layout.mapX + layout.mapWidth)
                {
                    continue;
                }
                int color = chunkX % 16 == 0 ? Colors.withAlpha(theme.mapGrid(), 0x40)
                        : Colors.withAlpha(theme.mapGrid(), 0x14);
                graphics.fill(x, layout.mapY, x + 1, layout.mapY + layout.mapHeight, color);
            }
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
            {
                int y = (int) Math.round(view.screenY(view.centerX(), chunkZ * 16.0D));
                if (y < layout.mapY || y >= layout.mapY + layout.mapHeight)
                {
                    continue;
                }
                int color = chunkZ % 16 == 0 ? Colors.withAlpha(theme.mapGrid(), 0x40)
                        : Colors.withAlpha(theme.mapGrid(), 0x14);
                graphics.fill(layout.mapX, y, layout.mapX + layout.mapWidth, y + 1, color);
            }
        }
        drawSlimeChunks(graphics, view, layout, theme);
        drawWorldBorder(graphics, view, layout, theme);
        drawSpawn(graphics, view, layout, theme);
    }

    private void drawSlimeChunks(GuiGraphicsExtractor graphics, MapView view, Layout layout,
                                 Theme theme)
    {
        if (!client.config().showSlimeChunks() || minecraft == null
                || minecraft.level == null || !minecraft.hasSingleplayerServer())
        {
            return;
        }
        var server = minecraft.getSingleplayerServer();
        if (server == null)
        {
            return;
        }
        long seed = server.overworld().getSeed();
        int minChunkX = (int) Math.floor(view.minWorldX() / 16.0D);
        int maxChunkX = (int) Math.floor(view.maxWorldX() / 16.0D);
        int minChunkZ = (int) Math.floor(view.minWorldZ() / 16.0D);
        int maxChunkZ = (int) Math.floor(view.maxWorldZ() / 16.0D);
        int size = Math.max(1, (int) Math.round(view.screenLength(16.0D)));
        int color = Colors.withAlpha(theme.success(), 0x24);
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
        {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
            {
                if (!isSlimeChunk(seed, chunkX, chunkZ))
                {
                    continue;
                }
                int x = (int) Math.round(view.screenX(chunkX * 16.0D, view.centerZ()));
                int y = (int) Math.round(view.screenY(view.centerX(), chunkZ * 16.0D));
                graphics.fill(x, y, x + size, y + size, color);
            }
        }
    }

    private void drawWorldBorder(GuiGraphicsExtractor graphics, MapView view, Layout layout,
                                 Theme theme)
    {
        if (!client.config().showWorldBorder() || minecraft == null
                || minecraft.level == null)
        {
            return;
        }
        WorldBorder border = minecraft.level.getWorldBorder();
        int color = Colors.withAlpha(theme.danger(), 0xC0);
        int minX = (int) Math.round(view.screenX(border.getMinX(), view.centerZ()));
        int maxX = (int) Math.round(view.screenX(border.getMaxX(), view.centerZ()));
        int minZ = (int) Math.round(view.screenY(view.centerX(), border.getMinZ()));
        int maxZ = (int) Math.round(view.screenY(view.centerX(), border.getMaxZ()));
        if (maxX >= layout.mapX && minX <= layout.mapX + layout.mapWidth)
        {
            graphics.fill(minX, layout.mapY, minX + 1, layout.mapY + layout.mapHeight, color);
            graphics.fill(maxX, layout.mapY, maxX + 1, layout.mapY + layout.mapHeight, color);
        }
        if (maxZ >= layout.mapY && minZ <= layout.mapY + layout.mapHeight)
        {
            graphics.fill(layout.mapX, minZ, layout.mapX + layout.mapWidth, minZ + 1, color);
            graphics.fill(layout.mapX, maxZ, layout.mapX + layout.mapWidth, maxZ + 1, color);
        }
    }

    private void drawSpawn(GuiGraphicsExtractor graphics, MapView view, Layout layout,
                           Theme theme)
    {
        if (!client.config().showSpawn() || minecraft == null || minecraft.level == null)
        {
            return;
        }
        BlockPos spawn = minecraft.level.getRespawnData().pos();
        double x = view.screenX(spawn.getX() + 0.5D, spawn.getZ() + 0.5D);
        double y = view.screenY(spawn.getX() + 0.5D, spawn.getZ() + 0.5D);
        if (!view.contains(x, y, 6))
        {
            return;
        }
        UiDraw.circle(graphics, (int) Math.round(x), (int) Math.round(y), 9,
                Colors.withAlpha(theme.background(), 0xE0));
        IconAtlas.draw(graphics, IconAtlas.Icon.HOME, (int) Math.round(x) - 6,
                (int) Math.round(y) - 6, 12, theme.accent());
    }

    private void drawMarkers(GuiGraphicsExtractor graphics, MapManager maps, MapView view,
                             Layout layout, Theme theme, int mouseX, int mouseY,
                             float partialTick)
    {
        hoveredWaypoint = null;
        String dimension = maps.dimensionId();
        WaypointManager manager = client.waypoints();
        if (manager != null && client.config().showMapWaypoints())
        {
            for (Waypoint waypoint : manager.inDimension(dimension))
            {
                if (!waypoint.visible())
                {
                    continue;
                }
                if (waypoint.death() && (!client.config().deathWaypointEnabled()
                        || !client.config().showMapDeaths()))
                {
                    continue;
                }
                drawWaypointOnMap(graphics, view, theme, waypoint, mouseX, mouseY);
            }
        }
        if (client.config().showMapWaypoints())
        {
            for (WaypointMarker marker : client.api().resolveWaypoints(
                    new WaypointQuery(dimension, view.centerX(), view.centerZ(),
                            view.halfWorldWidth(), true)))
            {
                double x = view.screenX(marker.x() + 0.5D, marker.z() + 0.5D);
                double y = view.screenY(marker.x() + 0.5D, marker.z() + 0.5D);
                if (view.contains(x, y, 4))
                {
                    MapMarkerRenderer.drawWaypoint(graphics, theme, marker, x, y, 1.1D);
                }
            }
        }
        if (client.config().showMapPlayers() && minecraft.level != null)
        {
            for (AbstractClientPlayer other : minecraft.level.players())
            {
                if (other == minecraft.player)
                {
                    continue;
                }
                double x = view.screenX(other.getX(), other.getZ());
                double y = view.screenY(other.getX(), other.getZ());
                if (view.contains(x, y, 3))
                {
                    MapMarkerRenderer.drawEntity(graphics, "player", x, y, 1.2D, theme);
                }
            }
        }
        if (client.config().showMapEntities() && minecraft.level != null)
        {
            drawEntities(graphics, view, theme, mouseX, mouseY);
        }
        if (draggingWaypoint && draggedWaypoint != null)
        {
            int x = (int) Math.round(view.screenX(draggedX + 0.5D, draggedZ + 0.5D));
            int y = (int) Math.round(view.screenY(draggedX + 0.5D, draggedZ + 0.5D));
            UiDraw.circle(graphics, x, y, 10, Colors.withAlpha(theme.accent(), 0x66));
            IconAtlas.draw(graphics, IconAtlas.Icon.TARGET, x - 7, y - 7, 14, theme.accent());
        }
        if (minecraft.player != null)
        {
            double x = view.screenX(minecraft.player.getX(), minecraft.player.getZ());
            double y = view.screenY(minecraft.player.getX(), minecraft.player.getZ());
            MapMarkerRenderer.drawPlayerArrow(graphics, x, y,
                    minecraft.player.getYRot(partialTick) + 180.0D, theme.player(),
                    0xFF06121C, 1.2D);
        }
    }

    private void drawWaypointOnMap(GuiGraphicsExtractor graphics, MapView view, Theme theme,
                                   Waypoint waypoint, int mouseX, int mouseY)
    {
        double x = view.screenX(waypoint.x() + 0.5D, waypoint.z() + 0.5D);
        double y = view.screenY(waypoint.x() + 0.5D, waypoint.z() + 0.5D);
        if (!view.contains(x, y, 4))
        {
            return;
        }
        MapMarkerRenderer.drawWaypoint(graphics, theme, waypoint, x, y, 1.2D);
        if (Math.hypot(mouseX - x, mouseY - y) <= 12.0D)
        {
            hoveredWaypoint = waypoint;
        }
        double distance = minecraft.player == null ? 0.0D
                : Math.hypot(waypoint.x() + 0.5D - minecraft.player.getX(),
                        waypoint.z() + 0.5D - minecraft.player.getZ());
        if (client.config().showWaypointLabels()
                && distance <= client.config().waypointLabelDistance())
        {
            MapMarkerRenderer.drawLabel(graphics, font, theme, waypoint.name(), x + 12,
                    y - 5, 160, true);
        }
    }

    private void drawEntities(GuiGraphicsExtractor graphics, MapView view, Theme theme,
                              int mouseX, int mouseY)
    {
        int limit = Math.max(0, client.config().entityLimitFullscreen());
        int drawn = 0;
        for (Entity entity : minecraft.level.entitiesForRendering())
        {
            if (drawn >= limit || entity == minecraft.player
                    || entity instanceof AbstractClientPlayer)
            {
                continue;
            }
            String category = category(entity);
            if (!visibleCategory(category))
            {
                continue;
            }
            double x = view.screenX(entity.getX(), entity.getZ());
            double y = view.screenY(entity.getX(), entity.getZ());
            if (!view.contains(x, y, 3))
            {
                continue;
            }
            MapMarkerRenderer.drawEntity(graphics, category, x, y, 1.0D, theme);
            if (client.config().entityLabelMode() == MinimapConfig.EntityLabels.ALWAYS
                    || (client.config().entityLabelMode()
                    == MinimapConfig.EntityLabels.HOVER
                    && Math.hypot(mouseX - x, mouseY - y) < 10.0D))
            {
                String name = entity.getName().getString();
                MapMarkerRenderer.drawLabel(graphics, font, theme, name, x + 8, y - 6,
                        120, true);
            }
            drawn++;
        }
    }

    private void drawMapChrome(GuiGraphicsExtractor graphics, Layout layout, Theme theme,
                               MapManager maps, boolean cave, int caveY, MapStats stats)
    {
        int x = layout.mapX + 10;
        int y = layout.mapY + 10;
        String title = dimensionName(maps.dimensionId());
        UiDraw.textPlate(graphics, font, title, x, y, 220, theme.text(),
                Colors.withAlpha(theme.background(), 0xB8), true);
        if (cave)
        {
            String label = caveY == Integer.MIN_VALUE ? "Cave: scanning"
                    : "Cave Y " + caveY;
            UiDraw.badge(graphics, font, label, x, y + 22,
                    Colors.withAlpha(theme.accent(), 0xD0), theme.accentContrast());
        }
        String footer = "Regions " + stats.loadedRegions() + "  Chunks "
                + stats.scannedChunks() + "  Zoom " + Math.round(100.0D / mapView(layout).zoom())
                + "%";
        int footerWidth = font.width(footer);
        UiDraw.textPlate(graphics, font, footer,
                layout.mapX + layout.mapWidth - footerWidth - 18,
                layout.mapY + layout.mapHeight - 24, footerWidth + 12, theme.textFaint(),
                Colors.withAlpha(theme.background(), 0x8C), false);
    }

    private void drawMapLoading(GuiGraphicsExtractor graphics, Layout layout, Theme theme,
                                String label)
    {
        int centerX = layout.mapX + layout.mapWidth / 2;
        int centerY = layout.mapY + layout.mapHeight / 2;
        UiDraw.circle(graphics, centerX, centerY, 24, Colors.withAlpha(theme.accent(), 0x18));
        IconAtlas.draw(graphics, IconAtlas.Icon.LAYERS, centerX - 12, centerY - 12, 24,
                theme.accent());
        UiDraw.textCentered(graphics, font, label, centerX, centerY + 34,
                theme.textMuted(), false);
        int dots = (int) ((System.currentTimeMillis() / 250L) % 4L);
        StringBuilder spinner = new StringBuilder();
        for (int index = 0; index < 3; index++)
        {
            spinner.append(index < dots ? '\u2022' : '\u00B7');
        }
        UiDraw.textCentered(graphics, font, spinner.toString(), centerX, centerY + 48,
                theme.accent(), false);
    }

    private void drawLayers(GuiGraphicsExtractor graphics, Layout layout, Theme theme,
                            int mouseX, int mouseY)
    {
        UiDraw.shadow(graphics, layout.layersX, layout.layersY, layout.layersWidth,
                layout.layersHeight, 16, theme.shadow(), 3);
        UiDraw.roundRect(graphics, layout.layersX, layout.layersY, layout.layersWidth,
                layout.layersHeight, 16, Colors.withAlpha(theme.surface(), 0xF0));
        UiDraw.roundRectBorder(graphics, layout.layersX, layout.layersY, layout.layersWidth,
                layout.layersHeight, 16, 1, theme.border());
        int x = layout.layersX + 16;
        int y = layout.layersY + 14;
        UiDraw.roundRect(graphics, x, y + 2, 4, 14, 2, theme.accent());
        graphics.text(font, "Layers", x + 10, y, theme.text(), false);
        int rowY = layout.layersY + 44;
        for (LayerRow row : layerRows())
        {
            IconAtlas.draw(graphics, row.icon, x, rowY + 3, 12, theme.textMuted());
            graphics.text(font, row.label, x + 20, rowY + 4, theme.textMuted(), false);
            rowY += 28;
        }
        int sectionY = rowY + 10;
        UiDraw.dividerH(graphics, x, sectionY - 6, layout.layersWidth - 32,
                Colors.withAlpha(theme.border(), 0x80));
        graphics.text(font, "Cave mode", x, sectionY, theme.textMuted(), false);
        int labelY = sectionY + 30;
        if (client.config().caveEnabled() && !client.config().caveAuto())
        {
            graphics.text(font, "Manual Y", x, labelY, theme.textMuted(), false);
            labelY += 26;
        }
        graphics.text(font, "Transparency", x, labelY, theme.textMuted(), false);
        labelY += 26;
        graphics.text(font, "Surface overlay", x, labelY, theme.textMuted(), false);
    }

    private void drawStatus(GuiGraphicsExtractor graphics, Layout layout, Theme theme,
                            int mouseX, int mouseY)
    {
        int y = height - STATUS_HEIGHT;
        graphics.fill(0, y, width, height, Colors.withAlpha(theme.surface(), 0xE8));
        UiDraw.dividerH(graphics, 0, y, width, Colors.withAlpha(theme.border(), 0x80));
        MapView view = mapView(layout);
        boolean overMap = inside(layout.mapX, layout.mapY, layout.mapWidth, layout.mapHeight,
                mouseX, mouseY);
        String coordinates;
        if (overMap)
        {
            int blockX = (int) Math.floor(view.worldX(mouseX, mouseY));
            int blockZ = (int) Math.floor(view.worldZ(mouseX, mouseY));
            coordinates = "Cursor  " + blockX + "  " + blockZ;
        }
        else if (minecraft.player != null)
        {
            coordinates = "Player  " + minecraft.player.getBlockX() + "  "
                    + minecraft.player.getBlockY() + "  " + minecraft.player.getBlockZ();
        }
        else
        {
            coordinates = "Cursor  -";
        }
        graphics.text(font, coordinates, 14, y + 10, theme.textMuted(), false);
        String biome = "Unknown";
        String dimension = "-";
        if (minecraft.level != null && minecraft.player != null)
        {
            biome = pretty(minecraft.level.getBiome(minecraft.player.blockPosition())
                    .unwrapKey().map(key -> key.identifier().getPath()).orElse("Unknown"));
        }
        if (client.maps() != null && client.maps().dimensionId() != null)
        {
            dimension = dimensionName(client.maps().dimensionId());
        }
        String middle = biome + "  \u00B7  " + dimension;
        UiDraw.textCentered(graphics, font, middle, width / 2, y + 10, theme.textFaint(),
                false);
        WaypointManager manager = client.waypoints();
        int waypointCount = manager == null ? 0 : manager.count();
        String right = String.format(Locale.ROOT, "Zoom %.0f%%   Waypoints %d   FPS %d",
                100.0D / mapView(layout).zoom(), waypointCount,
                minecraft == null ? 0 : minecraft.getFps());
        UiDraw.textRight(graphics, font, right, width - 14, y + 10, theme.textFaint(),
                false);
    }

    private void drawOverlays(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        if (filterDropdown != null)
        {
            filterDropdown.drawOverlay(graphics, mouseX, mouseY);
        }
        if (sortDropdown != null)
        {
            sortDropdown.drawOverlay(graphics, mouseX, mouseY);
        }
        if (caveModeDropdown != null)
        {
            caveModeDropdown.drawOverlay(graphics, mouseX, mouseY);
        }
        contextMenu.drawOverlay(graphics, mouseX, mouseY);
    }

    private void drawWaypointTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        List<Component> lines = new ArrayList<>(4);
        lines.add(Component.literal(hoveredWaypoint.name()));
        lines.add(Component.literal("x " + hoveredWaypoint.x() + "  y "
                + hoveredWaypoint.y() + "  z " + hoveredWaypoint.z()));
        String group = hoveredWaypoint.group().isBlank() ? "Ungrouped"
                : hoveredWaypoint.group();
        lines.add(Component.literal(group + "  \u00B7  "
                + (hoveredWaypoint.temporary() ? "Temporary  \u00B7  " : "")
                + (hoveredWaypoint.death() ? "Death" : "Waypoint")));
        lines.add(Component.literal("Double-click to edit  \u00B7  right-click for actions"));
        UiTooltip.draw(graphics, font, lines, mouseX, mouseY, width, height);
    }

    private boolean routeOpenDropdowns(MouseButtonEvent event)
    {
        if (filterDropdown != null && filterDropdown.isOpen())
        {
            if (filterDropdown.mouseClicked(event, false))
            {
                return true;
            }
            filterDropdown.close();
        }
        if (sortDropdown != null && sortDropdown.isOpen())
        {
            if (sortDropdown.mouseClicked(event, false))
            {
                return true;
            }
            sortDropdown.close();
        }
        if (caveModeDropdown != null && caveModeDropdown.isOpen())
        {
            if (caveModeDropdown.mouseClicked(event, false))
            {
                return true;
            }
            caveModeDropdown.close();
        }
        return false;
    }

    private boolean handleSidebarClick(MouseButtonEvent event, boolean doubled)
    {
        if (listScroll == null || listSnapshot.isEmpty())
        {
            return false;
        }
        Layout layout = layout();
        int rowX = layout.sidebarX + 12;
        int rowWidth = layout.sidebarWidth - 28;
        int index = rowIndexAt(event.x(), event.y(), layout);
        if (index < 0 || index >= listSnapshot.size())
        {
            return false;
        }
        Waypoint waypoint = listSnapshot.get(index);
        int rowY = listScroll.contentY() + index * ROW_HEIGHT;
        int eyeX = rowX + rowWidth - 58;
        int trashX = rowX + rowWidth - 34;
        if (event.y() >= rowY + 24 && event.y() <= rowY + 46)
        {
            if (event.x() >= eyeX - 4 && event.x() <= eyeX + 18)
            {
                if (client.waypoints() != null)
                {
                    client.waypoints().toggleVisible(waypoint.id());
                    try
                    {
                        client.waypoints().save();
                    }
                    catch (Exception failure)
                    {
                        client.log("MINIMAP waypoint save failed: " + failure);
                    }
                }
                return true;
            }
            if (event.x() >= trashX - 4 && event.x() <= trashX + 18)
            {
                client.removeWaypoint(waypoint);
                return true;
            }
        }
        if (doubled)
        {
            centerX = waypoint.x() + 0.5D;
            centerZ = waypoint.z() + 0.5D;
            client.openWaypointEditor(waypoint, waypoint.x(), waypoint.y(), waypoint.z());
        }
        else
        {
            centerX = waypoint.x() + 0.5D;
            centerZ = waypoint.z() + 0.5D;
            zoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, 1.0D));
        }
        return true;
    }

    private int rowIndexAt(double mouseX, double mouseY, Layout layout)
    {
        if (listScroll == null)
        {
            return -1;
        }
        int rowX = layout.sidebarX + 12;
        int rowWidth = layout.sidebarWidth - 28;
        if (mouseX < rowX || mouseX > rowX + rowWidth || mouseY < listScroll.getY()
                || mouseY > listScroll.getBottom())
        {
            return -1;
        }
        int relative = (int) (mouseY - listScroll.contentY());
        if (relative < 0)
        {
            return -1;
        }
        int index = relative / ROW_HEIGHT;
        return index >= 0 && index < listSnapshot.size() ? index : -1;
    }

    private void openContextMenu(double mouseX, double mouseY)
    {
        MapView view = mapView(layout());
        int blockX = (int) Math.floor(view.worldX(mouseX, mouseY));
        int blockZ = (int) Math.floor(view.worldZ(mouseX, mouseY));
        int blockY = minecraft.level == null ? 64
                : minecraft.level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                        blockX, blockZ) - 1;
        contextMenu.clear();
        contextMenu.add("Add waypoint here", IconAtlas.Icon.PLUS,
                () -> client.openWaypointEditor(null, blockX, blockY, blockZ));
        contextMenu.add("Copy coordinates", IconAtlas.Icon.CHECK, () ->
        {
            if (minecraft != null && minecraft.keyboardHandler != null)
            {
                minecraft.keyboardHandler.setClipboard(blockX + " " + blockY + " " + blockZ);
                if (minecraft.player != null)
                {
                    minecraft.player.sendOverlayMessage(Component.literal(
                            "Coordinates copied"));
                }
            }
        });
        contextMenu.add("Set as death waypoint", IconAtlas.Icon.SKULL, () ->
        {
            if (client.waypoints() != null && client.maps() != null
                    && client.maps().dimensionId() != null)
            {
                client.waypoints().setDeathWaypoint(client.maps().dimensionId(), blockX,
                        blockY, blockZ, client.config().deathColor());
                try
                {
                    client.waypoints().save();
                }
                catch (Exception failure)
                {
                    client.log("MINIMAP waypoint save failed: " + failure);
                }
            }
        });
        contextMenu.add("Center on player", IconAtlas.Icon.TARGET, this::centerOnPlayer);
        contextMenu.add(client.config().showMapWaypoints() ? "Hide waypoints"
                : "Show waypoints", IconAtlas.Icon.EYE, () ->
        {
            client.config().setShowMapWaypoints(!client.config().showMapWaypoints());
            client.saveConfigSoon();
        });
        contextMenu.add(client.config().mapShowGrid() ? "Hide chunk grid"
                : "Show chunk grid", IconAtlas.Icon.GRID, () ->
        {
            client.config().setMapShowGrid(!client.config().mapShowGrid());
            client.saveConfigSoon();
        });
        contextMenu.open((int) Math.round(mouseX), (int) Math.round(mouseY),
                contextMenu.items().isEmpty() ? List.of() : contextMenu.items());
    }

    private Waypoint waypointAt(double mouseX, double mouseY)
    {
        if (client.waypoints() == null || client.maps() == null
                || client.maps().dimensionId() == null)
        {
            return null;
        }
        MapView view = mapView(layout());
        double best = 14.0D;
        Waypoint nearest = null;
        for (Waypoint waypoint : client.waypoints().inDimension(client.maps().dimensionId()))
        {
            if (!waypoint.visible())
            {
                continue;
            }
            double x = view.screenX(waypoint.x() + 0.5D, waypoint.z() + 0.5D);
            double y = view.screenY(waypoint.x() + 0.5D, waypoint.z() + 0.5D);
            double distance = Math.hypot(mouseX - x, mouseY - y);
            if (distance < best)
            {
                best = distance;
                nearest = waypoint;
            }
        }
        return nearest;
    }

    private void zoomAt(double mouseX, double mouseY, double factor)
    {
        MapView view = mapView(layout());
        double worldX = view.worldX(mouseX, mouseY);
        double worldZ = view.worldZ(mouseX, mouseY);
        double next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (next == zoom)
        {
            return;
        }
        zoom = next;
        Layout layout = layout();
        double dx = mouseX - (layout.mapX + layout.mapWidth / 2.0D);
        double dy = mouseY - (layout.mapY + layout.mapHeight / 2.0D);
        centerX = worldX - dx * zoom;
        centerZ = worldZ - dy * zoom;
    }

    private int surfaceY(Minecraft minecraft, int blockX, int blockZ)
    {
        if (minecraft == null || minecraft.level == null)
        {
            return 64;
        }
        return minecraft.level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, blockX,
                blockZ) - 1;
    }

    private void centerOnPlayer()    {
        if (minecraft != null && minecraft.player != null)
        {
            centerX = minecraft.player.getX();
            centerZ = minecraft.player.getZ();
        }
    }

    private void focusSearch()
    {
        if (searchField != null)
        {
            searchField.setFocused(true);
            setFocused(searchField);
        }
    }

    private List<Waypoint> filteredWaypoints()
    {
        WaypointManager manager = client.waypoints();
        if (manager == null || client.maps() == null || client.maps().dimensionId() == null)
        {
            return List.of();
        }
        List<Waypoint> source = manager.inDimension(client.maps().dimensionId());
        String query = searchQuery.toLowerCase(Locale.ROOT);
        List<String> groups = groupOptions();
        String group = filterIndex > 0 && filterIndex < groups.size() ? groups.get(filterIndex)
                : null;
        List<Waypoint> filtered = new ArrayList<>();
        for (Waypoint waypoint : source)
        {
            if (group != null && !waypoint.group().equals(group))
            {
                continue;
            }
            if (!query.isEmpty())
            {
                String haystack = (waypoint.name() + " " + waypoint.group() + " "
                        + waypoint.x() + " " + waypoint.z()).toLowerCase(Locale.ROOT);
                if (!haystack.contains(query))
                {
                    continue;
                }
            }
            filtered.add(waypoint);
        }
        double originX = minecraft.player == null ? centerX : minecraft.player.getX();
        double originZ = minecraft.player == null ? centerZ : minecraft.player.getZ();
        Comparator<Waypoint> comparator = switch (sortIndex)
        {
            case 1 -> Comparator.comparingDouble(waypoint -> Math.hypot(
                    waypoint.x() - originX, waypoint.z() - originZ));
            case 2 -> Comparator.comparingInt(Waypoint::color);
            default -> Comparator.comparing(Waypoint::name, String.CASE_INSENSITIVE_ORDER);
        };
        filtered.sort(comparator);
        return filtered;
    }

    private List<String> groupOptions()
    {
        List<String> groups = new ArrayList<>();
        groups.add("All groups");
        if (client.waypoints() != null)
        {
            groups.addAll(client.waypoints().groups());
        }
        return groups;
    }

    private Layout layout()
    {
        int sidebarWidth = width >= 1040 ? 320 : width >= 860 ? 270 : 0;
        int sidebarX = MARGIN;
        int sidebarY = TOOLBAR_HEIGHT + MARGIN;
        int sidebarHeight = height - sidebarY - STATUS_HEIGHT - MARGIN;
        int layersWidth = layersOpen && width >= 1280 ? 280 : 0;
        int layersX = width - MARGIN - layersWidth;
        int layersY = TOOLBAR_HEIGHT + MARGIN;
        int layersHeight = height - layersY - STATUS_HEIGHT - MARGIN;
        int mapPanelX = sidebarWidth > 0 ? sidebarX + sidebarWidth + MARGIN : MARGIN;
        int mapPanelY = TOOLBAR_HEIGHT + MARGIN;
        int mapPanelWidth = width - mapPanelX - MARGIN
                - (layersWidth > 0 ? layersWidth + MARGIN : 0);
        int mapPanelHeight = height - mapPanelY - STATUS_HEIGHT - MARGIN;
        mapPanelWidth = Math.max(120, mapPanelWidth);
        mapPanelHeight = Math.max(120, mapPanelHeight);
        int mapX = mapPanelX + 10;
        int mapY = mapPanelY + 10;
        int mapWidth = Math.max(40, mapPanelWidth - 20);
        int mapHeight = Math.max(40, mapPanelHeight - 20);
        return new Layout(TOOLBAR_HEIGHT, STATUS_HEIGHT, sidebarX, sidebarY, sidebarWidth,
                sidebarHeight, layersX, layersY, layersWidth, layersHeight, mapPanelX,
                mapPanelY, mapPanelWidth, mapPanelHeight, mapX, mapY, mapWidth, mapHeight);
    }

    private MapView mapView(Layout layout)
    {
        return new MapView(centerX, centerZ, layout.mapX, layout.mapY, layout.mapWidth,
                layout.mapHeight, zoom, 0.0D);
    }

    private String subtitle()
    {
        String dimension = client.maps() == null ? "Overworld"
                : dimensionName(client.maps().dimensionId());
        String mode = client.config().caveEnabled()
                ? (client.config().caveAuto() ? "Cave auto" : "Cave Y "
                + client.config().caveY()) : "Surface";
        return dimension + "  \u00B7  " + mode;
    }

    private boolean visibleCategory(String category)
    {
        return switch (category)
        {
            case "player" -> client.config().showMapPlayers();
            case "hostile" -> client.config().showHostile();
            case "passive" -> client.config().showPassive();
            case "item" -> client.config().showItems();
            default -> client.config().showOtherEntities();
        };
    }

    private static String category(Entity entity)
    {
        if (entity instanceof AbstractClientPlayer)
        {
            return "player";
        }
        if (entity instanceof ItemEntity)
        {
            return "item";
        }
        if (entity instanceof Enemy)
        {
            return "hostile";
        }
        if (entity instanceof LivingEntity living)
        {
            MobCategory mobCategory = living.getType().getCategory();
            if (mobCategory == MobCategory.MONSTER)
            {
                return "hostile";
            }
            if (mobCategory == MobCategory.CREATURE || mobCategory == MobCategory.AMBIENT
                    || mobCategory == MobCategory.AXOLOTLS
                    || mobCategory == MobCategory.WATER_CREATURE
                    || mobCategory == MobCategory.WATER_AMBIENT
                    || mobCategory == MobCategory.UNDERGROUND_WATER_CREATURE)
            {
                return "passive";
            }
        }
        return "other";
    }

    private static boolean isSlimeChunk(long seed, int chunkX, int chunkZ)
    {
        long value = seed + (long) chunkX * chunkX * 4987142L + (long) chunkX * 5947611L
                + (long) chunkZ * chunkZ * 4392871L + (long) chunkZ * 389711L;
        return new java.util.Random(value ^ 987234911L).nextInt(10) == 0;
    }

    private static String dimensionName(String id)
    {
        if (id == null)
        {
            return "Unknown";
        }
        int colon = id.indexOf(':');
        return pretty(colon >= 0 ? id.substring(colon + 1) : id);
    }

    private static String pretty(String value)
    {
        if (value == null || value.isEmpty())
        {
            return "Unknown";
        }
        String spaced = value.replace('_', ' ');
        StringBuilder builder = new StringBuilder(spaced.length());
        boolean capitalize = true;
        for (int index = 0; index < spaced.length(); index++)
        {
            char character = spaced.charAt(index);
            if (character == ' ')
            {
                capitalize = true;
                builder.append(character);
            }
            else if (capitalize)
            {
                builder.append(Character.toUpperCase(character));
                capitalize = false;
            }
            else
            {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private static int opaque(int argb)
    {
        return 0xFF000000 | (argb & 0xFFFFFF);
    }

    private static boolean inside(int x, int y, int width, int height, double px, double py)
    {
        return width > 0 && height > 0 && px >= x && px < x + width && py >= y
                && py < y + height;
    }

    private record Layout(int toolbarHeight, int statusHeight, int sidebarX, int sidebarY,
                          int sidebarWidth, int sidebarHeight, int layersX, int layersY,
                          int layersWidth, int layersHeight, int mapPanelX, int mapPanelY,
                          int mapPanelWidth, int mapPanelHeight, int mapX, int mapY,
                          int mapWidth, int mapHeight)
    {
    }

    private static final class LayerRow
    {
        private final String label;
        private final IconAtlas.Icon icon;
        private final java.util.function.BooleanSupplier getter;
        private final java.util.function.Consumer<Boolean> setter;
        private final boolean cave;
        private UiToggle widget;

        private LayerRow(String label, IconAtlas.Icon icon,
                         java.util.function.BooleanSupplier getter,
                         java.util.function.Consumer<Boolean> setter)
        {
            this(label, icon, getter, setter, false);
        }

        private LayerRow(String label, IconAtlas.Icon icon,
                         java.util.function.BooleanSupplier getter,
                         java.util.function.Consumer<Boolean> setter, boolean cave)
        {
            this.label = label;
            this.icon = icon;
            this.getter = getter;
            this.setter = setter;
            this.cave = cave;
        }

        private boolean get()
        {
            return getter.getAsBoolean();
        }

        private void set(boolean value)
        {
            setter.accept(value);
        }

        private boolean cave()
        {
            return cave;
        }
    }
}
