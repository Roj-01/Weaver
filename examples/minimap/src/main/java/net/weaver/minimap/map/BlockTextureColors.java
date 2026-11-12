package net.weaver.minimap.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a block's map colour from the average colour of its top texture.
 *
 * <p>This is the difference between a map that looks like a flat diagram and a
 * map that looks like the world seen from above. A vanilla map colour is one
 * flat RGB value per material, so a plains biome becomes a solid green slab.
 * Averaging the block's own texture keeps the per-block luminance noise, the
 * mortar lines in stone brick, the grain in planks, the shading inside the
 * grass sprite; then the biome tint is multiplied over the result exactly the
 * way the block renderer does it, so grass, foliage and water carry their
 * biome colour and every other block stays neutral.</p>
 *
 * <p>Results are cached three ways: per {@link BlockState}, per tint index and
 * per texture path. The expensive work (decoding a PNG and averaging it) runs
 * once per distinct texture for the lifetime of the client, which is a few
 * hundred small images at most. All methods must be called on the client
 * thread; the cache is not synchronized.</p>
 */
public final class BlockTextureColors
{
    /** Blocks whose modal texture is missing and needs an explicit sprite. */
    private static final Identifier WATER_STILL = Identifier.parse("minecraft:block/water_still");
    private static final Identifier LAVA_STILL = Identifier.parse("minecraft:block/lava_still");

    private final Map<BlockState, Integer> stateColors = new HashMap<>(512);
    private final Map<BlockState, Integer> stateTintIndices = new HashMap<>(512);
    private final Map<Identifier, Integer> textureColors = new HashMap<>(256);
    private final List<BlockStateModelPart> parts = new ArrayList<>(4);

    /**
     * Returns the texture colour of a block with its biome tint applied.
     *
     * <p>The returned colour keeps the texture's average alpha in the high
     * byte. A liquid returns a partially transparent colour, which the surface
     * compositor blends over the block underneath instead of losing the
     * sea floor. Opaque blocks return {@code 0xFF...}.</p>
     *
     * @param level the client level, used for the biome tint and the fallback
     *              map colour
     * @param pos   the block position; the caller owns the mutable position
     * @param state the block state at {@code pos}
     * @return an ARGB colour, or {@code 0} when the block has no colour
     */
    public int color(ClientLevel level, BlockPos pos, BlockState state)
    {
        if (state.isAir())
        {
            return 0;
        }
        FluidState fluid = state.getFluidState();
        int argb;
        if (!fluid.isEmpty() && fluid.getType().is(FluidTags.WATER))
        {
            argb = texture(level, WATER_STILL);
        }
        else if (!fluid.isEmpty() && fluid.getType().is(FluidTags.LAVA))
        {
            argb = texture(level, LAVA_STILL);
        }
        else
        {
            argb = stateColor(level, pos, state);
        }
        if (argb == 0)
        {
            argb = mapColor(level, pos, state);
        }
        if (argb == 0)
        {
            return 0;
        }
        return applyTint(level, pos, state, argb);
    }

    /**
     * Returns the raw texture colour of a block, without any biome tint.
     *
     * @param level the client level
     * @param pos   the block position
     * @param state the block state
     * @return an ARGB colour, or {@code 0} when no texture colour is available
     */
    public int rawColor(ClientLevel level, BlockPos pos, BlockState state)
    {
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty() && fluid.getType().is(FluidTags.WATER))
        {
            return texture(level, WATER_STILL);
        }
        if (!fluid.isEmpty() && fluid.getType().is(FluidTags.LAVA))
        {
            return texture(level, LAVA_STILL);
        }
        int argb = stateColor(level, pos, state);
        return argb != 0 ? argb : mapColor(level, pos, state);
    }

    /**
     * Returns the tint index of the block model's top face.
     *
     * @param state the block state
     * @return the tint index, or {@code -1} when the block is never tinted
     */
    public int tintIndex(BlockState state)
    {
        Integer cached = stateTintIndices.get(state);
        if (cached != null)
        {
            return cached;
        }
        int index = -1;
        try
        {
            BakedQuad quad = biggestTopQuad(state);
            if (quad != null)
            {
                index = quad.materialInfo().tintIndex();
            }
        }
        catch (RuntimeException ignored)
        {
            index = -1;
        }
        stateTintIndices.put(state, index);
        return index;
    }

    /** Drops every cached colour. Call after a resource reload. */
    public void reset()
    {
        stateColors.clear();
        stateTintIndices.clear();
        textureColors.clear();
    }

    private int stateColor(ClientLevel level, BlockPos pos, BlockState state)
    {
        Integer cached = stateColors.get(state);
        if (cached != null)
        {
            return cached;
        }
        int argb = 0;
        try
        {
            TextureAtlasSprite sprite = topSprite(state);
            if (sprite != null)
            {
                argb = texture(level, sprite.contents().name());
            }
            if (argb == 0)
            {
                argb = mapColor(level, pos, state);
            }
        }
        catch (RuntimeException failure)
        {
            argb = mapColor(level, pos, state);
        }
        stateColors.put(state, argb);
        return argb;
    }

    private int mapColor(ClientLevel level, BlockPos pos, BlockState state)
    {
        MapColor color = state.getMapColor(level, pos);
        if (color == null || color == MapColor.NONE)
        {
            return 0;
        }
        return 0xFF000000 | (color.col & 0xFFFFFF);
    }

    private int applyTint(ClientLevel level, BlockPos pos, BlockState state, int argb)
    {
        if (!(state.getBlock() instanceof LiquidBlock))
        {
            int index = tintIndex(state);
            if (index < 0)
            {
                return argb;
            }
        }
        int tint;
        try
        {
            tint = tintColor(level, pos, state);
        }
        catch (RuntimeException failure)
        {
            return argb;
        }
        if (tint == -1 || (tint & 0xFFFFFF) == 0xFFFFFF)
        {
            return argb;
        }
        int red = (int) ((((argb >> 16) & 0xFF) * ((tint >> 16) & 0xFF)) / 255.0F);
        int green = (int) ((((argb >> 8) & 0xFF) * ((tint >> 8) & 0xFF)) / 255.0F);
        int blue = (int) (((argb & 0xFF) * (tint & 0xFF)) / 255.0F);
        return (argb & 0xFF000000) | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }

    private int tintColor(ClientLevel level, BlockPos pos, BlockState state)
    {
        Minecraft minecraft = Minecraft.getInstance();
        BlockTintSource source;
        if (state.getBlock() instanceof LiquidBlock)
        {
            FluidState fluid = state.getFluidState();
            if (fluid.isEmpty())
            {
                return -1;
            }
            source = minecraft.getModelManager().getFluidStateModelSet()
                    .get(fluid).tintSource();
        }
        else
        {
            BlockColors colors = minecraft.getBlockColors();
            source = colors.getTintSource(state, tintIndex(state));
        }
        if (source == null)
        {
            return -1;
        }
        return source.colorInWorld(state, BiomeBlend.INSTANCE.prepare(level, true), pos);
    }

    private TextureAtlasSprite topSprite(BlockState state)
    {
        BlockStateModelSet set = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        BlockStateModel model = set.get(state);
        if (model == null)
        {
            return null;
        }
        TextureAtlasSprite missing = Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(MissingTextureAtlasSprite.getLocation());

        BakedQuad proposed = biggestTopQuad(state);
        if (proposed != null && proposed.materialInfo().sprite() != missing)
        {
            return proposed.materialInfo().sprite();
        }
        Material.Baked particle = set.getParticleMaterial(state);
        if (particle != null && particle.sprite() != missing)
        {
            return particle.sprite();
        }
        return proposed != null ? proposed.materialInfo().sprite() : null;
    }

    private BakedQuad biggestTopQuad(BlockState state)
    {
        BlockStateModelSet set = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        BlockStateModel model = set.get(state);
        if (model == null)
        {
            return null;
        }
        parts.clear();
        model.collectParts(RandomSource.create(0L), parts);
        BakedQuad best = null;
        for (int index = 0; index < parts.size(); index++)
        {
            BlockStateModelPart part = parts.get(index);
            best = pick(best, part.getQuads(Direction.UP));
            if (best == null)
            {
                best = pick(null, part.getQuads(null));
            }
        }
        return best;
    }

    private static BakedQuad pick(BakedQuad current, List<BakedQuad> quads)
    {
        if (quads == null || quads.isEmpty())
        {
            return current;
        }
        BakedQuad best = current;
        float bestArea = best == null ? 0.0F : area(best);
        for (int index = 0; index < quads.size(); index++)
        {
            BakedQuad quad = quads.get(index);
            if (quad.direction() != Direction.UP)
            {
                continue;
            }
            float area = area(quad);
            if (area > bestArea)
            {
                bestArea = area;
                best = quad;
            }
        }
        return best;
    }

    private static float area(BakedQuad quad)
    {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int index = 0; index < 4; index++)
        {
            var position = quad.position(index);
            minX = Math.min(minX, position.x());
            maxX = Math.max(maxX, position.x());
            minZ = Math.min(minZ, position.z());
            maxZ = Math.max(maxZ, position.z());
        }
        return (maxX - minX) * (maxZ - minZ);
    }

    private int texture(ClientLevel level, Identifier spriteName)
    {
        Integer cached = textureColors.get(spriteName);
        if (cached != null)
        {
            return cached;
        }
        int argb = 0;
        Identifier location = Identifier.fromNamespaceAndPath(spriteName.getNamespace(),
                "textures/" + spriteName.getPath() + ".png");
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Optional<Resource> resource = manager.getResource(location);
        if (resource.isPresent())
        {
            try (InputStream input = resource.get().open())
            {
                BufferedImage image = ImageIO.read(input);
                if (image != null)
                {
                    argb = average(image);
                }
            }
            catch (IOException | RuntimeException ignored)
            {
                argb = 0;
            }
        }
        textureColors.put(spriteName, argb);
        return argb;
    }

    /**
     * Averages a texture over a sparse grid.
     *
     * <p>Sampling a 16x16 sprite at an 8x8 stride is enough for a map pixel and
     * roughly four times cheaper than walking every texel. Fully transparent
     * texels are skipped so cutout sprites average to their solid parts, and
     * the alpha average is kept so the compositor can let liquid and glass show
     * what is underneath.</p>
     */
    private static int average(BufferedImage image)
    {
        int width = image.getWidth();
        int height = image.getHeight();
        int size = Math.min(width, height);
        if (size <= 0)
        {
            return 0;
        }
        int stride = Math.max(1, Math.min(4, size / 8));
        int count = size / stride;
        int red = 0;
        int green = 0;
        int blue = 0;
        int alpha = 0;
        int used = 0;
        Raster raster = image.getData();
        int[] holder = null;
        boolean indexed = image.getColorModel().getNumComponents() < 3;
        for (int row = 0; row < count; row++)
        {
            for (int column = 0; column < count; column++)
            {
                int argb;
                if (indexed)
                {
                    holder = raster.getPixel(column * stride, row * stride, holder);
                    int sample = holder[0] & 0xFF;
                    int a = holder.length > 1 ? holder[1] : 255;
                    argb = (a << 24) | (sample << 16) | (sample << 8) | sample;
                }
                else
                {
                    argb = image.getRGB(column * stride, row * stride);
                }
                int a = (argb >>> 24) & 0xFF;
                if (a > 10)
                {
                    red += (argb >> 16) & 0xFF;
                    green += (argb >> 8) & 0xFF;
                    blue += argb & 0xFF;
                    alpha += a;
                    used++;
                }
            }
        }
        if (used == 0)
        {
            return 0;
        }
        return ((alpha / used) << 24) | ((red / used) << 16) | ((green / used) << 8) | (blue / used);
    }

    private static int clamp(int value)
    {
        return Math.max(0, Math.min(255, value));
    }
}
