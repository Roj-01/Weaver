package net.weaver.api.game.client.texture;

/**
 * Sampling options for a {@link DynamicTextureHandle}.
 *
 * <p>The defaults match the vanilla {@code DynamicTexture}: nearest filtering
 * with repeat addressing and one mip level. Use {@link #linearClamp()} for
 * smooth, edge-clamped sampling, and {@link #linearClampMipmapped()} when the
 * texture is drawn at a reduced scale.
 *
 * @param linearFiltering {@code true} for linear min/mag filtering,
 *                        {@code false} for nearest
 * @param clampToEdge     {@code true} for clamp-to-edge addressing,
 *                        {@code false} for repeat
 * @param mipmaps         {@code true} to allocate and maintain a full mip chain
 */
public record TextureOptions(boolean linearFiltering, boolean clampToEdge, boolean mipmaps)
{
    /** Nearest filtering, repeat addressing, no mipmaps. */
    public static final TextureOptions DEFAULT = new TextureOptions(false, false, false);

    /** Linear filtering, clamp-to-edge addressing, no mipmaps. */
    public static TextureOptions linearClamp()
    {
        return new TextureOptions(true, true, false);
    }

    /** Linear filtering, clamp-to-edge addressing, with a full mip chain. */
    public static TextureOptions linearClampMipmapped()
    {
        return new TextureOptions(true, true, true);
    }
}
