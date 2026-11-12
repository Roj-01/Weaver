package net.weaver.minimap.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.weaver.api.game.client.texture.DynamicTextureHandle;
import net.weaver.api.game.client.texture.TextureOptions;
import net.weaver.minimap.map.CaveSlice;
import net.weaver.minimap.map.MapManager;
import net.weaver.minimap.map.MapStats;
import net.weaver.minimap.map.RegionData;
import net.weaver.minimap.map.RegionKey;

import java.util.HashSet;
import java.util.Set;

/**
 * CPU-composites the HUD minimap into a single round-masked texture.
 *
 * <p>The region textures used by the fullscreen map cannot be clipped to a
 * circle or a rounded rectangle with the 26.3 GUI primitives. The HUD therefore
 * samples the block data directly into a small ARGB buffer, applies the shape
 * mask with one-pixel anti-aliasing, and uploads one texture per recomposition.
 * The buffer is only rebuilt when the player moves, turns, zooms, changes cave
 * level, or map data advances; a stationary HUD reuses the uploaded texture.</p>
 *
 * <p>All methods run on the render thread.</p>
 */
public final class HudMapCompositor implements AutoCloseable
{
    private static final int MAX_SIZE = 384;
    private static final double MOVE_EPSILON = 0.06D;
    private static final double ZOOM_EPSILON = 0.008D;
    private static final double ROTATION_EPSILON = 0.2D;

    private int size;
    private int[] pixels = new int[0];
    private DynamicTextureHandle texture;
    private boolean failed;
    private boolean loggedFailure;

    private long lastComposeNanos;
    private double lastCenterX = Double.NaN;
    private double lastCenterZ = Double.NaN;
    private double lastZoom = Double.NaN;
    private double lastRotation = Double.NaN;
    private int lastCaveY = Integer.MIN_VALUE;
    private long lastEpoch = Long.MIN_VALUE;
    private long lastDataVersion = Long.MIN_VALUE;
    private boolean lastCave;
    private boolean lastRounded;

    /**
     * Draws the HUD map.
     *
     * @param graphics    the graphics sink
     * @param maps        the map manager
     * @param dimension   the current dimension id
     * @param view        the rotated map view
     * @param cave        whether cave data should be sampled
     * @param caveY       the cave Y, ignored when {@code cave} is false
     * @param rounded     {@code true} for a rounded square, {@code false} for a circle
     * @param emptyArgb   the colour used where no map data exists
     * @param force       force a recomposition (for example after a resize)
     * @return {@code true} when a map texture was drawn
     */
    public boolean draw(GuiGraphicsExtractor graphics, MapManager maps, String dimension,
                        MapView view, boolean cave, int caveY, boolean rounded,
                        int emptyArgb, boolean force)
    {
        if (graphics == null || maps == null || dimension == null || view == null)
        {
            return false;
        }
        int wanted = Math.max(1, Math.min(MAX_SIZE, view.width()));
        if (wanted != size)
        {
            resize(wanted);
            force = true;
        }
        if (texture == null)
        {
            return false;
        }
        if (texture.height() != wanted)
        {
            resize(wanted);
            force = true;
        }
        long now = System.nanoTime();
        if (force || needsCompose(maps, view, cave, caveY, rounded, now))
        {
            compose(maps, dimension, view, cave, caveY, rounded, emptyArgb);
            remember(maps, view, cave, caveY, rounded);
            lastComposeNanos = now;
        }
        graphics.blit(texture.id(), view.originX(), view.originY(),
                view.originX() + wanted, view.originY() + wanted, 0.0F, 1.0F, 0.0F, 1.0F);
        return true;
    }

    /** Releases the GPU texture. */
    @Override
    public void close()
    {
        if (texture != null)
        {
            try
            {
                texture.close();
            }
            catch (RuntimeException ignored)
            {
                // The texture manager may already be shutting down.
            }
            texture = null;
        }
        failed = false;
        loggedFailure = false;
        lastCenterX = Double.NaN;
        lastEpoch = Long.MIN_VALUE;
        lastDataVersion = Long.MIN_VALUE;
    }

    private boolean needsCompose(MapManager maps, MapView view, boolean cave, int caveY,
                                 boolean rounded, long now)
    {
        if (Double.isNaN(lastCenterX))
        {
            return true;
        }
        boolean geometry = Math.abs(view.zoom() - lastZoom) > ZOOM_EPSILON
                || angleDistance(view.rotationDegrees(), lastRotation) > ROTATION_EPSILON
                || cave != lastCave
                || (cave && caveY != lastCaveY)
                || rounded != lastRounded
                || maps.epoch() != lastEpoch;
        if (geometry)
        {
            return true;
        }
        if (now - lastComposeNanos < 40_000_000L)
        {
            return false;
        }
        MapStats stats = maps.stats();
        long dataVersion = stats.scannedChunks() + stats.caveSlicesScanned()
                + stats.loadedRegions() + stats.loadedCaveSlices();
        return Math.abs(view.centerX() - lastCenterX) > MOVE_EPSILON
                || Math.abs(view.centerZ() - lastCenterZ) > MOVE_EPSILON
                || dataVersion != lastDataVersion;
    }

    private void remember(MapManager maps, MapView view, boolean cave, int caveY,
                          boolean rounded)
    {
        lastCenterX = view.centerX();
        lastCenterZ = view.centerZ();
        lastZoom = view.zoom();
        lastRotation = view.rotationDegrees();
        lastCave = cave;
        lastCaveY = caveY;
        lastRounded = rounded;
        lastEpoch = maps.epoch();
        MapStats stats = maps.stats();
        lastDataVersion = stats.scannedChunks() + stats.caveSlicesScanned()
                + stats.loadedRegions() + stats.loadedCaveSlices();
    }

    private void compose(MapManager maps, String dimension, MapView view, boolean cave, int caveY,
                         boolean rounded, int emptyArgb)
    {
        int count = size * size;
        if (pixels.length != count)
        {
            pixels = new int[count];
        }
        double cos = view.cos();
        double sin = view.sin();
        double zoom = view.zoom();
        double half = size * 0.5D;
        double radius = half - 1.5D;
        double innerRadius = radius - 0.75D;
        double outerRadius = radius + 0.75D;
        double innerSquared = innerRadius * innerRadius;
        double outerSquared = outerRadius * outerRadius;
        double cornerRadius = rounded ? Math.max(10.0D, size * 0.095D) : 0.0D;
        double cosZoom = cos * zoom;
        double sinZoom = sin * zoom;
        Set<RegionKey> requested = new HashSet<>(8);
        Set<RegionKey> requestedCave = new HashSet<>(8);
        RegionKey lastKey = null;
        RegionData lastRegion = null;
        CaveSlice lastSlice = null;
        int lastRegionX = Integer.MIN_VALUE;
        int lastRegionZ = Integer.MIN_VALUE;
        int fallbackRgb = emptyArgb & 0xFFFFFF;

        for (int row = 0; row < size; row++)
        {
            double screenY = row + 0.5D - half;
            double baseWorldX = view.centerX() + screenY * sinZoom;
            double baseWorldZ = view.centerZ() + screenY * cosZoom;
            int rowOffset = row * size;
            for (int column = 0; column < size; column++)
            {
                double screenX = column + 0.5D - half;
                double distanceSquared = screenX * screenX + screenY * screenY;
                double coverage;
                if (rounded)
                {
                    coverage = roundedCoverage(screenX + half, screenY + half, size,
                            cornerRadius);
                }
                else if (distanceSquared <= innerSquared)
                {
                    coverage = 1.0D;
                }
                else if (distanceSquared >= outerSquared)
                {
                    coverage = 0.0D;
                }
                else
                {
                    coverage = clamp01(radius - Math.sqrt(distanceSquared) + 0.5D);
                }
                if (coverage <= 0.0D)
                {
                    pixels[rowOffset + column] = 0;
                    continue;
                }

                double worldX = baseWorldX + screenX * cosZoom;
                double worldZ = baseWorldZ - screenX * sinZoom;
                int blockX = (int) Math.floor(worldX);
                int blockZ = (int) Math.floor(worldZ);
                int regionX = blockX >> 8;
                int regionZ = blockZ >> 8;
                if (regionX != lastRegionX || regionZ != lastRegionZ)
                {
                    lastRegionX = regionX;
                    lastRegionZ = regionZ;
                    lastKey = RegionKey.ofBlock(dimension, blockX, blockZ);
                    lastRegion = maps.regionIfLoaded(lastKey);
                    if (lastRegion == null && requested.add(lastKey))
                    {
                        maps.requestRegion(lastKey);
                    }
                    lastSlice = null;
                    if (cave && maps.caveEnabled())
                    {
                        lastSlice = maps.caveSlice(lastKey, caveY);
                        if (lastSlice == null && requestedCave.add(lastKey))
                        {
                            maps.requestCaveSlice(lastKey, caveY);
                        }
                    }
                }

                int localX = blockX & 255;
                int localZ = blockZ & 255;
                double sampleX = worldX - (regionX << 8);
                double sampleZ = worldZ - (regionZ << 8);
                int color = 0;
                if (cave && lastSlice != null)
                {
                    color = lastSlice.colorAt(localX, localZ);
                }
                if (color == 0 && lastRegion != null)
                {
                    color = zoom >= 3.0D
                            ? sampleMip(lastRegion, sampleX, sampleZ, fallbackRgb)
                            : sampleRegion(lastRegion, sampleX, sampleZ, fallbackRgb);
                }
                if (color == 0)
                {
                    color = fallbackRgb;
                }

                double edgeSquared = distanceSquared / (radius * radius);
                double vignette = 1.0D - 0.14D * Math.min(1.0D, edgeSquared);
                int red = (int) Math.round(((color >> 16) & 0xFF) * vignette);
                int green = (int) Math.round(((color >> 8) & 0xFF) * vignette);
                int blue = (int) Math.round((color & 0xFF) * vignette);
                int baseAlpha = (color >>> 24);
                if (baseAlpha == 0)
                {
                    baseAlpha = 255;
                }
                int alpha = (int) Math.round(baseAlpha * coverage);
                pixels[rowOffset + column] = (alpha << 24) | (red << 16) | (green << 8) | blue;
            }
        }
        try
        {
            texture.setPixels(pixels);
            texture.upload();
        }
        catch (RuntimeException failure)
        {
            texture = null;
            failed = true;
            if (!loggedFailure)
            {
                loggedFailure = true;
                System.err.println("[weaver-minimap] HUD map upload failed: " + failure);
            }
        }
    }

    /**
     * Bilinear sample of one region at a fractional block coordinate.
     *
     * <p>Nearest sampling turns every zoom level below one block per pixel into
     * a field of hard squares and makes the map crawl as the player walks.
     * Sampling the four texels around the cursor keeps the surface continuous,
     * which is what the eye reads as a map rather than as pixels.</p>
     */
    private static int sampleRegion(RegionData region, double localX, double localZ, int fallback)
    {
        int x0 = (int) Math.floor(localX - 0.5D);
        int z0 = (int) Math.floor(localZ - 0.5D);
        double fx = localX - 0.5D - x0;
        double fz = localZ - 0.5D - z0;
        return blend4(texel(region, x0, z0, fallback), texel(region, x0 + 1, z0, fallback),
                texel(region, x0, z0 + 1, fallback), texel(region, x0 + 1, z0 + 1, fallback),
                fx, fz);
    }

    private static int texel(RegionData region, int x, int z, int fallback)
    {
        int cx = Math.max(0, Math.min(RegionData.BLOCKS_PER_REGION - 1, x));
        int cz = Math.max(0, Math.min(RegionData.BLOCKS_PER_REGION - 1, z));
        int color = region.colorAt(cx, cz);
        return color != 0 ? color : fallback;
    }

    /** Bilinear sample of the 64x64 mip used when zoomed out. */
    private static int sampleMip(RegionData region, double localX, double localZ, int fallback)
    {
        double mx = localX * 0.25D - 0.5D;
        double mz = localZ * 0.25D - 0.5D;
        int x0 = (int) Math.floor(mx);
        int z0 = (int) Math.floor(mz);
        double fx = mx - x0;
        double fz = mz - z0;
        return blend4(mipTexel(region, x0, z0, fallback), mipTexel(region, x0 + 1, z0, fallback),
                mipTexel(region, x0, z0 + 1, fallback), mipTexel(region, x0 + 1, z0 + 1, fallback),
                fx, fz);
    }

    private static int mipTexel(RegionData region, int x, int z, int fallback)
    {
        int cx = Math.max(0, Math.min(RegionData.MIP_SIZE - 1, x));
        int cz = Math.max(0, Math.min(RegionData.MIP_SIZE - 1, z));
        int color = region.mipColorAt(cx, cz);
        return color != 0 ? color : fallback;
    }

    private static int blend4(int c00, int c10, int c01, int c11, double fx, double fz)
    {
        double w00 = (1.0D - fx) * (1.0D - fz);
        double w10 = fx * (1.0D - fz);
        double w01 = (1.0D - fx) * fz;
        double w11 = fx * fz;
        int red = (int) Math.round(((c00 >> 16) & 0xFF) * w00 + ((c10 >> 16) & 0xFF) * w10
                + ((c01 >> 16) & 0xFF) * w01 + ((c11 >> 16) & 0xFF) * w11);
        int green = (int) Math.round(((c00 >> 8) & 0xFF) * w00 + ((c10 >> 8) & 0xFF) * w10
                + ((c01 >> 8) & 0xFF) * w01 + ((c11 >> 8) & 0xFF) * w11);
        int blue = (int) Math.round((c00 & 0xFF) * w00 + (c10 & 0xFF) * w10
                + (c01 & 0xFF) * w01 + (c11 & 0xFF) * w11);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private void resize(int wanted)
    {
        size = wanted;
        pixels = new int[wanted * wanted];
        if (texture != null)
        {
            try
            {
                texture.close();
            }
            catch (RuntimeException ignored)
            {
            }
            texture = null;
        }
        if (failed)
        {
            return;
        }
        try
        {
            texture = DynamicTextureHandle.create("weaver-minimap", "hud_map_" + wanted,
                    wanted, wanted, TextureOptions.linearClamp());
        }
        catch (RuntimeException failure)
        {
            failed = true;
            if (!loggedFailure)
            {
                loggedFailure = true;
                System.err.println("[weaver-minimap] HUD map texture unavailable: " + failure);
            }
        }
    }

    private static double roundedCoverage(double x, double y, int size, double radius)
    {
        double half = size * 0.5D;
        double qx = Math.abs(x - half) - (half - radius);
        double qy = Math.abs(y - half) - (half - radius);
        double outside = Math.sqrt(Math.max(qx, 0.0D) * Math.max(qx, 0.0D)
                + Math.max(qy, 0.0D) * Math.max(qy, 0.0D));
        double distance = outside + Math.min(Math.max(qx, qy), 0.0D) - radius;
        return clamp01(0.5D - distance);
    }

    private static double clamp01(double value)
    {
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }

    private static double angleDistance(double a, double b)
    {
        double difference = Math.abs(a - b) % 360.0D;
        return difference > 180.0D ? 360.0D - difference : difference;
    }
}
