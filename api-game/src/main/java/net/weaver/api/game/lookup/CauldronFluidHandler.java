package net.weaver.api.game.lookup;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * A vanilla cauldron as a one-bucket tank: an empty cauldron takes a full bucket
 * of water or lava, and a full water or lava cauldron gives one back, the way a
 * player with a bucket does. A water cauldron that is one or two thirds full
 * reports 333 or 667 millibuckets but gives nothing, since vanilla moves water in
 * and out of it a bottle (a third) at a time.
 */
public final class CauldronFluidHandler implements FluidHandler {
    private final Level level;
    private final BlockPos pos;

    public CauldronFluidHandler(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    /** Whether {@code block} is one of the vanilla cauldrons this handler serves. */
    public static boolean handles(Block block) {
        return block == Blocks.CAULDRON || block == Blocks.WATER_CAULDRON || block == Blocks.LAVA_CAULDRON;
    }

    @Override
    public int tanks() {
        return 1;
    }

    @Override
    public FluidAmount fluid(int tank) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.LAVA_CAULDRON)) {
            return FluidAmount.of(Fluids.LAVA, FluidAmount.BUCKET);
        }
        if (state.is(Blocks.WATER_CAULDRON)) {
            int layers = state.getValue(LayeredCauldronBlock.LEVEL);
            return FluidAmount.of(Fluids.WATER,
                    layers >= 3 ? FluidAmount.BUCKET : Math.round(layers * FluidAmount.BUCKET / 3.0));
        }
        return FluidAmount.EMPTY;
    }

    @Override
    public long capacity(int tank) {
        return FluidAmount.BUCKET;
    }

    @Override
    public boolean accepts(int tank, Fluid fluid) {
        Fluid source = source(fluid);
        return source == Fluids.WATER || source == Fluids.LAVA;
    }

    @Override
    public long fill(FluidAmount offer, boolean simulate) {
        if (offer == null || offer.isEmpty() || offer.amount() < FluidAmount.BUCKET || !accepts(0, offer.fluid())
                || !level.getBlockState(pos).is(Blocks.CAULDRON)) {
            return 0;
        }
        if (!simulate) {
            BlockState full = source(offer.fluid()) == Fluids.LAVA ? Blocks.LAVA_CAULDRON.defaultBlockState()
                    : Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
            set(full);
        }
        return FluidAmount.BUCKET;
    }

    @Override
    public FluidAmount drain(FluidAmount request, boolean simulate) {
        if (request == null || request.isEmpty()) {
            return FluidAmount.EMPTY;
        }
        FluidAmount held = fluid(0);
        if (held.isEmpty() || source(held.fluid()) != source(request.fluid())) {
            return FluidAmount.EMPTY;
        }
        return drain(request.amount(), simulate);
    }

    @Override
    public FluidAmount drain(long amount, boolean simulate) {
        FluidAmount held = fluid(0);
        if (held.isEmpty() || held.amount() < FluidAmount.BUCKET || amount < FluidAmount.BUCKET) {
            return FluidAmount.EMPTY;
        }
        if (!simulate) {
            set(Blocks.CAULDRON.defaultBlockState());
        }
        return held;
    }

    private void set(BlockState state) {
        level.setBlockAndUpdate(pos, state);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
    }

    private static Fluid source(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }
}
