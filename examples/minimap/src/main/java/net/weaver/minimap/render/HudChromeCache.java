package net.weaver.minimap.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.weaver.api.game.client.texture.DynamicTextureHandle;
import net.weaver.api.game.client.texture.TextureOptions;
import net.weaver.minimap.config.MinimapConfig;
import net.weaver.minimap.ui.theme.Theme;

/**
 * Cached HUD chrome texture.
 *
 * <p>The circular glass panel, its soft shadow, hairline border, and top glint
 * are rasterised once into a small ARGB texture and blitted every frame. This
 * replaces roughly a thousand per-frame scanline fills with one draw call,
 * which is the difference between a smooth HUD and a measurable frame cost.
 * The texture is rebuilt only when the size, shape, theme, opacity, or border
 * settings change.</p>
 */
public final class HudChromeCache implements AutoCloseable
{
    private static final int MARGIN = 8;

    private DynamicTextureHandle handle;
    private long signature = Long.MIN_VALUE;
    private int size;
    private boolean rounded;

    /**
     * Draws the chrome behind the map.
     *
     * @param graphics the graphics sink
     * @param theme    the active theme
     * @param config   the live configuration
     * @param x        the map left edge
     * @param y        the map top edge
     * @param size     the map size
     * @param rounded  {@code true} for a rounded square
     */
    public void draw(GuiGraphicsExtractor graphics, Theme theme, MinimapConfig config,
                     int x, int y, int size, boolean rounded)
    {
        if (graphics == null || theme == null || config == null || size <= 0)
        {
            return;
        }
        long next = signature(theme, config, size, rounded);
        if (handle == null || next != signature || this.size != size
                || this.rounded != rounded)
        {
            rebuild(theme, config, size, rounded);
            signature = next;
        }
        if (handle == null)
        {
            return;
        }
        graphics.blit(handle.id(), x - MARGIN, y - MARGIN, x + size + MARGIN,
                y + size + MARGIN, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    /** Releases the GPU texture. */
    @Override
    public void close()
    {
        if (handle != null)
        {
            try
            {
                handle.close();
            }
            catch (RuntimeException ignored)
            {
            }
            handle = null;
        }
        signature = Long.MIN_VALUE;
    }

    private void rebuild(Theme theme, MinimapConfig config, int size, boolean rounded)
    {
        this.size = size;
        this.rounded = rounded;
        int total = size + MARGIN * 2;
        int[] pixels = new int[total * total];
        int half = size / 2;
        double radius = size * 0.5D - 1.5D;
        double cornerRadius = rounded ? Math.max(10.0D, size * 0.095D) : 0.0D;
        int backgroundAlpha = (int) Math.round(
                Math.max(0.0D, Math.min(1.0D, config.backgroundOpacity())) * 255.0D);
        int borderAlpha = (int) Math.round(
                Math.max(0.0D, Math.min(1.0D, config.hudBorderOpacity())) * 255.0D);
        int shadowBase = Math.max(0, Math.min(255, (theme.shadow() >>> 24)));
        int shadowRgb = theme.shadow() & 0xFFFFFF;
        int backgroundRgb = theme.surface() & 0xFFFFFF;
        int borderRgb = theme.border() & 0xFFFFFF;
        int accentRgb = theme.accent() & 0xFFFFFF;
        boolean glint = size >= 96;

        for (int row = 0; row < total; row++)
        {
            double localY = row - MARGIN;
            for (int column = 0; column < total; column++)
            {
                double localX = column - MARGIN;
                double signedDistance = rounded
                        ? roundedDistance(localX, localY, half, cornerRadius)
                        : Math.hypot(localX - half, localY - half) - (size * 0.5D - 1.0D);
                if (signedDistance > MARGIN)
                {
                    continue;
                }
                int result = 0;
                if (config.hudShadow() && signedDistance > 0.0D)
                {
                    double fade = 1.0D - signedDistance / MARGIN;
                    if (fade > 0.0D)
                    {
                        int shadowAlpha = (int) Math.round(shadowBase * 0.45D * fade * fade);
                        result = over(result, shadowRgb, shadowAlpha);
                    }
                }
                double coverage = clamp01(0.5D - signedDistance);
                if (coverage > 0.0D)
                {
                    int alpha = (int) Math.round(backgroundAlpha * coverage);
                    result = over(result, backgroundRgb, alpha);
                    double borderMask = clamp01(1.25D + signedDistance);
                    if (borderMask > 0.0D && borderAlpha > 0)
                    {
                        result = over(result, borderRgb,
                                (int) Math.round(borderAlpha * coverage * borderMask));
                    }
                    if (glint && localY < 2.0D && Math.abs(localX - half) < radius * 0.72D)
                    {
                        result = over(result, accentRgb,
                                (int) Math.round(150 * coverage));
                    }
                }
                pixels[row * total + column] = result;
            }
        }
        DynamicTextureHandle previous = handle;
        handle = null;
        try
        {
            DynamicTextureHandle created = DynamicTextureHandle.create("weaver-minimap",
                    "hud_chrome_" + size, total, total, TextureOptions.linearClamp());
            created.setPixels(pixels);
            created.upload();
            handle = created;
        }
        catch (RuntimeException failure)
        {
            System.err.println("[weaver-minimap] HUD chrome unavailable: " + failure);
        }
        if (previous != null)
        {
            try
            {
                previous.close();
            }
            catch (RuntimeException ignored)
            {
            }
        }
    }

    private static long signature(Theme theme, MinimapConfig config, int size,
                                  boolean rounded)
    {
        long hash = 17L;
        hash = hash * 31L + theme.id().ordinal();
        hash = hash * 31L + theme.surface();
        hash = hash * 31L + theme.border();
        hash = hash * 31L + theme.accent();
        hash = hash * 31L + theme.shadow();
        hash = hash * 31L + (int) Math.round(config.backgroundOpacity() * 255.0D);
        hash = hash * 31L + (int) Math.round(config.hudBorderOpacity() * 255.0D);
        hash = hash * 31L + (config.hudShadow() ? 1 : 0);
        hash = hash * 31L + size;
        hash = hash * 31L + (rounded ? 1 : 0);
        return hash;
    }

    private static double roundedDistance(double x, double y, double half, double radius)
    {
        double qx = Math.abs(x - half) - (half - radius);
        double qy = Math.abs(y - half) - (half - radius);
        double outside = Math.sqrt(Math.max(qx, 0.0D) * Math.max(qx, 0.0D)
                + Math.max(qy, 0.0D) * Math.max(qy, 0.0D));
        return outside + Math.min(Math.max(qx, qy), 0.0D) - radius;
    }

    private static int over(int destination, int sourceRgb, int sourceAlpha)
    {
        int alpha = Math.max(0, Math.min(255, sourceAlpha));
        if (alpha == 0)
        {
            return destination;
        }
        if (alpha == 255 || destination == 0)
        {
            return (alpha << 24) | sourceRgb;
        }
        int destinationAlpha = destination >>> 24;
        int inverse = 255 - alpha;
        int outAlpha = alpha + destinationAlpha * inverse / 255;
        if (outAlpha <= 0)
        {
            return 0;
        }
        int sourceRed = (sourceRgb >> 16) & 0xFF;
        int sourceGreen = (sourceRgb >> 8) & 0xFF;
        int sourceBlue = sourceRgb & 0xFF;
        int destinationRed = (destination >> 16) & 0xFF;
        int destinationGreen = (destination >> 8) & 0xFF;
        int destinationBlue = destination & 0xFF;
        int red = (sourceRed * alpha + destinationRed * destinationAlpha * inverse / 255)
                / outAlpha;
        int green = (sourceGreen * alpha
                + destinationGreen * destinationAlpha * inverse / 255) / outAlpha;
        int blue = (sourceBlue * alpha
                + destinationBlue * destinationAlpha * inverse / 255) / outAlpha;
        return (outAlpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static double clamp01(double value)
    {
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }
}
