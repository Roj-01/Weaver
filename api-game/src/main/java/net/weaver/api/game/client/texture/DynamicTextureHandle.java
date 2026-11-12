package net.weaver.api.game.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A small, game-typed helper for creating and updating a runtime texture.
 *
 * <p>This is the general-purpose texture path for client features that generate
 * pixels at runtime, such as a minimap. It owns a {@link NativeImage}, registers
 * a {@link DynamicTexture} with the client texture manager, exposes ARGB pixel
 * writes and partial uploads, and publishes the image to the GPU.
 *
 * <p>Create, update, upload, and close on the client/render thread. The handle
 * is not thread-safe. After {@link #close()} every mutating method fails.
 *
 * <p>Mipmaps are supported through {@link TextureOptions#mipmaps()}. The handle
 * allocates a full mip chain, generates it after a full upload, and regenerates
 * it after a partial upload. Partial uploads therefore cost an extra CPU
 * downsample pass proportional to the texture size.
 */
public final class DynamicTextureHandle implements AutoCloseable
{
    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern NAME = Pattern.compile("[a-z0-9_./-]+");
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private final Identifier id;
    private final DynamicTexture texture;
    private final int width;
    private final int height;
    private final TextureOptions options;
    private final int mipLevels;
    private boolean closed;

    private DynamicTextureHandle(Identifier id, DynamicTexture texture, int width, int height,
                                 TextureOptions options, int mipLevels)
    {
        this.id = id;
        this.texture = texture;
        this.width = width;
        this.height = height;
        this.options = options;
        this.mipLevels = mipLevels;
    }

    /**
     * Creates and registers a texture with {@link TextureOptions#DEFAULT}.
     *
     * @param modId  the owning mod id, matching {@code [a-z0-9_.-]+}
     * @param name   a stable name fragment, matching {@code [a-z0-9_./-]+}
     * @param width  the texture width, at least {@code 1}
     * @param height the texture height, at least {@code 1}
     * @return a new handle, never {@code null}
     * @throws IllegalArgumentException when an argument is invalid
     * @throws IllegalStateException    when no client is running
     */
    public static DynamicTextureHandle create(String modId, String name, int width, int height)
    {
        return create(modId, name, width, height, TextureOptions.DEFAULT);
    }

    /**
     * Creates and registers a texture.
     *
     * @param modId   the owning mod id, matching {@code [a-z0-9_.-]+}
     * @param name    a stable name fragment, matching {@code [a-z0-9_./-]+}
     * @param width   the texture width, at least {@code 1}
     * @param height  the texture height, at least {@code 1}
     * @param options the sampling and mipmap options, never {@code null}
     * @return a new handle, never {@code null}
     * @throws IllegalArgumentException when an argument is invalid
     * @throws IllegalStateException    when no client is running
     */
    public static DynamicTextureHandle create(String modId, String name, int width, int height,
                                              TextureOptions options)
    {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(options, "options");
        if (!MOD_ID.matcher(modId).matches())
        {
            throw new IllegalArgumentException("Invalid texture mod id '" + modId + "'");
        }
        if (!NAME.matcher(name).matches())
        {
            throw new IllegalArgumentException("Invalid texture name '" + name + "'");
        }
        if (width < 1 || height < 1)
        {
            throw new IllegalArgumentException("Texture size must be at least 1x1");
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null)
        {
            throw new IllegalStateException("Cannot create a dynamic texture without a client");
        }

        String path = "dynamic/" + name + "_" + NEXT_ID.incrementAndGet();
        Identifier id = Identifier.fromNamespaceAndPath(modId, path);
        int mipLevels = options.mipmaps() ? mipLevels(width, height) : 1;
        AddressMode address = options.clampToEdge()
                ? AddressMode.CLAMP_TO_EDGE
                : AddressMode.REPEAT;
        FilterMode filter = options.linearFiltering() ? FilterMode.LINEAR : FilterMode.NEAREST;
        GpuSampler sampler = RenderSystem.getSamplerCache().getSampler(address, address, filter,
                filter, mipLevels > 1);
        NativeImage image = new NativeImage(width, height, true);
        DynamicTexture texture = new WeaverDynamicTexture(() -> "weaver:" + id, image, mipLevels,
                sampler);
        TextureManager textureManager = minecraft.getTextureManager();
        try
        {
            textureManager.register(id, texture);
        }
        catch (RuntimeException | Error failure)
        {
            texture.close();
            throw failure;
        }
        DynamicTextureHandle handle = new DynamicTextureHandle(id, texture, width, height, options,
                mipLevels);
        handle.upload();
        return handle;
    }

    /** The registered texture id. */
    public Identifier id()
    {
        return id;
    }

    /** The texture width in pixels. */
    public int width()
    {
        return width;
    }

    /** The texture height in pixels. */
    public int height()
    {
        return height;
    }

    /** The sampling options this handle was created with. */
    public TextureOptions options()
    {
        return options;
    }

    /** The number of mip levels; {@code 1} when mipmaps are disabled. */
    public int mipLevels()
    {
        return mipLevels;
    }

    /**
     * Writes one ARGB pixel into the CPU image.
     *
     * <p>Call {@link #upload()} after a batch of writes to publish the image.
     *
     * @param x    the pixel x, from {@code 0} to {@link #width()} exclusive
     * @param y    the pixel y, from {@code 0} to {@link #height()} exclusive
     * @param argb the ARGB color
     * @throws IndexOutOfBoundsException when the coordinate is outside the image
     * @throws IllegalStateException     when the handle is closed
     */
    public void setPixel(int x, int y, int argb)
    {
        requireOpen();
        if (x < 0 || x >= width || y < 0 || y >= height)
        {
            throw new IndexOutOfBoundsException("Pixel " + x + "," + y
                    + " is outside " + width + "x" + height);
        }
        texture.getPixels().setPixel(x, y, argb);
    }

    /**
     * Copies a row-major ARGB image into the CPU texture.
     *
     * @param argb the pixels, at least {@code width * height} entries
     * @throws IllegalArgumentException when the array is too small
     * @throws IllegalStateException    when the handle is closed
     */
    public void setPixels(int[] argb)
    {
        requireOpen();
        Objects.requireNonNull(argb, "argb");
        if (argb.length < width * height)
        {
            throw new IllegalArgumentException("Expected at least " + (width * height)
                    + " pixels but got " + argb.length);
        }
        NativeImage image = texture.getPixels();
        int index = 0;
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                image.setPixel(x, y, argb[index++]);
            }
        }
    }

    /**
     * Uploads a rectangular region of the CPU image to the GPU.
     *
     * <p>The source array is row-major and only the first
     * {@code width * height} entries are read. The region is copied into the
     * CPU image, uploaded with a partial texture write, and the mip chain is
     * regenerated when mipmaps are enabled.
     *
     * @param x      the region x, from {@code 0} to {@link #width()} exclusive
     * @param y      the region y, from {@code 0} to {@link #height()} exclusive
     * @param width  the region width, at least {@code 1}
     * @param height the region height, at least {@code 1}
     * @param argb   the region pixels in row-major order
     * @throws IllegalArgumentException when the region is outside the image or
     *                                  the source array is too small
     * @throws IllegalStateException    when the handle is closed or the texture
     *                                  is unavailable
     */
    public void uploadRegion(int x, int y, int width, int height, int[] argb)
    {
        requireOpen();
        Objects.requireNonNull(argb, "argb");
        TextureRegion.validate(this.width, this.height, x, y, width, height, argb);
        int[] region = TextureRegion.copyRegion(argb, width, height);
        NativeImage image = texture.getPixels();
        NativeImage patch = new NativeImage(width, height, false);
        try
        {
            int index = 0;
            for (int row = 0; row < height; row++)
            {
                for (int column = 0; column < width; column++)
                {
                    int value = region[index++];
                    image.setPixel(x + column, y + row, value);
                    patch.setPixel(column, row, value);
                }
            }
            RenderSystem.getDevice().createCommandEncoder()
                    .writeToTexture(texture.getTexture(), patch, 0, 0, x, y);
            if (texture instanceof WeaverDynamicTexture mipmapped)
            {
                mipmapped.regenerateMips();
            }
        }
        catch (RuntimeException failure)
        {
            throw new IllegalStateException("Failed to upload region " + x + "," + y + " "
                    + width + "x" + height + " for dynamic texture " + id, failure);
        }
        finally
        {
            patch.close();
        }
    }

    /**
     * Uploads the CPU image to the GPU and regenerates the mip chain.
     *
     * @throws IllegalStateException when the handle is closed
     */
    public void upload()
    {
        requireOpen();
        texture.upload();
        if (texture instanceof WeaverDynamicTexture mipmapped)
        {
            mipmapped.regenerateMips();
        }
    }

    /**
     * Releases the texture and its native image.
     *
     * <p>This method is idempotent and safe after the client has shut down.
     */
    @Override
    public void close()
    {
        if (closed)
        {
            return;
        }
        closed = true;
        boolean released = false;
        try
        {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null)
            {
                minecraft.getTextureManager().release(id);
                released = true;
            }
        }
        catch (Throwable ignored)
        {
            // The texture manager may already be shutting down; the image is
            // still released by DynamicTexture's own close path.
        }
        if (!released)
        {
            try
            {
                texture.close();
            }
            catch (Throwable ignored)
            {
                // The GPU device may already be gone.
            }
        }
    }

    private void requireOpen()
    {
        if (closed)
        {
            throw new IllegalStateException("Dynamic texture " + id + " is closed");
        }
    }

    private static int mipLevels(int width, int height)
    {
        int largest = Math.max(width, height);
        int levels = 1;
        while (largest > 1)
        {
            largest >>= 1;
            levels++;
        }
        return levels;
    }

    private static NativeImage downsample(NativeImage source, int width, int height)
    {
        NativeImage target = new NativeImage(width, height, false);
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        for (int y = 0; y < height; y++)
        {
            int sourceY = Math.min(sourceHeight - 1, y * 2);
            int nextY = Math.min(sourceHeight - 1, sourceY + 1);
            for (int x = 0; x < width; x++)
            {
                int sourceX = Math.min(sourceWidth - 1, x * 2);
                int nextX = Math.min(sourceWidth - 1, sourceX + 1);
                int first = source.getPixel(sourceX, sourceY);
                int second = source.getPixel(nextX, sourceY);
                int third = source.getPixel(sourceX, nextY);
                int fourth = source.getPixel(nextX, nextY);
                target.setPixel(x, y, average(first, second, third, fourth));
            }
        }
        return target;
    }

    private static int average(int first, int second, int third, int fourth)
    {
        int alpha = (((first >>> 24) & 0xFF) + ((second >>> 24) & 0xFF)
                + ((third >>> 24) & 0xFF) + ((fourth >>> 24) & 0xFF)) / 4;
        int red = (((first >>> 16) & 0xFF) + ((second >>> 16) & 0xFF)
                + ((third >>> 16) & 0xFF) + ((fourth >>> 16) & 0xFF)) / 4;
        int green = (((first >>> 8) & 0xFF) + ((second >>> 8) & 0xFF)
                + ((third >>> 8) & 0xFF) + ((fourth >>> 8) & 0xFF)) / 4;
        int blue = ((first & 0xFF) + (second & 0xFF) + (third & 0xFF) + (fourth & 0xFF)) / 4;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static final class WeaverDynamicTexture extends DynamicTexture
    {
        private final int mipLevels;

        private WeaverDynamicTexture(Supplier<String> label, NativeImage source, int mipLevels,
                                     GpuSampler sampler)
        {
            super(label, source);
            this.mipLevels = mipLevels;
            if (mipLevels > 1)
            {
                GpuTexture previousTexture = this.texture;
                GpuTextureView previousView = this.textureView;
                GpuDevice device = RenderSystem.getDevice();
                this.texture = device.createTexture(label,
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        GpuFormat.RGBA8_UNORM, source.getWidth(), source.getHeight(), 1,
                        mipLevels);
                this.textureView = device.createTextureView(this.texture);
                if (previousView != null)
                {
                    previousView.close();
                }
                if (previousTexture != null)
                {
                    previousTexture.close();
                }
            }
            this.sampler = sampler;
        }

        private void regenerateMips()
        {
            if (mipLevels <= 1)
            {
                return;
            }
            NativeImage main = getPixels();
            NativeImage previous = main;
            for (int level = 1; level < mipLevels; level++)
            {
                int width = Math.max(1, previous.getWidth() >> 1);
                int height = Math.max(1, previous.getHeight() >> 1);
                NativeImage mip = downsample(previous, width, height);
                try
                {
                    RenderSystem.getDevice().createCommandEncoder()
                            .writeToTexture(getTexture(), mip, level, 0, 0, 0);
                }
                catch (RuntimeException | Error failure)
                {
                    mip.close();
                    throw failure;
                }
                finally
                {
                    if (previous != main)
                    {
                        previous.close();
                    }
                }
                previous = mip;
            }
            if (previous != main)
            {
                previous.close();
            }
        }
    }
}
