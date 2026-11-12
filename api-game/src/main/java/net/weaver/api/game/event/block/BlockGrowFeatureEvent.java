package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired when a sapling, mushroom, or nether fungus is about to grow into a tree
 * or huge mushroom ({@code TreeGrower#growTree}, {@code MushroomBlock#growMushroom},
 * {@code NetherFungusBlock#performBonemeal}). Cancelable: a canceled event grows
 * nothing.
 */
public final class BlockGrowFeatureEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;

    public BlockGrowFeatureEvent(ServerLevel level, BlockPos pos, BlockState state) {
        super(true);
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    /** The sapling, mushroom, or fungus block state. */
    public BlockState state() {
        return state;
    }
}
