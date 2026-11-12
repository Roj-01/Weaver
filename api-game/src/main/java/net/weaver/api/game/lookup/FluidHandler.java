package net.weaver.api.game.lookup;

import net.minecraft.world.level.material.Fluid;

/**
 * Fluid tanks that other code can fill and drain. Amounts are in millibuckets
 * ({@link FluidAmount#BUCKET}). With {@code simulate} the calls report what
 * would happen and change nothing.
 */
public interface FluidHandler {
    /** The number of tanks. */
    int tanks();

    /** What {@code tank} holds now, or {@link FluidAmount#EMPTY}. */
    FluidAmount fluid(int tank);

    /** The most {@code tank} can hold. */
    long capacity(int tank);

    /** Whether {@code fluid} may go into {@code tank} at all. */
    default boolean accepts(int tank, Fluid fluid) {
        return true;
    }

    /**
     * Fills from {@code offer}, into whichever tanks take it.
     *
     * @return the amount accepted
     */
    long fill(FluidAmount offer, boolean simulate);

    /**
     * Drains up to {@code request.amount()} of {@code request.fluid()}.
     *
     * @return what was taken, possibly {@link FluidAmount#EMPTY}
     */
    FluidAmount drain(FluidAmount request, boolean simulate);

    /**
     * Drains up to {@code amount} of whatever fluid comes first.
     *
     * @return what was taken, possibly {@link FluidAmount#EMPTY}
     */
    FluidAmount drain(long amount, boolean simulate);
}
