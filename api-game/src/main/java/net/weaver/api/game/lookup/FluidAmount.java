package net.weaver.api.game.lookup;

import java.util.Objects;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * An amount of one fluid, in millibuckets ({@link #BUCKET} per bucket). An
 * amount of {@code 0} or {@link Fluids#EMPTY} is empty.
 *
 * @param fluid  the fluid, never {@code null}
 * @param amount the amount in millibuckets, never negative
 */
public record FluidAmount(Fluid fluid, long amount) {
    /** Millibuckets in one bucket. */
    public static final long BUCKET = 1000;

    /** No fluid. */
    public static final FluidAmount EMPTY = new FluidAmount(Fluids.EMPTY, 0);

    public FluidAmount {
        Objects.requireNonNull(fluid, "fluid");
        if (amount < 0) {
            throw new IllegalArgumentException("negative fluid amount: " + amount);
        }
    }

    /** {@code amount} of {@code fluid}; {@link #EMPTY} when either is empty. */
    public static FluidAmount of(Fluid fluid, long amount) {
        return fluid == Fluids.EMPTY || amount == 0 ? EMPTY : new FluidAmount(fluid, amount);
    }

    public boolean isEmpty() {
        return amount == 0 || fluid == Fluids.EMPTY;
    }

    /** The same fluid with a different amount. */
    public FluidAmount withAmount(long newAmount) {
        return of(fluid, newAmount);
    }
}
