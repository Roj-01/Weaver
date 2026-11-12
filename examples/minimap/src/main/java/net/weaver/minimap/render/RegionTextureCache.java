package net.weaver.minimap.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.weaver.api.game.client.texture.DynamicTextureHandle;
import net.weaver.api.game.client.texture.TextureOptions;
import net.weaver.minimap.map.CaveSlice;
import net.weaver.minimap.map.CaveSliceKey;
import net.weaver.minimap.map.MapManager;
import net.weaver.minimap.map.MinimapWorlds;
import net.weaver.minimap.map.RegionData;
import net.weaver.minimap.map.RegionKey;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns the GPU textures used by the fullscreen map.
 *
 * <p>Surface regions have a 256x256 base texture and a 64x64 mip texture. The
 * renderer picks the mip when the region is drawn small, which removes the
 * shimmer that nearest-neighbour downsampling produces at wide zoom levels.
 * Cave slices use their own texture cache and an alpha tint so the cave
 * transparency setting is applied at draw time.</p>
 *
 * <p>Textures are created lazily, evicted least-recently-used, and uploaded
 * within a per-frame budget. The cache never performs disk I/O on the render
 * thread: it only reads regions that the map manager has already loaded and
 * asks the manager to queue the rest.</p>
 */
public final class RegionTextureCache implements AutoCloseable
{
    private static final int MAX_TEXTURES = 96;
    private static final int MAX_CAVE_TEXTURES = 48;

    private final Map<RegionKey, SurfaceEntry> surface =
            new LinkedHashMap<>(32, 0.75F, true);
    private final Map<CaveSliceKey, CaveEntry> cave =
            new LinkedHashMap<>(16, 0.75F, true);
    private int uploadBudget;
    private boolean loggedTexture;

    /** Starts a frame with an upload budget. */
    public void beginFrame(int uploads)
    {
        uploadBudget = Math.max(0, uploads);
    }

    /** Ends a frame. */
    public void endFrame()
    {
        uploadBudget = 0;
    }

    /**
     * Draws one surface region.
     *
     * @return {@code true} when a texture was drawn
     */
    public boolean drawSurface(GuiGraphicsExtractor graphics, MapManager maps, RegionKey key,
                               int x, int y, int size)
    {
        return drawSurfaceInternal(graphics, maps, key, x, y, size, -1);
    }

    /**
     * Draws a surface region with an opacity tint.
     *
     * <p>Used by the cave mode surface overlay so the cave layer stays the
     * dominant reading while the surface remains a faint reference.</p>
     *
     * @param opacity the overlay opacity, from 0 to 1
     * @return {@code true} when a texture was drawn
     */
    public boolean drawSurfaceOverlay(GuiGraphicsExtractor graphics, MapManager maps,
                                      RegionKey key, int x, int y, int size, double opacity)
    {
        int alpha = (int) Math.round(Math.max(0.0D, Math.min(1.0D, opacity)) * 255.0D);
        return drawSurfaceInternal(graphics, maps, key, x, y, size,
                (alpha << 24) | 0xFFFFFF);
    }

    private boolean drawSurfaceInternal(GuiGraphicsExtractor graphics, MapManager maps,
                                        RegionKey key, int x, int y, int size, int tint)
    {
        if (graphics == null || maps == null || key == null || size <= 0)
        {
            return false;
        }
        RegionData data = maps.regionIfLoaded(key);
        if (data == null)
        {
            maps.requestRegion(key);
            return false;
        }
        if (data.isEmpty())
        {
            return false;
        }
        boolean useMip = size < 140;
        SurfaceEntry entry = surface.get(key);
        if (entry == null)
        {
            entry = new SurfaceEntry();
            surface.put(key, entry);
            evictSurface(key);
        }
        if (maps.isTextureDirty(key))
        {
            entry.baseDirty = true;
            entry.mipDirty = true;
        }
        DynamicTextureHandle handle = useMip ? entry.mip : entry.base;
        if (handle == null && uploadBudget > 0)
        {
            handle = createSurface(key, useMip ? 64 : 256);
            if (handle != null)
            {
                uploadBudget--;
                if (useMip)
                {
                    entry.mip = handle;
                    entry.mipDirty = true;
                }
                else
                {
                    entry.base = handle;
                    entry.baseDirty = true;
                }
            }
        }
        if (handle != null && uploadBudget > 0)
        {
            boolean dirty = useMip ? (entry.mipDirty || data.isMipDirty())
                    : entry.baseDirty;
            if (dirty)
            {
                try
                {
                    handle.setPixels(useMip ? data.mip64() : data.colors());
                    handle.upload();
                    uploadBudget--;
                    if (useMip)
                    {
                        // The base texture has not caught up with this upload,
                        // so keep it flagged as stale; the manager's dirty flag
                        // belongs to the region itself and can be cleared now,
                        // otherwise it would force a re-upload every frame.
                        entry.mipDirty = false;
                        entry.baseDirty = true;
                    }
                    else
                    {
                        entry.baseDirty = false;
                    }
                    maps.clearTextureDirty(key);
                }
                catch (RuntimeException failure)
                {
                    closeSurface(entry);
                }
            }
        }
        DynamicTextureHandle draw = useMip ? entry.mip : entry.base;
        if (draw == null)
        {
            draw = entry.base != null ? entry.base : entry.mip;
        }
        if (draw == null)
        {
            return false;
        }
        if (!loggedTexture)
        {
            loggedTexture = true;
            System.out.println("[weaver-minimap] TEXTURE ok " + key.fileName());
        }
        if (tint == -1)
        {
            graphics.blit(draw.id(), x, y, x + size, y + size, 0.0F, 1.0F, 0.0F, 1.0F);
        }
        else
        {
            int textureSize = useMip ? 64 : 256;
            graphics.blit(RenderPipelines.GUI_TEXTURED, draw.id(), x, y, 0.0F, 0.0F,
                    size, size, textureSize, textureSize, tint);
        }
        return true;
    }

    /**
     * Draws one cave slice.
     *
     * @param alpha the cave transparency, from 0 to 1
     * @return {@code true} when a texture was drawn
     */
    public boolean drawCave(GuiGraphicsExtractor graphics, MapManager maps, RegionKey key,
                            int caveY, int x, int y, int size, double alpha)
    {
        if (graphics == null || maps == null || key == null || size <= 0)
        {
            return false;
        }
        CaveSlice slice = maps.caveSlice(key, caveY);
        if (slice == null)
        {
            maps.requestCaveSlice(key, caveY);
            return false;
        }
        if (slice.isEmpty())
        {
            return false;
        }
        CaveSliceKey sliceKey = new CaveSliceKey(key, caveY);
        CaveEntry entry = cave.get(sliceKey);
        if (entry == null)
        {
            entry = new CaveEntry();
            cave.put(sliceKey, entry);
            evictCave(sliceKey);
        }
        if (entry.handle == null && uploadBudget > 0)
        {
            entry.handle = createCave(key, caveY);
            if (entry.handle != null)
            {
                uploadBudget--;
            }
        }
        if (entry.handle == null)
        {
            return false;
        }
        if (!entry.uploaded && uploadBudget > 0)
        {
            try
            {
                entry.handle.setPixels(slice.colors());
                entry.handle.upload();
                entry.uploaded = true;
                uploadBudget--;
            }
            catch (RuntimeException failure)
            {
                closeCave(entry);
                return false;
            }
        }
        if (!entry.uploaded)
        {
            return false;
        }
        int tint = ((int) Math.round(Math.max(0.0D, Math.min(1.0D, alpha)) * 255.0D) << 24)
                | 0xFFFFFF;
        graphics.blit(RenderPipelines.GUI_TEXTURED, entry.handle.id(), x, y,
                0.0F, 0.0F, size, size, 256, 256, tint);
        return true;
    }

    /** Releases every texture. Call when the world changes. */
    public void clear()
    {
        for (SurfaceEntry entry : surface.values())
        {
            closeSurface(entry);
        }
        surface.clear();
        for (CaveEntry entry : cave.values())
        {
            closeCave(entry);
        }
        cave.clear();
        uploadBudget = 0;
    }

    @Override
    public void close()
    {
        clear();
    }

    private DynamicTextureHandle createSurface(RegionKey key, int size)
    {
        try
        {
            String name = "region_" + MinimapWorlds.safe(key.dimension()) + "_"
                    + key.regionX() + "_" + key.regionZ() + "_" + size;
            return DynamicTextureHandle.create("weaver-minimap", name, size, size,
                    TextureOptions.linearClamp());
        }
        catch (RuntimeException failure)
        {
            System.err.println("[weaver-minimap] surface texture unavailable for "
                    + key.fileName() + " reason=" + failure);
            return null;
        }
    }

    private DynamicTextureHandle createCave(RegionKey key, int caveY)
    {
        try
        {
            String name = "cave_" + MinimapWorlds.safe(key.dimension()) + "_"
                    + key.regionX() + "_" + key.regionZ() + "_" + caveY;
            return DynamicTextureHandle.create("weaver-minimap", name, 256, 256,
                    TextureOptions.linearClamp());
        }
        catch (RuntimeException failure)
        {
            System.err.println("[weaver-minimap] cave texture unavailable for "
                    + key.fileName() + " y=" + caveY + " reason=" + failure);
            return null;
        }
    }

    private void evictSurface(RegionKey keep)
    {
        while (surface.size() > MAX_TEXTURES)
        {
            Iterator<Map.Entry<RegionKey, SurfaceEntry>> iterator = surface.entrySet().iterator();
            Map.Entry<RegionKey, SurfaceEntry> oldest = iterator.next();
            if (oldest.getKey().equals(keep) && surface.size() > 1)
            {
                oldest = iterator.next();
            }
            iterator.remove();
            closeSurface(oldest.getValue());
        }
    }

    private void evictCave(CaveSliceKey keep)
    {
        while (cave.size() > MAX_CAVE_TEXTURES)
        {
            Iterator<Map.Entry<CaveSliceKey, CaveEntry>> iterator = cave.entrySet().iterator();
            Map.Entry<CaveSliceKey, CaveEntry> oldest = iterator.next();
            if (oldest.getKey().equals(keep) && cave.size() > 1)
            {
                oldest = iterator.next();
            }
            iterator.remove();
            closeCave(oldest.getValue());
        }
    }

    private static void closeSurface(SurfaceEntry entry)
    {
        if (entry.base != null)
        {
            entry.base.close();
            entry.base = null;
        }
        if (entry.mip != null)
        {
            entry.mip.close();
            entry.mip = null;
        }
        entry.baseDirty = false;
        entry.mipDirty = false;
    }

    private static void closeCave(CaveEntry entry)
    {
        if (entry.handle != null)
        {
            entry.handle.close();
            entry.handle = null;
        }
        entry.uploaded = false;
    }

    private static final class SurfaceEntry
    {
        private DynamicTextureHandle base;
        private DynamicTextureHandle mip;
        private boolean baseDirty = true;
        private boolean mipDirty = true;
    }

    private static final class CaveEntry
    {
        private DynamicTextureHandle handle;
        private boolean uploaded;
    }
}
