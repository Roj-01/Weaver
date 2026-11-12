package net.weaver.minimap.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.Objects;

/**
 * {@link CaveBlockSource} backed by the loaded client level.
 *
 * <p>Must only be used on the client thread. Columns whose chunk is not loaded
 * report {@link CaveBlockKind#NONE} instead of forcing a chunk load.</p>
 */
public final class LevelCaveSource implements CaveBlockSource
{
    private static final int FALLBACK_SOLID = 0xFF7A7A7A;

    private final ClientLevel level;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final int minY;
    private final int maxY;

    /**
     * Creates a source for one level.
     *
     * @param level the client level, never {@code null}
     */
    public LevelCaveSource(ClientLevel level)
    {
        this.level = Objects.requireNonNull(level, "level");
        this.minY = level.getMinY();
        this.maxY = level.getMaxY();
    }

    @Override
    public int minY()
    {
        return minY;
    }

    @Override
    public int maxY()
    {
        return maxY;
    }

    @Override
    public CaveBlockKind kindAt(int x, int y, int z)
    {
        if (y < minY || y >= maxY)
        {
            return CaveBlockKind.VOID;
        }
        if (!level.hasChunk(x >> 4, z >> 4))
        {
            return CaveBlockKind.NONE;
        }
        BlockState state = level.getBlockState(cursor.set(x, y, z));
        if (state.isAir())
        {
            return CaveBlockKind.AIR;
        }
        var fluid = state.getFluidState();
        if (!fluid.isEmpty())
        {
            if (fluid.getType().is(FluidTags.LAVA))
            {
                return CaveBlockKind.LAVA;
            }
            if (fluid.getType().is(FluidTags.WATER))
            {
                return CaveBlockKind.WATER;
            }
        }
        return state.isSolid() ? CaveBlockKind.SOLID : CaveBlockKind.AIR;
    }

    @Override
    public int blockColor(int x, int y, int z)
    {
        if (!level.hasChunk(x >> 4, z >> 4))
        {
            return FALLBACK_SOLID;
        }
        BlockState state = level.getBlockState(cursor.set(x, y, z));
        MapColor mapColor = state.getMapColor(level, cursor);
        if (mapColor == null || mapColor == MapColor.NONE)
        {
            return FALLBACK_SOLID;
        }
        return 0xFF000000 | (mapColor.col & 0xFFFFFF);
    }

    @Override
    public int surfaceY(int x, int z)
    {
        if (!level.hasChunk(x >> 4, z >> 4))
        {
            return Integer.MIN_VALUE;
        }
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
    }
}
