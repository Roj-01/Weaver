package net.weaver.proof;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.AttachmentType;
import net.weaver.api.capability.Capability;
import net.weaver.api.data.DataCodecs;
import net.weaver.api.game.lookup.BlockLookup;
import net.weaver.api.game.lookup.EnergyHandler;
import net.weaver.api.game.lookup.FluidAmount;
import net.weaver.api.game.lookup.FluidHandler;
import net.weaver.api.game.lookup.ItemHandler;
import net.weaver.api.game.lookup.Lookups;
import net.weaver.api.game.lookup.SimpleEnergyHandler;
import net.weaver.api.game.lookup.SimpleFluidTank;

/**
 * Lookups: vanilla containers as item handlers (chest, furnace sides, chest
 * minecart), providers per block, block entity type, entity type, and item,
 * provider order, an item energy handler saved in the stack, and shared ids.
 */
final class GameEventsLookupProof {
    private static final Capability<Long> STACK_ENERGY = AttachmentType.builder("weaverproof:stack_energy", Long.class)
            .codec(DataCodecs.varLong(), 12).build();

    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;

    GameEventsLookupProof(ServerLevel level, BlockPos base, GameEventsProof proof) {
        this.level = level;
        this.base = base;
        this.proof = proof;
    }

    void run() {
        guard("chest item handler", this::checkChest);
        guard("furnace sides", this::checkFurnace);
        guard("entity item handler", this::checkMinecart);
        guard("block providers", this::checkProviders);
        guard("item energy", this::checkItemEnergy);
        guard("lookup ids", this::checkIds);
    }

    private void checkChest() {
        BlockPos pos = base;
        BlockState before = level.getBlockState(pos);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        try {
            ItemHandler handler = Lookups.BLOCK_ITEMS.find(level, pos, Direction.UP);
            ItemStack offer = new ItemStack(Items.DIAMOND, 70);
            ItemStack simulated = handler.insert(0, offer, true);
            boolean untouched = handler.stack(0).isEmpty();
            ItemStack left = handler.insert(0, offer, false);
            ItemStack taken = handler.extract(0, 10, false);
            Container chest = (Container) level.getBlockEntity(pos);
            boolean ok = handler.slots() == 27 && simulated.getCount() == 6 && untouched && left.getCount() == 6
                    && taken.getCount() == 10 && chest.getItem(0).getCount() == 54 && offer.getCount() == 70;
            proof.check(ok, "chest item handler slots=" + handler.slots() + " left=" + left.getCount()
                    + " inChest=" + chest.getItem(0).getCount());
        } finally {
            ((Container) level.getBlockEntity(pos)).clearContent();
            level.setBlock(pos, before, 3);
        }
    }

    private void checkFurnace() {
        BlockPos pos = base.east(2);
        BlockState before = level.getBlockState(pos);
        level.setBlock(pos, Blocks.FURNACE.defaultBlockState(), 3);
        try {
            ItemHandler top = Lookups.BLOCK_ITEMS.find(level, pos, Direction.UP);
            ItemHandler bottom = Lookups.BLOCK_ITEMS.find(level, pos, Direction.DOWN);
            ItemStack left = top.insert(0, new ItemStack(Items.RAW_IRON, 3), false);
            Container furnace = (Container) level.getBlockEntity(pos);
            boolean toInput = left.isEmpty() && furnace.getItem(0).is(Items.RAW_IRON);
            // The bottom exposes the result and fuel slots, not the input.
            boolean bottomHidesInput = bottom.slots() == 2 && !bottom.stack(0).is(Items.RAW_IRON)
                    && !bottom.stack(1).is(Items.RAW_IRON);
            proof.check(top.slots() == 1 && toInput && bottomHidesInput,
                    "furnace sides top=" + top.slots() + " bottom=" + bottom.slots() + " input=" + toInput);
            furnace.clearContent();
        } finally {
            level.setBlock(pos, before, 3);
        }
    }

    private void checkMinecart() {
        Entity cart = EntityTypes.CHEST_MINECART.create(level, EntitySpawnReason.COMMAND);
        ItemHandler handler = cart == null ? null : Lookups.ENTITY_ITEMS.find(cart, null);
        Entity pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        boolean pigHasNone = pig != null && Lookups.ENTITY_ITEMS.find(pig, null) == null;
        boolean ok = handler != null && handler.slots() == 27
                && handler.insert(3, new ItemStack(Items.APPLE), false).isEmpty()
                && ((Container) cart).getItem(3).is(Items.APPLE) && pigHasNone;
        proof.check(ok, "entity item handler cart=" + (handler == null ? -1 : handler.slots()) + " pigNone=" + pigHasNone);
    }

    private void checkProviders() {
        SimpleEnergyHandler lodestone = new SimpleEnergyHandler(100);
        SimpleEnergyHandler barrelEnergy = new SimpleEnergyHandler(200);
        SimpleEnergyHandler pigEnergy = new SimpleEnergyHandler(300);
        SimpleFluidTank barrelTank = new SimpleFluidTank(4 * FluidAmount.BUCKET, fluid -> fluid.isSame(Fluids.WATER));
        Lookups.BLOCK_ENERGY.registerForBlocks((lvl, pos, state, be, side) -> lodestone, Blocks.LODESTONE);
        Lookups.BLOCK_ENERGY.registerForBlockEntities((barrel, side) -> side == Direction.DOWN ? null : barrelEnergy,
                BlockEntityTypes.BARREL);
        Lookups.BLOCK_FLUIDS.registerForBlockEntities((barrel, side) -> barrelTank, BlockEntityTypes.BARREL);
        Lookups.ENTITY_ENERGY.registerForEntities((entity, side) -> pigEnergy, EntityTypes.PIG);

        BlockPos lodestonePos = base.south(2);
        BlockPos barrelPos = base.south(4);
        BlockState lodestoneBefore = level.getBlockState(lodestonePos);
        BlockState barrelBefore = level.getBlockState(barrelPos);
        level.setBlock(lodestonePos, Blocks.LODESTONE.defaultBlockState(), 3);
        level.setBlock(barrelPos, Blocks.BARREL.defaultBlockState(), 3);
        try {
            EnergyHandler atLodestone = Lookups.BLOCK_ENERGY.find(level, lodestonePos, null);
            EnergyHandler atBarrel = Lookups.BLOCK_ENERGY.find(level, barrelPos, Direction.NORTH);
            EnergyHandler barrelBottom = Lookups.BLOCK_ENERGY.find(level, barrelPos, Direction.DOWN);
            EnergyHandler atStone = Lookups.BLOCK_ENERGY.find(level, base.below(), null);
            Entity pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
            EnergyHandler onPig = Lookups.ENTITY_ENERGY.find(pig, null);
            // A barrel is still a vanilla container for items, and a tank for fluids.
            ItemHandler barrelItems = Lookups.BLOCK_ITEMS.find(level, barrelPos, Direction.UP);
            FluidHandler tank = Lookups.BLOCK_FLUIDS.find(level, barrelPos, Direction.UP);
            long filled = tank.fill(FluidAmount.of(Fluids.WATER, 5 * FluidAmount.BUCKET), false);
            long lava = tank.fill(FluidAmount.of(Fluids.LAVA, FluidAmount.BUCKET), false);
            FluidAmount drained = tank.drain(FluidAmount.BUCKET, false);
            long moved = atLodestone.insert(150, false);
            boolean ok = atLodestone == lodestone && atBarrel == barrelEnergy && barrelBottom == null
                    && atStone == null && onPig == pigEnergy && barrelItems != null && barrelItems.slots() == 27
                    && filled == 4 * FluidAmount.BUCKET && lava == 0 && drained.amount() == FluidAmount.BUCKET
                    && moved == 100 && lodestone.stored() == 100;
            proof.check(ok, "block providers lodestone=" + (atLodestone == lodestone) + " barrel="
                    + (atBarrel == barrelEnergy) + " bottom=" + barrelBottom + " pig=" + (onPig == pigEnergy)
                    + " filled=" + filled + " lava=" + lava + " moved=" + moved);
        } finally {
            level.setBlock(lodestonePos, lodestoneBefore, 3);
            level.setBlock(barrelPos, barrelBefore, 3);
        }
    }

    private void checkItemEnergy() {
        AttachmentRegistry registry = AttachmentRegistry.global();
        if (registry.find(STACK_ENERGY.id()).isEmpty()) {
            registry.register(STACK_ENERGY);
        }
        // A battery item: the handler reads and writes the stack's own saved data.
        Lookups.ITEM_ENERGY.registerForItems((stack, context) -> new SimpleEnergyHandler(1000, 1000, 1000,
                registry.get(stack, STACK_ENERGY) == null ? 0 : registry.get(stack, STACK_ENERGY)) {
            @Override
            protected void onChanged() {
                registry.set(stack, STACK_ENERGY, stored());
            }
        }, Items.AMETHYST_SHARD);
        ItemStack battery = new ItemStack(Items.AMETHYST_SHARD);
        long accepted = Lookups.ITEM_ENERGY.find(battery, null).insert(250, false);
        ItemStack copy = battery.copy();
        long storedInCopy = Lookups.ITEM_ENERGY.find(copy, null).stored();
        boolean none = Lookups.ITEM_ENERGY.find(new ItemStack(Items.STICK), null) == null
                && Lookups.ITEM_ENERGY.find(ItemStack.EMPTY, null) == null;
        proof.check(accepted == 250 && storedInCopy == 250 && none,
                "item energy accepted=" + accepted + " copy=" + storedInCopy + " none=" + none);
    }

    private void checkIds() {
        Identifier id = Identifier.fromNamespaceAndPath("weaver", "energy");
        boolean shared = BlockLookup.get(id, EnergyHandler.class, Direction.class) == Lookups.BLOCK_ENERGY;
        boolean rejected;
        try {
            BlockLookup.get(id, ItemHandler.class, Direction.class);
            rejected = false;
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        proof.check(shared && rejected, "lookup ids shared=" + shared + " rejected=" + rejected);
    }

    private void guard(String name, ThrowingCheck check) {
        try {
            check.run();
        } catch (Throwable failure) {
            proof.check(false, name + " unexpected " + failure);
        } finally {
            proof.clearListeners();
        }
    }

    private interface ThrowingCheck {
        void run() throws Exception;
    }
}
