package net.weaver.api.game.lookup;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.world.level.material.Fluid;

/**
 * A one-tank {@link FluidHandler} with a capacity and an optional fluid
 * filter. {@link #onChanged()} runs after every real change.
 */
public class SimpleFluidTank implements FluidHandler {
    private final long capacity;
    private final Predicate<Fluid> filter;
    private FluidAmount contents = FluidAmount.EMPTY;

    public SimpleFluidTank(long capacity) {
        this(capacity, fluid -> true);
    }

    public SimpleFluidTank(long capacity, Predicate<Fluid> filter) {
        if (capacity < 0) {
            throw new IllegalArgumentException("negative capacity");
        }
        this.capacity = capacity;
        this.filter = Objects.requireNonNull(filter, "filter");
    }

    /** What the tank holds now. */
    public FluidAmount contents() {
        return contents;
    }

    /** Replaces the contents directly (clamped to the capacity); no {@link #onChanged()}. */
    public void setContents(FluidAmount amount) {
        contents = amount.withAmount(Math.min(capacity, amount.amount()));
    }

    @Override
    public int tanks() {
        return 1;
    }

    @Override
    public FluidAmount fluid(int tank) {
        return tank == 0 ? contents : FluidAmount.EMPTY;
    }

    @Override
    public long capacity(int tank) {
        return tank == 0 ? capacity : 0;
    }

    @Override
    public boolean accepts(int tank, Fluid fluid) {
        return tank == 0 && filter.test(fluid);
    }

    @Override
    public long fill(FluidAmount offer, boolean simulate) {
        if (offer.isEmpty() || !filter.test(offer.fluid())
                || (!contents.isEmpty() && !contents.fluid().isSame(offer.fluid()))) {
            return 0;
        }
        long accepted = Math.min(offer.amount(), capacity - contents.amount());
        if (!simulate && accepted > 0) {
            contents = FluidAmount.of(offer.fluid(), contents.amount() + accepted);
            onChanged();
        }
        return Math.max(0, accepted);
    }

    @Override
    public FluidAmount drain(FluidAmount request, boolean simulate) {
        if (request.isEmpty() || contents.isEmpty() || !contents.fluid().isSame(request.fluid())) {
            return FluidAmount.EMPTY;
        }
        return drain(request.amount(), simulate);
    }

    @Override
    public FluidAmount drain(long amount, boolean simulate) {
        long taken = Math.min(Math.max(0, amount), contents.amount());
        if (taken == 0) {
            return FluidAmount.EMPTY;
        }
        FluidAmount drained = contents.withAmount(taken);
        if (!simulate) {
            contents = contents.withAmount(contents.amount() - taken);
            onChanged();
        }
        return drained;
    }

    /** Called after the contents changed through {@link #fill} or {@link #drain}. */
    protected void onChanged() {
    }
}
