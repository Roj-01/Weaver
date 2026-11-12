package net.weaver.minimap.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns a block into the colour a map pixel should show.
 *
 * <p>The colour comes from the block's own top texture, not from its vanilla
 * map colour. A map colour is a single flat RGB value per material, so grass,
 * stone and sand all collapse into even slabs; a texture average keeps the
 * grain, the mortar lines and the per-block noise that make a top-down view
 * read as a place rather than a diagram. The biome tint is then multiplied
 * over the result through the same tint sources the block renderer uses, so
 * grass and foliage still change with the biome.</p>
 *
 * <p>Shading is not applied here. The scanner owns the height field, so it
 * applies slope, depth and daylight after the colour is known; keeping the two
 * apart means a colour can be cached per block state while the light in front
 * of it stays free to change with the column.</p>
 */
public final class MinimapColors
{
    private static final BlockTextureColors TEXTURES = new BlockTextureColors();

    private MinimapColors()
    {
    }

    /**
     * Returns the tinted texture colour for a block.
     *
     * @param level the client level
     * @param pos   the block position; the caller owns the mutable position
     * @param state the block state at {@code pos}
     * @return an ARGB colour whose alpha is the texture's average alpha, or
     *         {@code 0} for an invisible block
     */
    public static int color(ClientLevel level, BlockPos pos, BlockState state)
    {
        return TEXTURES.color(level, pos, state);
    }

    /**
     * Returns the tinted texture colour for a block with a known biome.
     *
     * <p>The biome parameter is kept for callers that already resolved it;
     * tinting reads the biome under each neighbour itself so the seam between
     * two biomes stays soft.</p>
     *
     * @param level the client level
     * @param pos   the block position; the caller owns the mutable position
     * @param state the block state at {@code pos}
     * @param biome the biome at {@code pos}, never {@code null}
     * @return an ARGB colour, or {@code 0} for an invisible block
     */
    public static int color(ClientLevel level, BlockPos pos, BlockState state, Biome biome)
    {
        return TEXTURES.color(level, pos, state);
    }

    /**
     * Multiplies the RGB channels of a colour by a factor.
     *
     * @param argb   the ARGB colour
     * @param factor the multiplier
     * @return an opaque ARGB colour
     */
    public static int shade(int argb, double factor)
    {
        int red = clamp((int) Math.round(((argb >> 16) & 0xFF) * factor));
        int green = clamp((int) Math.round(((argb >> 8) & 0xFF) * factor));
        int blue = clamp((int) Math.round((argb & 0xFF) * factor));
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /**
     * Applies a subtle depth tint to water.
     *
     * @param argb  the water colour
     * @param depth the depth below sea level, at least {@code 0}
     * @return an opaque ARGB colour
     */
    public static int waterDepth(int argb, int depth)
    {
        double factor = Math.max(0.55, 1.0 - Math.max(0, depth) * 0.018);
        return shade(argb, factor);
    }

    /** Drops every cached texture colour. Call after a resource reload. */
    public static void reset()
    {
        TEXTURES.reset();
    }

    private static int clamp(int value)
    {
        return Math.max(0, Math.min(255, value));
    }
}
