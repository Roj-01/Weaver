package net.weaver.minimap.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.weaver.minimap.api.WaypointMarker;
import net.weaver.minimap.ui.theme.Colors;
import net.weaver.minimap.ui.theme.Theme;
import net.weaver.minimap.waypoint.Waypoint;

import java.util.List;
import java.util.Locale;

/**
 * Shared marker drawing for the HUD and the fullscreen map.
 *
 * <p>Every marker is built from GUI primitives so it works with the 26.3
 * extraction pipeline. Player arrows and off-screen indicators use the pose
 * matrix for rotation; markers and labels use the generated icon atlas.</p>
 */
public final class MapMarkerRenderer
{
    private static final int OUTLINE = 0xE6000000;

    private MapMarkerRenderer()
    {
    }

    /**
     * Draws the player arrow.
     *
     * @param graphics  the graphics sink
     * @param centerX   the screen X
     * @param centerY   the screen Y
     * @param rotation  the clockwise rotation in degrees
     * @param fill      the arrow fill
     * @param outline   the arrow outline
     * @param scale     the size scale
     */
    public static void drawPlayerArrow(GuiGraphicsExtractor graphics, double centerX,
                                       double centerY, double rotation, int fill, int outline,
                                       double scale)
    {
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) centerX, (float) centerY);
        graphics.pose().rotate((float) Math.toRadians(rotation));
        int tip = (int) Math.round(-9 * scale);
        int tail = (int) Math.round(7 * scale);
        int halfWidth = (int) Math.round(7 * scale);
        for (int row = tip; row <= tail; row++)
        {
            double t = (row - tip) / (double) Math.max(1, tail - tip);
            int half = (int) Math.round(halfWidth * Math.min(1.0D, t * 1.6D));
            if (row <= tip + 2)
            {
                half = Math.max(1, half);
            }
            graphics.fill(-half - 1, row, half + 2, row + 1, outline);
        }
        for (int row = tip + 1; row < tail; row++)
        {
            double t = (row - tip) / (double) Math.max(1, tail - tip);
            int half = (int) Math.round((halfWidth - 1) * Math.min(1.0D, t * 1.6D));
            graphics.fill(-half, row, half + 1, row + 1, fill);
        }
        int dot = Math.max(1, (int) Math.round(2 * scale));
        graphics.fill(-dot, tip + 2, dot + 1, tip + 2 + dot, outline);
        graphics.fill(-dot + 1, tip + 3, dot, tip + 2 + dot, fill);
        graphics.pose().popMatrix();
    }

    /**
     * Draws an off-screen indicator pointing outward.
     *
     * @param graphics the graphics sink
     * @param x        the clamped screen X
     * @param y        the clamped screen Y
     * @param angle    the outward angle in degrees, where 0 points right
     * @param fill     the triangle fill
     * @param outline  the triangle outline
     */
    public static void drawOffscreenArrow(GuiGraphicsExtractor graphics, double x, double y,
                                          double angle, int fill, int outline)
    {
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().rotate((float) Math.toRadians(angle));
        for (int row = -2; row <= 2; row++)
        {
            int length = 8 - Math.abs(row) * 2;
            graphics.fill(0, row * 2 - 1, length + 1, row * 2 + 1, outline);
        }
        for (int row = -1; row <= 1; row++)
        {
            int length = 6 - Math.abs(row) * 2;
            graphics.fill(1, row * 2, length + 1, row * 2 + 1, fill);
        }
        graphics.pose().popMatrix();
    }

    /**
     * Draws a waypoint marker.
     *
     * @param graphics the graphics sink
     * @param theme    the active theme
     * @param marker   the waypoint
     * @param x        the screen X
     * @param y        the screen Y
     * @param scale    the marker scale
     */
    public static void drawWaypoint(GuiGraphicsExtractor graphics, Theme theme, Waypoint marker,
                                    double x, double y, double scale)
    {
        int color = opaque(marker.color());
        if (marker.death())
        {
            color = theme.death();
        }
        int radius = (int) Math.round(7 * scale);
        int ring = (int) Math.round(1.5D * scale);
        UiDraw.circle(graphics, (int) Math.round(x), (int) Math.round(y), radius + ring,
                OUTLINE);
        UiDraw.circle(graphics, (int) Math.round(x), (int) Math.round(y), radius, color);
        IconAtlas.Icon icon = iconFor(marker.death() ? "death" : marker.icon());
        int iconSize = (int) Math.round(9 * scale);
        int iconColor = Colors.contrastText(color);
        IconAtlas.draw(graphics, icon, (int) Math.round(x) - iconSize / 2,
                (int) Math.round(y) - iconSize / 2, iconSize, iconColor);
    }

    /**
     * Draws a provider waypoint marker.
     *
     * @param graphics the graphics sink
     * @param theme    the active theme
     * @param marker   the provider marker
     * @param x        the screen X
     * @param y        the screen Y
     * @param scale    the marker scale
     */
    public static void drawWaypoint(GuiGraphicsExtractor graphics, Theme theme,
                                    WaypointMarker marker, double x, double y, double scale)
    {
        int color = opaque(marker.color());
        int radius = (int) Math.round(7 * scale);
        UiDraw.circle(graphics, (int) Math.round(x), (int) Math.round(y), radius + 2, OUTLINE);
        UiDraw.circle(graphics, (int) Math.round(x), (int) Math.round(y), radius, color);
        int iconSize = (int) Math.round(9 * scale);
        IconAtlas.draw(graphics, iconFor(marker.icon()),
                (int) Math.round(x) - iconSize / 2, (int) Math.round(y) - iconSize / 2,
                iconSize, Colors.contrastText(color));
    }

    /**
     * Draws a small entity marker.
     *
     * @param graphics the graphics sink
     * @param category the category name
     * @param x        the screen X
     * @param y        the screen Y
     * @param scale    the marker scale
     * @param theme    the active theme
     */
    public static void drawEntity(GuiGraphicsExtractor graphics, String category, double x,
                                  double y, double scale, Theme theme)
    {
        int radius = (int) Math.max(2, Math.round(3 * scale));
        int color = switch (category)
        {
            case "player" -> theme.player();
            case "hostile" -> 0xFFE45757;
            case "passive" -> 0xFF8FD16A;
            case "item" -> 0xFFF5C542;
            default -> 0xFFB0BEC5;
        };
        UiDraw.circle(graphics, (int) Math.round(x), (int) Math.round(y), radius + 1, OUTLINE);
        UiDraw.circle(graphics, (int) Math.round(x), (int) Math.round(y), radius, color);
    }

    /**
     * Draws a marker label with a dark plate.
     *
     * @param graphics  the graphics sink
     * @param font      the font
     * @param theme     the active theme
     * @param text      the label
     * @param x         the screen X
     * @param y         the screen Y
     * @param maxWidth  the maximum label width
     * @param available whether to draw at all
     */
    public static void drawLabel(GuiGraphicsExtractor graphics, Font font, Theme theme,
                                 String text, double x, double y, int maxWidth,
                                 boolean available)
    {
        if (!available || text == null || text.isBlank())
        {
            return;
        }
        int plate = Colors.withAlpha(theme.background(), 0xC8);
        UiDraw.textPlate(graphics, font, text, (int) Math.round(x), (int) Math.round(y),
                maxWidth, theme.text(), plate, true);
    }

    /**
     * Formats a distance for a label.
     *
     * @param blocks the distance in blocks
     * @return a compact distance string
     */
    public static String distance(double blocks)
    {
        if (blocks >= 1000.0D)
        {
            return String.format(Locale.ROOT, "%.1fk", blocks / 1000.0D);
        }
        return Integer.toString((int) Math.round(blocks));
    }

    /**
     * Resolves a waypoint icon id to an atlas icon.
     *
     * @param id the icon id, possibly {@code null}
     * @return the atlas icon, never {@code null}
     */
    public static IconAtlas.Icon iconFor(String id)
    {
        if (id == null || id.isBlank())
        {
            return IconAtlas.Icon.PIN;
        }
        return switch (id.toLowerCase(Locale.ROOT))
        {
            case "death", "skull" -> IconAtlas.Icon.SKULL;
            case "home" -> IconAtlas.Icon.HOME;
            case "portal" -> IconAtlas.Icon.PORTAL;
            case "village" -> IconAtlas.Icon.VILLAGE;
            case "resource", "diamond" -> IconAtlas.Icon.RESOURCE;
            case "cave" -> IconAtlas.Icon.CAVE;
            case "star", "favourite", "favorite" -> IconAtlas.Icon.STAR;
            case "waypoint" -> IconAtlas.Icon.WAYPOINT;
            case "pin" -> IconAtlas.Icon.PIN;
            default ->
            {
                try
                {
                    yield IconAtlas.Icon.valueOf(id.toUpperCase(Locale.ROOT));
                }
                catch (IllegalArgumentException ignored)
                {
                    yield IconAtlas.Icon.PIN;
                }
            }
        };
    }

    /**
     * Returns the icon ids offered by the waypoint editor.
     *
     * @return an immutable list of icon ids
     */
    public static List<String> iconIds()
    {
        return List.of("pin", "home", "death", "portal", "village", "resource", "cave",
                "star", "waypoint");
    }

    private static int opaque(int argb)
    {
        return 0xFF000000 | (argb & 0xFFFFFF);
    }
}
