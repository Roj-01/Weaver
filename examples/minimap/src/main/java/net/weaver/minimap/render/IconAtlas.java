package net.weaver.minimap.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.weaver.api.game.client.texture.DynamicTextureHandle;
import net.weaver.api.game.client.texture.TextureOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime-generated icon atlas.
 *
 * <p>Every icon is rasterised at 32x32 logical pixels with 4x supersampling and
 * packed into one white atlas texture. The atlas is tinted per draw call with
 * the extended {@code blit} colour argument, so one texture serves every theme
 * colour. Creation happens on first use; failures are logged once and drawing
 * falls back to simple geometric primitives.</p>
 */
public final class IconAtlas
{
    /** The icon catalogue. The ordinal selects the atlas cell. */
    public enum Icon
    {
        CLOSE,
        SETTINGS,
        PLUS,
        TARGET,
        LAYERS,
        SEARCH,
        CHEVRON_LEFT,
        CHEVRON_RIGHT,
        CHEVRON_DOWN,
        CHEVRON_UP,
        EYE,
        EYE_OFF,
        TRASH,
        PENCIL,
        FILTER,
        SORT,
        COMPASS,
        ZOOM_IN,
        ZOOM_OUT,
        GRID,
        CAVE,
        PLAYER,
        SKULL,
        HOME,
        PORTAL,
        VILLAGE,
        RESOURCE,
        STAR,
        PIN,
        WAYPOINT,
        DEATH,
        ENTITY_HOSTILE,
        ENTITY_PASSIVE,
        ENTITY_ITEM,
        ENTITY_PLAYER,
        WORLD,
        SHIELD,
        TIME,
        BIOME,
        DIMENSION,
        CHECK,
        INFO,
        WARNING
    }

    private static final int CELL = 32;
    private static final int COLUMNS = 8;
    private static final int ROWS = (Icon.values().length + COLUMNS - 1) / COLUMNS;
    private static final int ATLAS_WIDTH = COLUMNS * CELL;
    private static final int ATLAS_HEIGHT = ROWS * CELL;
    private static final int SUPERSAMPLE = 4;
    private static final String MOD_ID = "weaver-minimap";
    private static final String ATLAS_NAME = "ui_icons";

    private static DynamicTextureHandle handle;
    private static boolean failed;
    private static boolean loggedFailure;

    private IconAtlas()
    {
    }

    /** The rendered atlas width in pixels. */
    public static int atlasWidth()
    {
        return ATLAS_WIDTH;
    }

    /** The rendered atlas height in pixels. */
    public static int atlasHeight()
    {
        return ATLAS_HEIGHT;
    }

    /** Whether a live GPU atlas is available. */
    public static boolean isReady()
    {
        return handle != null;
    }

    /**
     * Creates the atlas if the client is running and no atlas exists yet.
     *
     * <p>Must be called on the client/render thread.</p>
     */
    public static void ensureInitialized()
    {
        if (handle != null || failed)
        {
            return;
        }
        try
        {
            DynamicTextureHandle created = DynamicTextureHandle.create(MOD_ID, ATLAS_NAME,
                    ATLAS_WIDTH, ATLAS_HEIGHT, TextureOptions.linearClamp());
            int[] pixels = buildAtlas();
            created.setPixels(pixels);
            created.upload();
            handle = created;
        }
        catch (Throwable failure)
        {
            failed = true;
            if (!loggedFailure)
            {
                loggedFailure = true;
                System.err.println("[weaver-minimap] icon atlas unavailable, using primitives: "
                        + failure);
            }
        }
    }

    /** Releases the atlas texture. The next draw rebuilds it. */
    public static void reset()
    {
        DynamicTextureHandle previous = handle;
        handle = null;
        failed = false;
        loggedFailure = false;
        if (previous != null)
        {
            try
            {
                previous.close();
            }
            catch (Throwable ignored)
            {
                // The client may already be shutting down.
            }
        }
    }

    /**
     * Draws one icon.
     *
     * @param graphics the graphics sink
     * @param icon     the icon, never {@code null}
     * @param x        the left edge
     * @param y        the top edge
     * @param size     the drawn size in pixels
     * @param argb     the tint colour
     */
    public static void draw(GuiGraphicsExtractor graphics, Icon icon, int x, int y, int size,
                            int argb)
    {
        if (graphics == null || icon == null || size <= 0 || (argb >>> 24) == 0)
        {
            return;
        }
        ensureInitialized();
        DynamicTextureHandle current = handle;
        if (current == null)
        {
            drawFallback(graphics, x, y, size, argb);
            return;
        }
        int cellX = (icon.ordinal() % COLUMNS) * CELL;
        int cellY = (icon.ordinal() / COLUMNS) * CELL;
        try
        {
            graphics.blit(RenderPipelines.GUI_TEXTURED, current.id(), x, y,
                    cellX, cellY, size, size, CELL, CELL, ATLAS_WIDTH, ATLAS_HEIGHT, argb);
        }
        catch (RuntimeException failure)
        {
            failed = true;
            handle = null;
            try
            {
                current.close();
            }
            catch (Throwable ignored)
            {
                // The texture may already be gone.
            }
            if (!loggedFailure)
            {
                loggedFailure = true;
                System.err.println("[weaver-minimap] icon atlas draw failed: " + failure);
            }
            drawFallback(graphics, x, y, size, argb);
        }
    }

    private static void drawFallback(GuiGraphicsExtractor graphics, int x, int y, int size,
                                     int argb)
    {
        UiDraw.roundRectBorder(graphics, x, y, size, size, Math.max(1, size / 4), 1, argb);
        int dot = Math.max(1, size / 4);
        UiDraw.roundRect(graphics, x + (size - dot) / 2, y + (size - dot) / 2, dot, dot,
                dot / 2, argb);
    }

    private static int[] buildAtlas()
    {
        int[] pixels = new int[ATLAS_WIDTH * ATLAS_HEIGHT];
        Icon[] icons = Icon.values();
        for (int index = 0; index < icons.length; index++)
        {
            byte[] mask = rasterize(icons[index]);
            int originX = (index % COLUMNS) * CELL;
            int originY = (index / COLUMNS) * CELL;
            for (int row = 0; row < CELL; row++)
            {
                for (int column = 0; column < CELL; column++)
                {
                    int coverage = mask[row * CELL + column] & 0xFF;
                    int pixel = (coverage << 24) | 0x00FFFFFF;
                    pixels[(originY + row) * ATLAS_WIDTH + originX + column] = pixel;
                }
            }
        }
        return pixels;
    }

    private static byte[] rasterize(Icon icon)
    {
        Art art = art(icon);
        byte[] mask = new byte[CELL * CELL];
        int samples = SUPERSAMPLE * SUPERSAMPLE;
        for (int row = 0; row < CELL; row++)
        {
            for (int column = 0; column < CELL; column++)
            {
                int hits = 0;
                for (int sampleY = 0; sampleY < SUPERSAMPLE; sampleY++)
                {
                    double y = row + (sampleY + 0.5D) / SUPERSAMPLE;
                    for (int sampleX = 0; sampleX < SUPERSAMPLE; sampleX++)
                    {
                        double x = column + (sampleX + 0.5D) / SUPERSAMPLE;
                        if (art.contains(x, y))
                        {
                            hits++;
                        }
                    }
                }
                mask[row * CELL + column] = (byte) (hits * 255 / samples);
            }
        }
        return mask;
    }

    private static Art art(Icon icon)
    {
        Art art = new Art();
        switch (icon)
        {
            case CLOSE ->
            {
                art.add(line(10, 10, 22, 22, 3));
                art.add(line(22, 10, 10, 22, 3));
            }
            case SETTINGS ->
            {
                art.add(ring(16, 16, 6.5D, 2.6D));
                for (int index = 0; index < 8; index++)
                {
                    double angle = Math.toRadians(index * 45.0D);
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    art.add(line(16 + cos * 8.5D, 16 + sin * 8.5D,
                            16 + cos * 11.5D, 16 + sin * 11.5D, 3.0D));
                }
                art.add(circle(16, 16, 2.2D));
            }
            case PLUS ->
            {
                art.add(line(10, 16, 22, 16, 3.2D));
                art.add(line(16, 10, 16, 22, 3.2D));
            }
            case TARGET ->
            {
                art.add(ring(16, 16, 8, 2.2D));
                art.add(circle(16, 16, 2.4D));
                for (int index = 0; index < 4; index++)
                {
                    double angle = Math.toRadians(index * 90.0D);
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    art.add(line(16 + cos * 11.0D, 16 + sin * 11.0D,
                            16 + cos * 14.5D, 16 + sin * 14.5D, 2.0D));
                }
            }
            case LAYERS ->
            {
                art.add(line(7, 12, 16, 7, 2.4D));
                art.add(line(16, 7, 25, 12, 2.4D));
                art.add(line(7, 18, 16, 13, 2.4D));
                art.add(line(16, 13, 25, 18, 2.4D));
                art.add(line(7, 24, 16, 19, 2.4D));
                art.add(line(16, 19, 25, 24, 2.4D));
            }
            case SEARCH ->
            {
                art.add(ring(14, 14, 6.5D, 2.8D));
                art.add(line(19, 19, 25.5D, 25.5D, 3.2D));
            }
            case CHEVRON_LEFT ->
            {
                art.add(line(20, 9, 12, 16, 3));
                art.add(line(12, 16, 20, 23, 3));
                art.add(circle(12, 16, 1.5D));
            }
            case CHEVRON_RIGHT ->
            {
                art.add(line(12, 9, 20, 16, 3));
                art.add(line(20, 16, 12, 23, 3));
                art.add(circle(20, 16, 1.5D));
            }
            case CHEVRON_DOWN ->
            {
                art.add(line(9, 13, 16, 20, 3));
                art.add(line(16, 20, 23, 13, 3));
                art.add(circle(16, 20, 1.5D));
            }
            case CHEVRON_UP ->
            {
                art.add(line(9, 19, 16, 12, 3));
                art.add(line(16, 12, 23, 19, 3));
                art.add(circle(16, 12, 1.5D));
            }
            case EYE ->
            {
                art.add(ellipseRing(16, 16, 10.8D, 6.4D, 2.2D));
                art.add(circle(16, 16, 2.6D));
            }
            case EYE_OFF ->
            {
                art.add(ellipseRing(16, 16, 10.8D, 6.4D, 2.2D));
                art.add(circle(16, 16, 2.6D));
                art.add(line(8, 8, 24, 24, 2.6D));
            }
            case TRASH ->
            {
                art.add(line(8, 9, 24, 9, 2.4D));
                art.add(line(13, 6, 19, 6, 2.4D));
                art.add(line(10, 10, 10, 26, 2.0D));
                art.add(line(22, 10, 22, 26, 2.0D));
                art.add(line(10, 27, 22, 27, 2.0D));
                art.add(line(14, 14, 14, 23, 1.6D));
                art.add(line(18, 14, 18, 23, 1.6D));
            }
            case PENCIL ->
            {
                art.add(line(11, 21, 21, 9, 5.0D));
                art.add(polygon(7.5D, 25.0D, 12.0D, 23.5D, 9.0D, 20.0D));
                art.add(circle(22, 8, 2.5D));
            }
            case FILTER ->
            {
                art.add(polygon(7, 9, 25, 9, 18, 17.5D, 18, 25, 14, 25, 14, 17.5D));
            }
            case SORT ->
            {
                art.add(line(11, 8, 11, 23, 2.0D));
                art.add(line(7, 12, 11, 8, 2.0D));
                art.add(line(11, 8, 15, 12, 2.0D));
                art.add(line(21, 24, 21, 9, 2.0D));
                art.add(line(17, 20, 21, 24, 2.0D));
                art.add(line(21, 24, 25, 20, 2.0D));
            }
            case COMPASS ->
            {
                art.add(ring(16, 16, 10, 2.2D));
                art.add(polygon(16, 6.5D, 19.5D, 16, 16, 25.5D, 12.5D, 16));
                art.cut(circle(16, 16, 1.6D));
            }
            case ZOOM_IN ->
            {
                art.add(ring(14, 14, 7, 2.6D));
                art.add(line(19.5D, 19.5D, 25, 25, 3.0D));
                art.add(line(10, 14, 18, 14, 2.2D));
                art.add(line(14, 10, 14, 18, 2.2D));
            }
            case ZOOM_OUT ->
            {
                art.add(ring(14, 14, 7, 2.6D));
                art.add(line(19.5D, 19.5D, 25, 25, 3.0D));
                art.add(line(10, 14, 18, 14, 2.2D));
            }
            case GRID ->
            {
                for (int row = 0; row < 3; row++)
                {
                    for (int column = 0; column < 3; column++)
                    {
                        art.add(roundedRect(5 + column * 9, 5 + row * 9, 6, 6, 1.5D));
                    }
                }
            }
            case CAVE ->
            {
                art.add(ring(16, 20, 10, 2.6D));
                art.cut(rect(0, 20, 32, 12));
                art.add(line(5, 28, 27, 28, 2.0D));
            }
            case PLAYER ->
            {
                art.add(circle(16, 10.5D, 4.2D));
                art.add(ellipse(16, 25, 8.5D, 7.0D));
                art.cut(rect(0, 26, 32, 6));
            }
            case SKULL ->
            {
                art.add(circle(16, 14, 8.8D));
                art.cut(circle(12.5D, 13, 2.2D));
                art.cut(circle(19.5D, 13, 2.2D));
                art.cut(polygon(16, 15.5D, 14.5D, 19, 17.5D, 19));
                art.add(roundedRect(10, 19.5D, 12, 7, 3));
                art.cut(line(12.5D, 23, 12.5D, 27, 1.0D));
                art.cut(line(16, 23, 16, 27, 1.0D));
                art.cut(line(19.5D, 23, 19.5D, 27, 1.0D));
            }
            case HOME ->
            {
                art.add(polygon(5, 15, 16, 6, 27, 15));
                art.add(line(8, 15, 8, 28, 2.0D));
                art.add(line(24, 15, 24, 28, 2.0D));
                art.add(line(8, 28, 24, 28, 2.0D));
                art.add(line(8, 16, 24, 16, 1.6D));
                art.add(line(13.5D, 21, 13.5D, 28, 1.2D));
                art.add(line(18.5D, 21, 18.5D, 28, 1.2D));
                art.add(line(13.5D, 21, 18.5D, 21, 1.2D));
            }
            case PORTAL ->
            {
                art.add(ellipseRing(16, 15, 6.5D, 10.0D, 2.4D));
                art.add(circle(5, 6, 1.5D));
                art.add(circle(27, 6, 1.5D));
                art.add(circle(5, 25, 1.5D));
                art.add(circle(27, 25, 1.5D));
            }
            case VILLAGE ->
            {
                art.add(polygon(4, 15, 10, 9, 16, 15));
                art.add(line(5, 15, 5, 26, 1.6D));
                art.add(line(15, 15, 15, 26, 1.6D));
                art.add(line(5, 26, 15, 26, 1.6D));
                art.add(line(9, 20, 9, 26, 1.2D));
                art.add(polygon(15, 20, 21, 14, 27, 20));
                art.add(line(16, 20, 16, 28, 1.6D));
                art.add(line(26, 20, 26, 28, 1.6D));
                art.add(line(16, 28, 26, 28, 1.6D));
                art.add(line(4, 28, 27, 28, 1.4D));
            }
            case RESOURCE ->
            {
                art.add(line(16, 5, 26, 12, 2.2D));
                art.add(line(26, 12, 23, 25, 2.2D));
                art.add(line(23, 25, 9, 25, 2.2D));
                art.add(line(9, 25, 6, 12, 2.2D));
                art.add(line(6, 12, 16, 5, 2.2D));
                art.add(line(16, 5, 16, 25, 1.2D));
                art.add(line(6, 12, 16, 16, 1.2D));
                art.add(line(26, 12, 16, 16, 1.2D));
            }
            case STAR ->
            {
                art.add(star(16, 16, 12, 5, 5, -90.0D));
            }
            case PIN ->
            {
                art.add(ring(16, 12, 6, 2.6D));
                art.add(polygon(11, 16.5D, 21, 16.5D, 16, 27));
                art.add(circle(16, 12, 2.0D));
            }
            case WAYPOINT ->
            {
                art.add(line(9, 5, 9, 28, 2.2D));
                art.add(polygon(10, 6, 25, 10, 10, 16));
                art.add(line(5, 28, 13, 28, 2.0D));
            }
            case DEATH ->
            {
                art.add(circle(16, 12, 8));
                art.cut(circle(12.5D, 11, 2.1D));
                art.cut(circle(19.5D, 11, 2.1D));
                art.cut(polygon(16, 14, 14.5D, 17, 17.5D, 17));
                art.add(line(7, 20, 25, 29, 2.0D));
                art.add(line(25, 20, 7, 29, 2.0D));
            }
            case ENTITY_HOSTILE ->
            {
                art.add(line(8, 24, 24, 8, 2.6D));
                art.add(line(8, 8, 24, 24, 2.6D));
                art.add(circle(7, 25, 2.0D));
                art.add(circle(25, 25, 2.0D));
                art.add(circle(7, 7, 1.5D));
                art.add(circle(25, 7, 1.5D));
            }
            case ENTITY_PASSIVE ->
            {
                art.add(ellipse(16, 21, 6.5D, 5.5D));
                art.add(circle(9, 12.5D, 3.0D));
                art.add(circle(14.5D, 9, 3.0D));
                art.add(circle(21.5D, 9, 3.0D));
                art.add(circle(25, 12.5D, 3.0D));
            }
            case ENTITY_ITEM ->
            {
                art.add(line(16, 4, 27, 10, 2.2D));
                art.add(line(27, 10, 27, 22, 2.2D));
                art.add(line(27, 22, 16, 28, 2.2D));
                art.add(line(16, 28, 5, 22, 2.2D));
                art.add(line(5, 22, 5, 10, 2.2D));
                art.add(line(5, 10, 16, 4, 2.2D));
                art.add(line(16, 4, 16, 15, 1.5D));
                art.add(line(16, 15, 5, 21, 1.5D));
                art.add(line(16, 15, 27, 21, 1.5D));
            }
            case ENTITY_PLAYER ->
            {
                art.add(circle(16, 10, 4.2D));
                art.add(ring(16, 10, 7.5D, 1.4D));
                art.add(ellipse(16, 25, 8.5D, 7.0D));
                art.cut(rect(0, 26, 32, 6));
            }
            case WORLD ->
            {
                art.add(ring(16, 16, 10, 2.0D));
                art.add(ellipseRing(16, 16, 5.0D, 10.0D, 1.5D));
                art.add(line(6, 16, 26, 16, 1.5D));
                art.add(line(8, 11, 24, 11, 1.2D));
                art.add(line(8, 21, 24, 21, 1.2D));
            }
            case SHIELD ->
            {
                art.add(line(16, 4, 26, 8, 2.2D));
                art.add(line(26, 8, 24, 20, 2.2D));
                art.add(line(24, 20, 16, 28, 2.2D));
                art.add(line(16, 28, 8, 20, 2.2D));
                art.add(line(8, 20, 6, 8, 2.2D));
                art.add(line(6, 8, 16, 4, 2.2D));
                art.add(line(11, 16, 15, 20, 2.6D));
                art.add(line(15, 20, 22, 11, 2.6D));
            }
            case TIME ->
            {
                art.add(ring(16, 16, 10, 2.2D));
                art.add(line(16, 16, 16, 9, 2.0D));
                art.add(line(16, 16, 22, 19, 2.0D));
                art.add(circle(16, 16, 1.5D));
            }
            case BIOME ->
            {
                art.add(polygon(16, 4, 26, 21, 6, 21));
                art.add(line(16, 20, 16, 28, 2.0D));
                art.add(line(11, 26, 21, 26, 1.5D));
            }
            case DIMENSION ->
            {
                art.add(line(11, 6, 20, 12, 1.8D));
                art.add(line(20, 12, 11, 18, 1.8D));
                art.add(line(11, 18, 2, 12, 1.8D));
                art.add(line(2, 12, 11, 6, 1.8D));
                art.add(line(21, 14, 30, 20, 1.8D));
                art.add(line(30, 20, 21, 26, 1.8D));
                art.add(line(21, 26, 12, 20, 1.8D));
                art.add(line(12, 20, 21, 14, 1.8D));
            }
            case CHECK ->
            {
                art.add(line(9, 16, 14, 22, 3.0D));
                art.add(line(14, 22, 23, 10, 3.0D));
            }
            case INFO ->
            {
                art.add(ring(16, 16, 10, 2.2D));
                art.add(circle(16, 10, 1.6D));
                art.add(line(16, 14, 16, 23, 2.4D));
            }
            case WARNING ->
            {
                art.add(line(16, 5, 28, 27, 2.4D));
                art.add(line(28, 27, 4, 27, 2.4D));
                art.add(line(4, 27, 16, 5, 2.4D));
                art.add(line(16, 12, 16, 20, 2.2D));
                art.add(circle(16, 23.5D, 1.6D));
            }
        }
        return art;
    }

    private static Shape line(double x1, double y1, double x2, double y2, double thickness)
    {
        return capsule(x1, y1, x2, y2, thickness);
    }

    private static Shape circle(double centerX, double centerY, double radius)
    {
        double squared = radius * radius;
        return (x, y) ->
        {
            double dx = x - centerX;
            double dy = y - centerY;
            return dx * dx + dy * dy <= squared;
        };
    }

    private static Shape ellipse(double centerX, double centerY, double radiusX,
                                 double radiusY)
    {
        return (x, y) ->
        {
            double dx = (x - centerX) / radiusX;
            double dy = (y - centerY) / radiusY;
            return dx * dx + dy * dy <= 1.0D;
        };
    }

    private static Shape ring(double centerX, double centerY, double radius, double thickness)
    {
        double inner = Math.max(0.0D, radius - thickness / 2.0D);
        double outer = radius + thickness / 2.0D;
        double innerSquared = inner * inner;
        double outerSquared = outer * outer;
        return (x, y) ->
        {
            double dx = x - centerX;
            double dy = y - centerY;
            double distance = dx * dx + dy * dy;
            return distance >= innerSquared && distance <= outerSquared;
        };
    }

    private static Shape ellipseRing(double centerX, double centerY, double radiusX,
                                     double radiusY, double thickness)
    {
        double band = thickness / Math.min(radiusX, radiusY) / 2.0D;
        return (x, y) ->
        {
            double dx = (x - centerX) / radiusX;
            double dy = (y - centerY) / radiusY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            return Math.abs(distance - 1.0D) <= band;
        };
    }

    private static Shape capsule(double x1, double y1, double x2, double y2, double thickness)
    {
        double radius = thickness / 2.0D;
        double radiusSquared = radius * radius;
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;
        return (x, y) ->
        {
            double t = lengthSquared <= 0.0D
                    ? 0.0D
                    : ((x - x1) * dx + (y - y1) * dy) / lengthSquared;
            t = t < 0.0D ? 0.0D : Math.min(t, 1.0D);
            double closestX = x1 + dx * t;
            double closestY = y1 + dy * t;
            double distanceX = x - closestX;
            double distanceY = y - closestY;
            return distanceX * distanceX + distanceY * distanceY <= radiusSquared;
        };
    }

    private static Shape roundedRect(double x, double y, double width, double height,
                                     double radius)
    {
        double r = Math.max(0.0D, Math.min(radius, Math.min(width, height) / 2.0D));
        double radiusSquared = r * r;
        return (px, py) ->
        {
            double localX = px - x;
            double localY = py - y;
            if (localX < 0.0D || localY < 0.0D || localX > width || localY > height)
            {
                return false;
            }
            if ((localX >= r && localX <= width - r) || (localY >= r && localY <= height - r))
            {
                return true;
            }
            double cornerX = localX < r ? r : width - r;
            double cornerY = localY < r ? r : height - r;
            double dx = localX - cornerX;
            double dy = localY - cornerY;
            return dx * dx + dy * dy <= radiusSquared;
        };
    }

    private static Shape rect(double x, double y, double width, double height)
    {
        return (px, py) -> px >= x && py >= y && px <= x + width && py <= y + height;
    }

    private static Shape polygon(double... points)
    {
        double[] coordinates = points.clone();
        return (x, y) ->
        {
            boolean inside = false;
            for (int current = 0, previous = coordinates.length - 2;
                 current < coordinates.length;
                 previous = current, current += 2)
            {
                double currentX = coordinates[current];
                double currentY = coordinates[current + 1];
                double previousX = coordinates[previous];
                double previousY = coordinates[previous + 1];
                boolean crosses = (currentY > y) != (previousY > y)
                        && x < (previousX - currentX) * (y - currentY)
                        / (previousY - currentY) + currentX;
                if (crosses)
                {
                    inside = !inside;
                }
            }
            return inside;
        };
    }

    private static Shape star(double centerX, double centerY, double outerRadius,
                              double innerRadius, int points, double rotationDegrees)
    {
        int vertices = points * 2;
        double[] coordinates = new double[vertices * 2];
        for (int index = 0; index < vertices; index++)
        {
            double radius = (index & 1) == 0 ? outerRadius : innerRadius;
            double angle = Math.toRadians(rotationDegrees + index * (180.0D / points));
            coordinates[index * 2] = centerX + Math.cos(angle) * radius;
            coordinates[index * 2 + 1] = centerY + Math.sin(angle) * radius;
        }
        return polygon(coordinates);
    }

    private interface Shape
    {
        boolean contains(double x, double y);
    }

    private static final class Art
    {
        private final List<Shape> shapes = new ArrayList<>(8);
        private final List<Boolean> subtract = new ArrayList<>(2);

        private Art add(Shape shape)
        {
            shapes.add(shape);
            subtract.add(Boolean.FALSE);
            return this;
        }

        private Art cut(Shape shape)
        {
            shapes.add(shape);
            subtract.add(Boolean.TRUE);
            return this;
        }

        private boolean contains(double x, double y)
        {
            boolean inside = false;
            for (int index = 0; index < shapes.size(); index++)
            {
                if (shapes.get(index).contains(x, y))
                {
                    inside = !subtract.get(index);
                }
            }
            return inside;
        }
    }
}
