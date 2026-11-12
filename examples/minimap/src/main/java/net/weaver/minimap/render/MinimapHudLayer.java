package net.weaver.minimap.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
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
import net.minecraft.world.phys.Vec3;
import net.weaver.api.client.HudLayer;
import net.weaver.api.client.HudRenderContext;
import net.weaver.minimap.MinimapClient;
import net.weaver.minimap.api.EntityIconContext;
import net.weaver.minimap.api.HudAnchor;
import net.weaver.minimap.api.HudOverlayContext;
import net.weaver.minimap.api.HudOverlayLine;
import net.weaver.minimap.api.WaypointMarker;
import net.weaver.minimap.api.WaypointQuery;
import net.weaver.minimap.config.MinimapConfig;
import net.weaver.minimap.map.MapManager;
import net.weaver.minimap.ui.theme.Colors;
import net.weaver.minimap.ui.theme.Theme;
import net.weaver.minimap.ui.theme.ThemeManager;
import net.weaver.minimap.ui.widget.UiTooltip;
import net.weaver.minimap.waypoint.Waypoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The redesigned in-game minimap HUD.
 *
 * <p>The layer draws a circular or rounded-glass map with a compass ring, a
 * player arrow, waypoint and entity markers, off-screen waypoint indicators, a
 * configurable readout, and third-party HUD overlay lines. It hides for the
 * debug screen, chat, and any fullscreen screen according to the settings and
 * never mutates world state.</p>
 */
public final class MinimapHudLayer implements HudLayer
{
    private static final String ID = "weaver-minimap:hud";
    private static final int SHADOW_MARGIN = 8;

    private final MinimapClient client;
    private final SmoothDouble zoom = new SmoothDouble();
    private final SmoothDouble rotation = new SmoothDouble(true);
    private final List<MarkerHit> hits = new ArrayList<>();
    private long lastNanos;
    private boolean hoveredThisFrame;
    private volatile long lastRenderNanos;

    /**
     * Creates the layer.
     *
     * @param client the mod client, never {@code null}
     */
    public MinimapHudLayer(MinimapClient client)
    {
        this.client = client;
    }

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public int order()
    {
        return 1000;
    }

    @Override
    public void render(HudRenderContext context)
    {
        Minecraft minecraft = client.minecraft();
        if (minecraft == null || minecraft.level == null || minecraft.player == null)
        {
            return;
        }
        if (!(context.graphics() instanceof GuiGraphicsExtractor graphics))
        {
            return;
        }
        long started = System.nanoTime();
        MinimapConfig config = client.config();
        if (!shouldRender(minecraft, config))
        {
            return;
        }
        MapManager maps = client.maps();
        if (maps == null || !maps.isActive() || maps.dimensionId() == null)
        {
            return;
        }

        int size = hudSize(config);
        int x = anchorX(config, context.width(), size);
        int y = anchorY(config, context.height(), size);
        if (x + size + SHADOW_MARGIN <= 0 || y + size + SHADOW_MARGIN <= 0
                || x - SHADOW_MARGIN >= context.width()
                || y - SHADOW_MARGIN >= context.height())
        {
            return;
        }

        long now = System.nanoTime();
        double frameSeconds = lastNanos == 0L ? 0.016D
                : Math.min(0.1D, Math.max(0.001D, (now - lastNanos) / 1.0E9D));
        lastNanos = now;
        double smoothZoom = zoom.update(config.zoom(), frameSeconds);
        LocalPlayer player = minecraft.player;
        Vec3 position = player.getPosition(context.partialTick());
        float yaw = player.getYRot(context.partialTick());
        double targetRotation = config.rotationMode() == MinimapConfig.Rotation.PLAYER_UP
                ? 180.0D - yaw : 0.0D;
        double smoothRotation = rotation.update(targetRotation, frameSeconds);
        boolean rounded = config.hudShape() == MinimapConfig.Shape.ROUNDED_SQUARE;

        int caveY = Integer.MIN_VALUE;
        boolean cave = false;
        if (maps.caveEnabled() && config.showMapCaveLayer())
        {
            caveY = config.caveAuto() ? maps.autoCaveY() : config.caveY();
            cave = caveY != Integer.MIN_VALUE;
        }

        Theme theme = ThemeManager.get().current();
        MapView view = new MapView(position.x, position.z, x, y, size, size, smoothZoom,
                smoothRotation);
        double mouseX = mouseX(minecraft);
        double mouseY = mouseY(minecraft);
        hoveredThisFrame = false;

        drawChrome(graphics, theme, config, x, y, size, rounded);
        graphics.enableScissor(x, y, x + size, y + size);
        try
        {
            int empty = Colors.withAlpha(theme.surface(), 0xD0);
            boolean drawn = client.hudCompositor().draw(graphics, maps, maps.dimensionId(), view,
                    cave, caveY, rounded, empty, false);
            if (drawn)
            {
                drawCompass(graphics, minecraft.font, theme, view, config);
                drawMarkers(graphics, minecraft, maps, view, config, theme, context, mouseX,
                        mouseY);
                drawPlayer(graphics, player, view, yaw, config, theme);
                if (cave)
                {
                    drawCaveBadge(graphics, minecraft.font, theme, view, caveY);
                }
            }
            else
            {
                drawLoading(graphics, minecraft.font, theme, view);
            }
        }
        finally
        {
            graphics.disableScissor();
        }
        drawReadout(graphics, minecraft, maps, config, theme, view, cave, caveY, size);
        drawHudOverlays(graphics, minecraft.font, theme, context, config, maps);
        client.markHudRendered();
        lastRenderNanos = System.nanoTime() - started;
    }

    /** The most recent HUD render duration in nanoseconds. */
    public long lastRenderNanos()
    {
        return lastRenderNanos;
    }

    /** Clears smoothing so the next frame snaps to the live values. */
    public void resetSmoothing()
    {
        lastNanos = 0L;
        zoom.snap(client.config().zoom());
        rotation.snap(0.0D);
    }

    private boolean shouldRender(Minecraft minecraft, MinimapConfig config)
    {
        if (minecraft.gui == null || minecraft.gui.hud == null || minecraft.gui.hud.isHidden())
        {
            return false;
        }
        if (client.isHudTemporarilyHidden())
        {
            return false;
        }
        if (minecraft.getDebugOverlay() != null && minecraft.getDebugOverlay().showDebugScreen()
                && (config.hideInDebug() || config.hideWithDebugScreen()))
        {
            return false;
        }
        if (minecraft.gui.screen() != null)
        {
            if (minecraft.gui.screen() instanceof ChatScreen && !config.hideInChat())
            {
                return true;
            }
            return false;
        }
        return config.enabled();
    }

    private void drawChrome(GuiGraphicsExtractor graphics, Theme theme, MinimapConfig config,
                            int x, int y, int size, boolean rounded)
    {
        client.hudChrome().draw(graphics, theme, config, x, y, size, rounded);
    }
    private void drawCompass(GuiGraphicsExtractor graphics, Font font, Theme theme,
                             MapView view, MinimapConfig config)
    {
        if (!config.showCompass() || view.width() < 96)
        {
            return;
        }
        int centerX = view.originX() + view.width() / 2;
        int centerY = view.originY() + view.height() / 2;
        int radius = view.width() / 2;
        int ring = Colors.withAlpha(theme.text(), 0x22);
        UiDraw.ring(graphics, centerX, centerY, radius - 4, radius - 5, ring);
        double rotation = view.rotationDegrees();
        for (int tick = 0; tick < 16; tick++)
        {
            double angle = Math.toRadians(tick * 22.5D + rotation);
            int tickRadius = tick % 4 == 0 ? radius - 9 : radius - 7;
            int dotX = centerX + (int) Math.round(Math.cos(angle) * tickRadius);
            int dotY = centerY + (int) Math.round(Math.sin(angle) * tickRadius);
            int color = tick % 4 == 0 ? Colors.withAlpha(theme.text(), 0x88)
                    : Colors.withAlpha(theme.text(), 0x33);
            graphics.fill(dotX - 1, dotY - 1, dotX + 1, dotY + 1, color);
        }
        String[] labels = {"N", "E", "S", "W"};
        double[] angles = {-90.0D, 0.0D, 90.0D, 180.0D};
        for (int index = 0; index < labels.length; index++)
        {
            double angle = Math.toRadians(angles[index] + rotation);
            int labelRadius = radius - font.lineHeight - 4;
            int labelX = centerX + (int) Math.round(Math.cos(angle) * labelRadius);
            int labelY = centerY + (int) Math.round(Math.sin(angle) * labelRadius)
                    - font.lineHeight / 2;
            int color = index == 0 ? theme.accent() : Colors.withAlpha(theme.text(), 0xB0);
            graphics.text(font, labels[index], labelX - font.width(labels[index]) / 2,
                    labelY, color, true);
        }
    }

    private void drawPlayer(GuiGraphicsExtractor graphics, LocalPlayer player, MapView view,
                            float yaw, MinimapConfig config, Theme theme)
    {
        double rotation = config.rotationMode() == MinimapConfig.Rotation.PLAYER_UP
                ? 0.0D : yaw + 180.0D;
        double scale = Math.max(0.75D, Math.min(1.5D, view.width() / 160.0D));
        MapMarkerRenderer.drawPlayerArrow(graphics, view.originX() + view.width() / 2.0D,
                view.originY() + view.height() / 2.0D, rotation, theme.player(),
                0xFF06121C, scale);
    }

    private void drawMarkers(GuiGraphicsExtractor graphics, Minecraft minecraft, MapManager maps,
                             MapView view, MinimapConfig config, Theme theme,
                             HudRenderContext context, double mouseX, double mouseY)
    {
        String dimension = maps.dimensionId();
        LocalPlayer player = minecraft.player;
        if (player == null)
        {
            return;
        }
        double playerX = view.centerX();
        double playerZ = view.centerZ();
        hits.clear();
        if (config.showWaypoints() && client.waypoints() != null)
        {
            for (Waypoint waypoint : client.waypoints().inDimension(dimension))
            {
                if (!waypoint.visible())
                {
                    continue;
                }
                if (waypoint.death() && (!config.deathWaypointEnabled()
                        || !config.showMapDeaths()))
                {
                    continue;
                }
                drawMarker(graphics, view, config, theme, waypoint, playerX, playerZ,
                        mouseX, mouseY, hits, false);
            }
        }
        if (config.showWaypoints() && !client.api().waypointProviders().isEmpty())
        {
            for (WaypointMarker marker : client.api().resolveWaypoints(
                    new WaypointQuery(dimension, view.centerX(), view.centerZ(),
                            view.halfWorldWidth(), false)))
            {
                drawProviderMarker(graphics, view, config, theme, marker, mouseX, mouseY, hits);
            }
        }
        if (config.showPlayers())
        {
            drawPlayerMarkers(graphics, minecraft, view, config, theme);
        }
        if (config.showEntities())
        {
            drawEntityMarkers(graphics, minecraft, maps, view, config, theme);
        }
        if (!hits.isEmpty())
        {
            MarkerHit nearest = hits.stream()
                    .min(Comparator.comparingDouble(MarkerHit::distance))
                    .orElse(null);
            if (nearest != null && nearest.distance <= 12.0D)
            {
                hoveredThisFrame = true;
                UiTooltip.draw(graphics, minecraft.font, nearest.lines,
                        (int) Math.round(mouseX), (int) Math.round(mouseY),
                        context.width(), context.height());
            }
        }
    }

    private void drawMarker(GuiGraphicsExtractor graphics, MapView view, MinimapConfig config,
                            Theme theme, Waypoint waypoint, double playerX, double playerZ,
                            double mouseX, double mouseY, List<MarkerHit> hits, boolean provider)
    {
        double screenX = view.screenX(waypoint.x() + 0.5D, waypoint.z() + 0.5D);
        double screenY = view.screenY(waypoint.x() + 0.5D, waypoint.z() + 0.5D);
        double distance = Math.hypot(waypoint.x() + 0.5D - playerX,
                waypoint.z() + 0.5D - playerZ);
        double markerScale = Math.max(0.8D, Math.min(1.4D, view.width() / 180.0D));
        if (view.contains(screenX, screenY, 8))
        {
            MapMarkerRenderer.drawWaypoint(graphics, theme, waypoint, screenX, screenY,
                    markerScale);
            double mouseDistance = Math.hypot(mouseX - screenX, mouseY - screenY);
            hits.add(new MarkerHit(mouseDistance, tooltipFor(waypoint, distance)));
            if (config.showWaypointLabels()
                    && distance <= config.waypointLabelDistance())
            {
                MapMarkerRenderer.drawLabel(graphics, Minecraft.getInstance().font, theme,
                        waypoint.name(), screenX + 10, screenY - 4,
                        Math.max(40, (int) (view.width() * 0.55D)), true);
            }
        }
        else if (config.showOffscreenArrows())
        {
            OffscreenPoint point = offscreen(view, screenX, screenY);
            MapMarkerRenderer.drawOffscreenArrow(graphics, point.x, point.y, point.angle,
                    waypoint.death() ? theme.death() : opaque(waypoint.color()),
                    0xE6000000);
        }
    }

    private void drawProviderMarker(GuiGraphicsExtractor graphics, MapView view,
                                    MinimapConfig config, Theme theme, WaypointMarker marker,
                                    double mouseX, double mouseY, List<MarkerHit> hits)
    {
        double screenX = view.screenX(marker.x() + 0.5D, marker.z() + 0.5D);
        double screenY = view.screenY(marker.x() + 0.5D, marker.z() + 0.5D);
        if (!view.contains(screenX, screenY, 8))
        {
            return;
        }
        MapMarkerRenderer.drawWaypoint(graphics, theme, marker, screenX, screenY, 1.0D);
        double distance = Math.hypot(marker.x() + 0.5D - view.centerX(),
                marker.z() + 0.5D - view.centerZ());
        hits.add(new MarkerHit(Math.hypot(mouseX - screenX, mouseY - screenY),
                List.of(Component.literal(marker.name()),
                        Component.literal("x " + marker.x() + "  z " + marker.z()),
                        Component.literal(MapMarkerRenderer.distance(distance) + " blocks"))));
    }

    private void drawPlayerMarkers(GuiGraphicsExtractor graphics, Minecraft minecraft,
                                   MapView view, MinimapConfig config, Theme theme)
    {
        if (minecraft.level == null)
        {
            return;
        }
        for (AbstractClientPlayer other : minecraft.level.players())
        {
            if (other == minecraft.player)
            {
                continue;
            }
            double x = view.screenX(other.getX(), other.getZ());
            double y = view.screenY(other.getX(), other.getZ());
            if (!view.contains(x, y, 4))
            {
                continue;
            }
            MapMarkerRenderer.drawEntity(graphics, "player", x, y, 1.1D, theme);
        }
    }

    private void drawEntityMarkers(GuiGraphicsExtractor graphics, Minecraft minecraft,
                                   MapManager maps, MapView view, MinimapConfig config,
                                   Theme theme)
    {
        if (minecraft.level == null)
        {
            return;
        }
        int limit = Math.max(0, config.entityLimitHud());
        int drawn = 0;
        for (Entity entity : minecraft.level.entitiesForRendering())
        {
            if (drawn >= limit)
            {
                break;
            }
            if (entity == minecraft.player || entity instanceof AbstractClientPlayer)
            {
                continue;
            }
            String category = category(entity);
            if (!categoryVisible(config, category))
            {
                continue;
            }
            double x = view.screenX(entity.getX(), entity.getZ());
            double y = view.screenY(entity.getX(), entity.getZ());
            if (!view.contains(x, y, 3))
            {
                continue;
            }
            MapMarkerRenderer.drawEntity(graphics, category, x, y, 0.9D, theme);
            drawn++;
        }
    }

    private void drawReadout(GuiGraphicsExtractor graphics, Minecraft minecraft, MapManager maps,
                             MinimapConfig config, Theme theme, MapView view, boolean cave,
                             int caveY, int size)
    {
        if (!config.showCoordinates() && !config.showBiome() && !config.showDimension()
                && !config.showZoom() && !config.showTime())
        {
            return;
        }
        Font font = minecraft.font;
        LocalPlayer player = minecraft.player;
        if (player == null)
        {
            return;
        }
        int lineHeight = font.lineHeight + 1;
        int stripHeight = lineHeight * 2 + 6;
        int stripX = view.originX() + 5;
        boolean above = config.corner() == MinimapConfig.Corner.BOTTOM_LEFT
                || config.corner() == MinimapConfig.Corner.BOTTOM_RIGHT;
        int stripY = above ? view.originY() - stripHeight - 5
                : view.originY() + size + 5;
        int stripWidth = size - 10;
        if (size < 110)
        {
            stripHeight = lineHeight + 5;
            stripY = above ? view.originY() - stripHeight - 4
                    : view.originY() + size + 4;
        }
        int plate = Colors.withAlpha(theme.background(), 0xB4);
        UiDraw.roundRect(graphics, stripX, stripY, stripWidth, stripHeight, 7, plate);
        int y = stripY + 3;
        StringBuilder first = new StringBuilder();
        if (config.showCoordinates())
        {
            first.append(player.getBlockX()).append(' ').append(player.getBlockY())
                    .append(' ').append(player.getBlockZ());
        }
        if (config.showZoom())
        {
            String zoomValue = String.format(Locale.ROOT, "%.0f%%",
                    Math.max(1.0D, Math.min(999.0D, 100.0D / view.zoom())));
            if (first.length() == 0)
            {
                first.append(zoomValue);
            }
            else
            {
                first.append("  ").append(zoomValue);
            }
        }
        UiDraw.textClipped(graphics, font, first.toString(), stripX + 5, y,
                stripWidth - 10, theme.text(), true);
        if (size >= 110)
        {
            StringBuilder second = new StringBuilder();
            if (config.showBiome() && minecraft.level != null)
            {
                second.append(biomeName(minecraft.level, player.blockPosition()));
            }
            if (config.showDimension())
            {
                if (second.length() > 0)
                {
                    second.append("  ");
                }
                second.append(dimensionName(maps.dimensionId()));
            }
            if (config.showTime() && minecraft.level != null)
            {
                if (second.length() > 0)
                {
                    second.append("  ");
                }
                second.append(clock(minecraft.level));
            }
            if (cave)
            {
                second.append("  Y ").append(caveY);
            }
            UiDraw.textClipped(graphics, font, second.toString(), stripX + 5, y + lineHeight,
                    stripWidth - 10, theme.textMuted(), true);
        }
    }

    private void drawHudOverlays(GuiGraphicsExtractor graphics, Font font, Theme theme,
                                 HudRenderContext context, MinimapConfig config, MapManager maps)
    {
        if (client.api().hudOverlayProviders().isEmpty() || maps.dimensionId() == null)
        {
            return;
        }
        LocalPlayer player = client.minecraft().player;
        if (player == null)
        {
            return;
        }
        List<HudOverlayLine> lines = new ArrayList<>(client.api().resolveHudOverlays(
                new HudOverlayContext(false, context.width(), context.height(),
                        config.hudScale(), maps.dimensionId(), player.getBlockX(),
                        player.getBlockY(), player.getBlockZ(), config.zoom())));
        lines.sort(Comparator.comparingInt(HudOverlayLine::zOrder));
        for (HudOverlayLine line : lines)
        {
            int width = font.width(line.text());
            int x = switch (line.anchor())
            {
                case TOP_LEFT, BOTTOM_LEFT -> 4 + line.offsetX();
                case TOP_RIGHT, BOTTOM_RIGHT -> context.width() - width - 4 - line.offsetX();
            };
            int y = switch (line.anchor())
            {
                case TOP_LEFT, TOP_RIGHT -> 4 + line.offsetY();
                case BOTTOM_LEFT, BOTTOM_RIGHT -> context.height() - font.lineHeight - 4
                        - line.offsetY();
            };
            graphics.text(font, line.text(), x, y, line.color(), true);
        }
    }

    private void drawCaveBadge(GuiGraphicsExtractor graphics, Font font, Theme theme,
                               MapView view, int caveY)
    {
        String text = "CAVE  Y " + caveY;
        int x = view.originX() + 8;
        int y = view.originY() + 8;
        UiDraw.badge(graphics, font, text, x, y, Colors.withAlpha(theme.accent(), 0xD0),
                theme.accentContrast());
    }

    private void drawLoading(GuiGraphicsExtractor graphics, Font font, Theme theme,
                             MapView view)
    {
        int centerX = view.originX() + view.width() / 2;
        int centerY = view.originY() + view.height() / 2;
        UiDraw.circle(graphics, centerX, centerY, view.width() / 2 - 2,
                Colors.withAlpha(theme.surfaceAlt(), 0xE0));
        UiDraw.ring(graphics, centerX, centerY, view.width() / 2 - 4,
                view.width() / 2 - 5, Colors.withAlpha(theme.border(), 0xA0));
        String text = "Loading terrain";
        UiDraw.textCentered(graphics, font, text, centerX, centerY - font.lineHeight / 2,
                theme.textMuted(), false);
        int dots = (int) ((System.currentTimeMillis() / 250L) % 4L);
        StringBuilder spinner = new StringBuilder();
        for (int index = 0; index < 3; index++)
        {
            spinner.append(index < dots ? '\u2022' : '\u00B7');
        }
        UiDraw.textCentered(graphics, font, spinner.toString(), centerX,
                centerY + font.lineHeight + 2, theme.accent(), false);
    }

    /**
     * Draws a themed HUD preview inside a settings panel.
     *
     * @param graphics the graphics sink
     * @param x        the preview left edge
     * @param y        the preview top edge
     * @param size     the preview size
     */
    public void renderPreview(GuiGraphicsExtractor graphics, int x, int y, int size)
    {
        MinimapConfig config = client.config();
        Theme theme = ThemeManager.get().current();
        boolean rounded = config.hudShape() == MinimapConfig.Shape.ROUNDED_SQUARE;
        drawChrome(graphics, theme, config, x, y, size, rounded);
        graphics.enableScissor(x, y, x + size, y + size);
        try
        {
            drawPreviewTerrain(graphics, theme, x, y, size, rounded);
            MapView view = new MapView(0.0D, 0.0D, x, y, size, size, 1.0D,
                    config.rotationMode() == MinimapConfig.Rotation.PLAYER_UP ? 28.0D : 0.0D);
            drawCompass(graphics, Minecraft.getInstance().font, theme, view, config);
            MapMarkerRenderer.drawPlayerArrow(graphics, x + size / 2.0D, y + size / 2.0D,
                    config.rotationMode() == MinimapConfig.Rotation.PLAYER_UP ? 0.0D : 180.0D,
                    theme.player(), 0xFF06121C, 1.0D);
            drawPreviewMarkers(graphics, theme, view);
        }
        finally
        {
            graphics.disableScissor();
        }
        int lineHeight = Minecraft.getInstance().font.lineHeight + 1;
        int stripHeight = lineHeight * 2 + 6;
        int stripX = x + 5;
        int stripY = y + size + 5;
        UiDraw.roundRect(graphics, stripX, stripY, size - 10, stripHeight, 7,
                Colors.withAlpha(theme.background(), 0xB4));
        UiDraw.textClipped(graphics, Minecraft.getInstance().font, "128 72 -340  100%",
                stripX + 5, stripY + 3, size - 20, theme.text(), true);
        UiDraw.textClipped(graphics, Minecraft.getInstance().font,
                "Plains  Overworld  10:24", stripX + 5, stripY + 3 + lineHeight,
                size - 20, theme.textMuted(), true);
    }

    private void drawPreviewTerrain(GuiGraphicsExtractor graphics, Theme theme, int x, int y,
                                    int size, boolean rounded)
    {
        int radius = rounded ? Math.max(8, size / 9) : size / 2;
        if (rounded)
        {
            UiDraw.roundRect(graphics, x, y, size, size, radius, theme.surfaceAlt());
        }
        else
        {
            UiDraw.circle(graphics, x + size / 2, y + size / 2, size / 2, theme.surfaceAlt());
        }
        int inset = size / 8;
        int terrain = size - inset * 2;
        UiDraw.roundRect(graphics, x + inset, y + inset, terrain, terrain, Math.max(6, inset / 2),
                0xFF3E6B45);
        UiDraw.roundRect(graphics, x + inset + terrain / 3, y + inset + terrain / 3,
                terrain / 3, terrain / 3, Math.max(4, inset / 2), 0xFF3D6FA8);
        UiDraw.roundRect(graphics, x + inset + terrain / 10, y + inset + terrain / 2,
                terrain / 3, terrain / 4, Math.max(4, inset / 2), 0xFF7C8C5A);
        UiDraw.roundRect(graphics, x + inset + terrain / 2, y + inset + terrain / 8,
                terrain / 3, terrain / 3, Math.max(4, inset / 2), 0xFF4E7A4E);
        for (int index = 0; index < 8; index++)
        {
            int dotX = x + inset + (index * 37) % Math.max(1, terrain - 4);
            int dotY = y + inset + (index * 61) % Math.max(1, terrain - 4);
            UiDraw.circle(graphics, dotX, dotY, 1, 0x66000000);
        }
        UiDraw.dividerH(graphics, x + inset, y + inset + terrain / 3, terrain, 0x22000000);
        UiDraw.dividerV(graphics, x + inset + terrain / 2, y + inset, terrain, 0x22000000);
    }

    private void drawPreviewMarkers(GuiGraphicsExtractor graphics, Theme theme, MapView view)
    {
        int centerX = view.originX() + view.width() / 2;
        int centerY = view.originY() + view.height() / 2;
        int radius = view.width() / 2;
        int[][] offsets = {{-radius / 3, radius / 3, theme.waypointColor(2)},
                {radius / 3, -radius / 4, theme.waypointColor(4)},
                {radius / 4, radius / 3, theme.waypointColor(7)}};
        for (int[] offset : offsets)
        {
            int markerX = centerX + offset[0];
            int markerY = centerY + offset[1];
            UiDraw.circle(graphics, markerX, markerY, 7, 0xFF000000);
            UiDraw.circle(graphics, markerX, markerY, 6, offset[2]);
            IconAtlas.draw(graphics, IconAtlas.Icon.PIN, markerX - 4, markerY - 4, 8,
                    Colors.contrastText(offset[2]));
        }
        MapMarkerRenderer.drawOffscreenArrow(graphics, view.originX() + view.width() - 12,
                centerY, 0.0D, theme.accent(), 0xFF000000);
    }

    private List<Component> tooltipFor(Waypoint waypoint, double distance)
    {
        List<Component> lines = new ArrayList<>(4);
        lines.add(Component.literal(waypoint.name()));
        lines.add(Component.literal("x " + waypoint.x() + "  y " + waypoint.y() + "  z "
                + waypoint.z()));
        String group = waypoint.group().isBlank() ? "Ungrouped" : waypoint.group();
        lines.add(Component.literal(group + "  \u00B7  "
                + MapMarkerRenderer.distance(distance) + " blocks"));
        return lines;
    }

    private static OffscreenPoint offscreen(MapView view, double screenX, double screenY)
    {
        double centerX = view.originX() + view.width() / 2.0D;
        double centerY = view.originY() + view.height() / 2.0D;
        double dx = screenX - centerX;
        double dy = screenY - centerY;
        if (dx == 0.0D && dy == 0.0D)
        {
            return new OffscreenPoint(centerX, centerY, 0.0D);
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        double radius = view.width() / 2.0D - 12.0D;
        double length = Math.hypot(dx, dy);
        return new OffscreenPoint(centerX + dx / length * radius,
                centerY + dy / length * radius, angle);
    }

    private static boolean categoryVisible(MinimapConfig config, String category)
    {
        return switch (category)
        {
            case "player" -> config.showPlayers();
            case "hostile" -> config.showHostile();
            case "passive" -> config.showPassive();
            case "item" -> config.showItems();
            default -> config.showOtherEntities();
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

    private static String biomeName(ClientLevel level, BlockPos pos)
    {
        return level.getBiome(pos).unwrapKey()
                .map(key -> pretty(key.identifier().getPath()))
                .orElse("Unknown");
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

    private static String clock(ClientLevel level)
    {
        long time = Math.floorMod(level.getDefaultClockTime(), 24000L);
        long hours = Math.floorMod(time / 1000L + 6L, 24L);
        long minutes = time % 1000L * 60L / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private static String pretty(String value)
    {
        if (value == null || value.isEmpty())
        {
            return "Unknown";
        }
        String spaced = value.replace('_', ' ').replace('/', ' ');
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

    private static int hudSize(MinimapConfig config)
    {
        return Math.max(64, Math.min(384,
                (int) Math.round(config.size() * config.hudScale())));
    }

    private static int anchorX(MinimapConfig config, int width, int size)
    {
        int offset = Math.max(0, config.offsetX());
        return switch (config.corner())
        {
            case TOP_LEFT, BOTTOM_LEFT -> offset;
            case TOP_RIGHT, BOTTOM_RIGHT -> width - size - offset;
        };
    }

    private static int anchorY(MinimapConfig config, int height, int size)
    {
        int offset = Math.max(0, config.offsetY());
        return switch (config.corner())
        {
            case TOP_LEFT, TOP_RIGHT -> offset;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> height - size - offset;
        };
    }

    private static double mouseX(Minecraft minecraft)
    {
        return minecraft.getWindow() == null ? -1000.0D
                : minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
    }

    private static double mouseY(Minecraft minecraft)
    {
        return minecraft.getWindow() == null ? -1000.0D
                : minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
    }

    private static int opaque(int argb)
    {
        return 0xFF000000 | (argb & 0xFFFFFF);
    }

    private record MarkerHit(double distance, List<Component> lines)
    {
    }

    private record OffscreenPoint(double x, double y, double angle)
    {
    }

    private static final class SmoothDouble
    {
        private final boolean angle;
        private double value;
        private double target;
        private boolean initialized;

        private SmoothDouble()
        {
            this(false);
        }

        private SmoothDouble(boolean angle)
        {
            this.angle = angle;
        }

        private double update(double next, double seconds)
        {
            if (!initialized)
            {
                value = next;
                target = next;
                initialized = true;
                return value;
            }
            target = next;
            double factor = 1.0D - Math.exp(-seconds * 14.0D);
            if (angle)
            {
                double delta = ((target - value + 540.0D) % 360.0D) - 180.0D;
                value += delta * factor;
            }
            else
            {
                value += (target - value) * factor;
            }
            if (Math.abs(target - value) < 0.0005D)
            {
                value = target;
            }
            return value;
        }

        private void snap(double next)
        {
            value = next;
            target = next;
            initialized = true;
        }
    }
}
