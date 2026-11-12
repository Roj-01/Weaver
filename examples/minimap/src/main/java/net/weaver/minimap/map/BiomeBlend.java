package net.weaver.minimap.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * Biome colour sampler that blends the tint over the neighbouring blocks.
 *
 * <p>Handing a raw level to a tint source produces a hard border wherever two
 * biomes meet: one map pixel is jungle green, the next is plains green, and
 * the seam reads as a straight line. Vanilla's block renderer solves this by
 * sampling the biome colour on a small plus-shaped neighbourhood, and the map
 * has to do the same or the transition is visible from a thousand blocks up.
 * A three-by-three cross is enough; a full square weights the diagonals, which
 * makes the seam softer but muddies single-block details.</p>
 *
 * <p>One instance is reused; {@link #prepare(ClientLevel, boolean)} must be
 * called before each sampling run. Not thread-safe.</p>
 */
final class BiomeBlend implements BlockAndTintGetter
{
    /** The shared instance used by the colour pipeline. */
    static final BiomeBlend INSTANCE = new BiomeBlend();

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private ClientLevel level;
    private int start;
    private int end;

    private BiomeBlend()
    {
    }

    /**
     * Points the sampler at a level.
     *
     * @param level the client level
     * @param blend {@code true} to average the neighbours, {@code false} to
     *              sample the biome under the position only
     * @return this sampler
     */
    BiomeBlend prepare(ClientLevel level, boolean blend)
    {
        this.level = level;
        this.start = blend ? -1 : 0;
        this.end = blend ? 1 : 0;
        return this;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos)
    {
        return level.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        return level.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos)
    {
        return level.getFluidState(pos);
    }

    @Override
    public LevelLightEngine getLightEngine()
    {
        return level.getLightEngine();
    }

    @Override
    public int getHeight()
    {
        return level.getHeight();
    }

    @Override
    public int getMinY()
    {
        return level.getMinY();
    }

    @Override
    public CardinalLighting cardinalLighting()
    {
        return level.cardinalLighting();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver)
    {
        if (level == null)
        {
            return 0xFFFFFFFF;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int red = 0;
        int green = 0;
        int blue = 0;
        int total = 0;
        for (int dx = start; dx <= end; dx++)
        {
            for (int dz = start; dz <= end; dz++)
            {
                if (dx != 0 && dz != 0)
                {
                    continue;
                }
                cursor.set(x + dx, y, z + dz);
                Holder<Biome> holder = level.getBiome(cursor);
                if (holder == null)
                {
                    continue;
                }
                int color = resolver.getColor(holder.value(), cursor.getX(), cursor.getZ());
                red += color & 0xFF0000;
                green += color & 0xFF00;
                blue += color & 0xFF;
                total++;
            }
        }
        if (total == 0)
        {
            return 0xFFFFFFFF;
        }
        return 0xFF000000 | (red / total) & 0xFF0000 | (green / total) & 0xFF00 | (blue / total) & 0xFF;
    }
}
