package net.weaver.api.game.lookup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The standard lookups for moving items, fluids, and energy between mods.
 * The context of block and entity lookups is the side being accessed, or
 * {@code null} for no particular side.
 *
 * <p>Vanilla containers are item handlers without any registration: block
 * entities that are a {@code Container} (a double chest counts as one
 * container, a furnace exposes its per-side slots), blocks such as the
 * composter, and entities that are a {@code Container} such as chest
 * minecarts. These are fallbacks, so a provider registered for the block, the
 * block entity type, or the entity type wins.</p>
 */
public final class Lookups {
    /** Item slots of a block. */
    public static final BlockLookup<ItemHandler, Direction> BLOCK_ITEMS =
            BlockLookup.get(id("items"), ItemHandler.class, Direction.class);
    /** Item slots of an entity. */
    public static final EntityLookup<ItemHandler, Direction> ENTITY_ITEMS =
            EntityLookup.get(id("items"), ItemHandler.class, Direction.class);
    /** Item slots inside an item stack (a backpack, a shulker box). */
    public static final ItemLookup<ItemHandler, Void> ITEM_ITEMS =
            ItemLookup.get(id("items"), ItemHandler.class, Void.class);

    /** Fluid tanks of a block. */
    public static final BlockLookup<FluidHandler, Direction> BLOCK_FLUIDS =
            BlockLookup.get(id("fluids"), FluidHandler.class, Direction.class);
    /** Fluid tanks of an entity. */
    public static final EntityLookup<FluidHandler, Direction> ENTITY_FLUIDS =
            EntityLookup.get(id("fluids"), FluidHandler.class, Direction.class);
    /** Fluid tanks inside an item stack. */
    public static final ItemLookup<FluidHandler, Void> ITEM_FLUIDS =
            ItemLookup.get(id("fluids"), FluidHandler.class, Void.class);

    /** Stored energy of a block. */
    public static final BlockLookup<EnergyHandler, Direction> BLOCK_ENERGY =
            BlockLookup.get(id("energy"), EnergyHandler.class, Direction.class);
    /** Stored energy of an entity. */
    public static final EntityLookup<EnergyHandler, Direction> ENTITY_ENERGY =
            EntityLookup.get(id("energy"), EnergyHandler.class, Direction.class);
    /** Stored energy of an item stack (a battery). */
    public static final ItemLookup<EnergyHandler, Void> ITEM_ENERGY =
            ItemLookup.get(id("energy"), EnergyHandler.class, Void.class);

    static {
        BLOCK_ITEMS.registerFallback(Lookups::vanillaBlockItems);
        ENTITY_ITEMS.registerFallback(Lookups::vanillaEntityItems);
        BLOCK_FLUIDS.registerFallback(Lookups::vanillaBlockFluids);
    }

    /** Vanilla cauldrons as one-bucket tanks; a fallback, so a mod's provider for the block wins. */
    private static FluidHandler vanillaBlockFluids(Level level, BlockPos pos, BlockState state,
            BlockEntity blockEntity, Direction side) {
        return CauldronFluidHandler.handles(state.getBlock()) ? new CauldronFluidHandler(level, pos) : null;
    }

    private Lookups() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("weaver", path);
    }

    private static ItemHandler vanillaBlockItems(Level level, BlockPos pos, BlockState state,
            BlockEntity blockEntity, Direction side) {
        if (state.getBlock() instanceof WorldlyContainerHolder holder) {
            Container container = holder.getContainer(state, level, pos);
            return container == null ? null : ContainerItemHandler.of(container, side);
        }
        if (!(blockEntity instanceof Container container)) {
            return null;
        }
        if (state.getBlock() instanceof ChestBlock chest) {
            Container combined = ChestBlock.getContainer(chest, state, level, pos, true);
            if (combined != null) {
                return ContainerItemHandler.of(combined, side);
            }
        }
        return ContainerItemHandler.of(container, side);
    }

    private static ItemHandler vanillaEntityItems(Entity entity, Direction side) {
        return entity instanceof Container container && entity.isAlive()
                ? ContainerItemHandler.of(container, side) : null;
    }
}
