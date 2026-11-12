package net.weaver.api.game.lookup;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.weaver.api.game.event.mixin.BucketItemAccessor;

/**
 * Fluid in items that change when filled or emptied: a bucket becomes a water
 * bucket and back. An {@link ItemLookup} handler cannot swap the item of a stack,
 * so these calls work on one item of the stack and return what that one item
 * becomes; the caller puts it in place (shrink the stack by one and give the
 * result, as vanilla does with a bucket in a stack).
 *
 * <p>Handles every {@link BucketItem} but buckets holding a mob, vanilla and modded (a modded fluid's
 * {@code getBucket()} is its filled bucket), and falls back to the item's
 * {@link Lookups#ITEM_FLUIDS} handler for items that keep fluid in their own data.
 * Amounts are in millibuckets; a bucket holds exactly {@link FluidAmount#BUCKET}.</p>
 */
public final class FluidContainers {
    /**
     * The outcome for one item.
     *
     * @param fluid     the fluid moved (into or out of the item), possibly empty
     * @param container what the item becomes; a copy of the unchanged item when nothing moved
     */
    public record Result(FluidAmount fluid, ItemStack container) {
        /** Whether any fluid moved. */
        public boolean moved() {
            return !fluid.isEmpty();
        }
    }

    private FluidContainers() {
    }

    /** What one item of {@code stack} holds, or {@link FluidAmount#EMPTY}. */
    public static FluidAmount contents(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return FluidAmount.EMPTY;
        }
        Fluid held = bucketFluid(stack.getItem());
        if (held != null) {
            return held == Fluids.EMPTY ? FluidAmount.EMPTY : FluidAmount.of(held, FluidAmount.BUCKET);
        }
        FluidHandler handler = Lookups.ITEM_FLUIDS.find(stack.copyWithCount(1), null);
        return handler == null || handler.tanks() == 0 ? FluidAmount.EMPTY : handler.fluid(0);
    }

    /** Takes up to {@code maxAmount} out of one item of {@code stack}. {@code stack} is not changed. */
    public static Result drain(ItemStack stack, long maxAmount, boolean simulate) {
        if (stack == null || stack.isEmpty() || maxAmount <= 0) {
            return none(stack);
        }
        Fluid held = bucketFluid(stack.getItem());
        if (held != null) {
            if (held == Fluids.EMPTY || maxAmount < FluidAmount.BUCKET) {
                return none(stack);
            }
            return new Result(FluidAmount.of(held, FluidAmount.BUCKET), new ItemStack(Items.BUCKET));
        }
        ItemStack one = stack.copyWithCount(1);
        FluidHandler handler = Lookups.ITEM_FLUIDS.find(one, null);
        if (handler == null) {
            return none(stack);
        }
        FluidAmount drained = handler.drain(maxAmount, simulate);
        return new Result(drained == null ? FluidAmount.EMPTY : drained, one);
    }

    /** Puts up to {@code offer} into one item of {@code stack}. {@code stack} is not changed. */
    public static Result fill(ItemStack stack, FluidAmount offer, boolean simulate) {
        if (stack == null || stack.isEmpty() || offer == null || offer.isEmpty()) {
            return none(stack);
        }
        Fluid held = bucketFluid(stack.getItem());
        if (held != null) {
            Fluid source = offer.fluid() instanceof FlowingFluid flowing ? flowing.getSource() : offer.fluid();
            if (held != Fluids.EMPTY || offer.amount() < FluidAmount.BUCKET) {
                return none(stack);
            }
            Item filled;
            try {
                filled = source.getBucket();
            } catch (RuntimeException noBucket) {
                return none(stack);
            }
            if (filled == null || filled == Items.AIR || bucketFluid(filled) != source) {
                return none(stack);
            }
            return new Result(FluidAmount.of(source, FluidAmount.BUCKET), new ItemStack(filled));
        }
        ItemStack one = stack.copyWithCount(1);
        FluidHandler handler = Lookups.ITEM_FLUIDS.find(one, null);
        if (handler == null) {
            return none(stack);
        }
        long accepted = handler.fill(offer, simulate);
        return new Result(accepted <= 0 ? FluidAmount.EMPTY : offer.withAmount(accepted), one);
    }

    private static Result none(ItemStack stack) {
        return new Result(FluidAmount.EMPTY, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    /** The fluid a bucket item holds ({@link Fluids#EMPTY} for the empty bucket), or null for other items. */
    private static Fluid bucketFluid(Item item) {
        // A bucket with a mob in it (fish, axolotl, tadpole) is not a fluid container:
        // emptying it into a tank would delete the mob.
        if (item instanceof BucketItem && !(item instanceof net.minecraft.world.item.MobBucketItem)) {
            Fluid content = ((BucketItemAccessor) item).weaver$content();
            return content == null ? Fluids.EMPTY : content;
        }
        return null;
    }
}
