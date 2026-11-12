package net.weaver.proof;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.extension.BlockExtension;
import net.weaver.api.game.extension.EntityExtension;
import net.weaver.api.game.extension.ItemExtension;
import net.weaver.api.game.extension.TriState;

/**
 * Block, item, and entity extensions on a real server level: each check puts the
 * extension where vanilla would read it and compares with a vanilla block, item,
 * or entity in the same place.
 */
final class ExtensionsProof {
    /** Climbable, slippery, plant soil, blast proof, eternal fire, sticky, and redstone-connecting. */
    static final class ExtensionBlock extends Block implements BlockExtension,
            net.minecraft.world.level.block.BeaconBeamBlock {
        static volatile Entity frictionEntity;
        static volatile boolean fireAsked;
        static volatile boolean usedWithItem;
        static volatile boolean burned;
        static volatile boolean flammable;
        static volatile Entity ranOver;
        static volatile int landingCount = -1;
        static volatile BlockPos noHarvestAt;
        static volatile BlockPos harvestedAt;
        static volatile int soundAsks;

        ExtensionBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        public boolean isClimbable(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
            return true;
        }

        @Override
        public float getFriction(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
            frictionEntity = entity;
            return 0.98F;
        }

        @Override
        public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos pos, BlockState plant) {
            return TriState.TRUE;
        }

        @Override
        public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
            return 3_600_000.0F;
        }

        @Override
        public boolean isFireSource(BlockState state, LevelReader level, BlockPos pos, Direction side) {
            fireAsked = true;
            return true;
        }

        @Override
        public boolean isStickyBlock(BlockState state) {
            return true;
        }

        @Override
        public TriState connectsToRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return TriState.TRUE;
        }

        @Override
        public boolean makesOpenTrapdoorAboveClimbable(BlockState state, LevelReader level, BlockPos pos,
                BlockState trapdoor) {
            return true;
        }

        @Override
        public int getFlammability(BlockState state) {
            return flammable ? 100 : 0;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state) {
            return flammable ? 50 : 0;
        }

        @Override
        public void onBurnedByFire(BlockState state, Level level, BlockPos pos) {
            burned = true;
        }

        @Override
        public net.minecraft.world.level.pathfinder.PathType getPathType(BlockState state, BlockGetter level, BlockPos pos) {
            return net.minecraft.world.level.pathfinder.PathType.DAMAGING;
        }

        @Override
        public float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) {
            return 15.0F;
        }

        @Override
        public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
            ranOver = entity;
            return true;
        }

        @Override
        public boolean addLandingEffects(BlockState state, ServerLevel level, BlockPos pos, LivingEntity entity, int count) {
            landingCount = count;
            return true;
        }

        @Override
        public TriState canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
            return pos.equals(noHarvestAt) ? TriState.FALSE : TriState.DEFAULT;
        }

        @Override
        public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
                net.minecraft.world.level.block.entity.BlockEntity blockEntity, ItemStack tool) {
            harvestedAt = pos.immutable();
            super.playerDestroy(level, player, pos, state, blockEntity, tool);
        }

        @Override
        public net.minecraft.world.level.block.SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos,
                Entity entity) {
            soundAsks++;
            return net.minecraft.world.level.block.SoundType.AMETHYST;
        }

        @Override
        public net.minecraft.world.item.DyeColor getColor() {
            return net.minecraft.world.item.DyeColor.WHITE;
        }

        @Override
        public Integer getBeaconColor(BlockState state, LevelReader level, BlockPos pos, BlockPos beaconPos) {
            return BEAM_COLOR;
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                Player player, InteractionHand hand, BlockHitResult hit) {
            usedWithItem = true;
            return InteractionResult.SUCCESS;
        }
    }

    /** Short-lived when dropped, piglin currency, a wrench, undroppable, and powder snow boots. */
    static final class ExtensionItem extends Item implements ItemExtension {
        static volatile int itemTicks;
        static volatile Entity attacked;
        static volatile Entity created;
        static volatile int stoppedWith = -1;

        ExtensionItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public boolean onItemEntityTick(ItemStack stack, ItemEntity entity) {
            itemTicks++;
            return false;
        }

        @Override
        public int getItemEntityLifespan(ItemStack stack, Level level) {
            return 20;
        }

        @Override
        public boolean isPiglinCurrency(ItemStack stack) {
            return true;
        }

        @Override
        public boolean usesBlockWhileSneaking(ItemStack stack, Level level, BlockPos pos, Player player) {
            return true;
        }

        @Override
        public boolean canBeDropped(ItemStack stack, Player player) {
            return false;
        }

        @Override
        public boolean allowsWalkingOnPowderSnow(ItemStack stack, LivingEntity wearer) {
            return true;
        }

        @Override
        public boolean canPerformAction(ItemStack stack, net.weaver.api.game.extension.ItemAction action) {
            return action == net.weaver.api.game.extension.ItemAction.SHEAR;
        }

        @Override
        public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
            return true;
        }

        @Override
        public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
            attacked = target;
            return true;
        }

        @Override
        public void onStopUsing(ItemStack stack, LivingEntity entity, int remainingTicks) {
            stoppedWith = remainingTicks;
        }

        @Override
        public ItemStack getCraftingRemainder(ItemStack stack) {
            return new ItemStack(Items.BOWL);
        }

        @Override
        public Entity createItemEntity(ServerLevel level, ItemEntity entity, ItemStack stack) {
            if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                return null;
            }
            SturdyItemEntity sturdy = new SturdyItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), stack.copy());
            created = sturdy;
            return sturdy;
        }
    }

    /** The entity the item drops as when named: a plain item entity of its own class. */
    static final class SturdyItemEntity extends ItemEntity {
        SturdyItemEntity(Level level, double x, double y, double z, ItemStack stack) {
            super(level, x, y, z, stack);
        }
    }

    /** A pig that does not trample farmland and walks on powder snow. */
    static final class ExtensionPig extends Pig implements EntityExtension {
        static volatile int added;
        static volatile int removed;

        ExtensionPig(EntityType<? extends Pig> type, Level level) {
            super(type, level);
        }

        @Override
        public boolean canTrampleFarmland(BlockState state, Level level, BlockPos pos, double fallDistance) {
            return false;
        }

        @Override
        public boolean walksOnPowderSnow() {
            return true;
        }

        @Override
        public void onAddedToLevel() {
            added++;
        }

        @Override
        public void onRemovedFromLevel() {
            removed++;
        }
    }

    static final int BEAM_COLOR = 0xFF123456;

    /** A mod bed: vanilla sleeping, occupancy, and respawning work through AbstractBedBlock. */
    static final class ProofBed extends net.minecraft.world.level.block.AbstractBedBlock {
        ProofBed(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected net.minecraft.world.attribute.EnvironmentAttribute<net.minecraft.world.attribute.BedRule> getBedEnvironmentAttribute() {
            return net.minecraft.world.attribute.EnvironmentAttributes.BED_RULE;
        }

        @Override
        protected InteractionResult destroyOnUse(BlockState state, Level level, BlockPos pos, Player player) {
            return InteractionResult.SUCCESS;
        }

        @Override
        protected void destroyOnLeave(Level level, BlockPos pos) {
        }
    }

    private static ProofBed bed;
    private static ExtensionBlock block;
    private static ExtensionItem item;

    /** From the register phase. */
    static void registerContent() {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath("weaverproof", "extension_block"));
        block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                new ExtensionBlock(BlockBehaviour.Properties.of().setId(blockKey).strength(1.0F)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("weaverproof", "extension_item"));
        item = Registry.register(BuiltInRegistries.ITEM, itemKey, new ExtensionItem(new Item.Properties().setId(itemKey)));
        ResourceKey<Block> bedKey = ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath("weaverproof", "proof_bed"));
        bed = Registry.register(BuiltInRegistries.BLOCK, bedKey,
                new ProofBed(BlockBehaviour.Properties.of().setId(bedKey).strength(0.2F)));
    }

    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;

    ExtensionsProof(ServerLevel level, BlockPos base, GameEventsProof proof) {
        this.level = level;
        this.base = base;
        this.proof = proof;
    }

    void run() {
        if (block == null || item == null) {
            proof.check(false, "extensions content not registered");
            return;
        }
        guard("block climbable", this::checkClimbable);
        guard("block friction", this::checkFriction);
        guard("block plant soil", this::checkPlantSoil);
        guard("block explosion resistance", this::checkExplosion);
        guard("block fire source", this::checkFireSource);
        guard("block sticky", this::checkSticky);
        guard("block redstone", this::checkRedstone);
        guard("item lifespan", this::checkItemEntity);
        guard("item piglin currency", this::checkPiglin);
        guard("item sneak use and drop", this::checkPlayerUse);
        guard("entity farmland", this::checkTrample);
        guard("powder snow", this::checkPowderSnow);
        guard("item shears", this::checkShears);
        guard("block trapdoor ladder", this::checkTrapdoorLadder);
        guard("item swing and attack", this::checkSwingAndAttack);
        guard("block flammability", this::checkFlammability);
        guard("item crafting remainder", this::checkCraftingRemainder);
        guard("item entity replaced", this::checkItemEntityReplaced);
        guard("block path type", this::checkPathType);
        guard("block enchant power", this::checkEnchantPower);
        guard("block running and landing effects", this::checkParticleEffects);
        guard("block beacon color", this::checkBeaconColor);
        guard("item stop using", this::checkStopUsing);
        guard("block harvest", this::checkHarvest);
        guard("entity added and removed", this::checkTracking);
        guard("mod bed", this::checkModBed);
        guard("enum extension", this::checkEnumExtension);
        guard("block sounds", this::checkSounds);
    }

    private BlockPos spot(int index) {
        return base.offset(index * 4, 0, 0);
    }

    private void place(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private void clear(BlockPos pos, int radius) {
        for (BlockPos each : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
            level.setBlock(each, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private Pig pigAt(BlockPos pos) {
        Pig pig = EntityTypes.PIG.spawn(level, pos, EntitySpawnReason.COMMAND);
        if (pig == null) {
            throw new IllegalStateException("pig did not spawn");
        }
        pig.setNoAi(true);
        return pig;
    }

    private void checkClimbable() {
        BlockPos pos = spot(0);
        BlockPos stone = pos.above(4);
        place(pos, block.defaultBlockState());
        place(stone, Blocks.STONE.defaultBlockState());
        Pig onBlock = pigAt(pos);
        Pig inStone = pigAt(stone);
        try {
            onBlock.setPos(Vec3.atBottomCenterOf(pos));
            inStone.setPos(Vec3.atBottomCenterOf(stone));
            boolean climbs = onBlock.onClimbable();
            boolean vanilla = !inStone.onClimbable();
            proof.check(climbs && vanilla, "block extension climbable=" + climbs + " stoneNot=" + vanilla);
        } finally {
            onBlock.discard();
            inStone.discard();
            clear(pos, 0);
            clear(stone, 0);
        }
    }

    private void checkFriction() {
        BlockPos pos = spot(1);
        place(pos, block.defaultBlockState());
        Pig pig = pigAt(pos.above());
        try {
            pig.setPos(Vec3.atBottomCenterOf(pos.above()));
            pig.setOnGround(true);
            ExtensionBlock.frictionEntity = null;
            pig.travel(new Vec3(0.0, 0.0, 1.0));
            boolean asked = ExtensionBlock.frictionEntity == pig;
            proof.check(asked, "block extension friction askedFor=" + ExtensionBlock.frictionEntity);
        } finally {
            pig.discard();
            clear(pos, 0);
        }
    }

    private void checkPlantSoil() {
        BlockPos pos = spot(2);
        BlockPos stone = pos.above(4);
        place(pos, block.defaultBlockState());
        place(stone, Blocks.STONE.defaultBlockState());
        try {
            BlockState poppy = Blocks.POPPY.defaultBlockState();
            boolean grows = poppy.canSurvive(level, pos.above());
            boolean vanilla = !poppy.canSurvive(level, stone.above());
            proof.check(grows && vanilla, "block extension plant soil poppy=" + grows + " stoneNot=" + vanilla);
        } finally {
            clear(pos, 0);
            clear(stone, 0);
        }
    }

    private void checkExplosion() {
        BlockPos pos = spot(3).above(8);
        // The explosion is centered in the air block above and south of the extension block; the dirt
        // sits right above the center, so vanilla always destroys it.
        BlockPos dirt = pos.above(2).south();
        place(pos, block.defaultBlockState());
        place(dirt, Blocks.DIRT.defaultBlockState());
        try {
            level.explode(null, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 1.5, 6.0F, Level.ExplosionInteraction.TNT);
            boolean stands = level.getBlockState(pos).is(block);
            boolean dirtGone = level.getBlockState(dirt).isAir();
            proof.check(stands && dirtGone, "block extension explosion stands=" + stands + " dirtGone=" + dirtGone);
        } finally {
            clear(pos, 3);
        }
    }

    private void checkFireSource() {
        BlockPos pos = spot(4).above(16);
        place(pos, block.defaultBlockState());
        place(pos.above(), Blocks.FIRE.defaultBlockState());
        // Vanilla fire only ticks near a player; -1 lets it tick anywhere for this check.
        var rules = level.getGameRules();
        var radius = net.minecraft.world.level.gamerules.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER;
        Integer previous = rules.get(radius);
        rules.set(radius, -1, level.getServer());
        try {
            ExtensionBlock.fireAsked = false;
            BlockState fire = level.getBlockState(pos.above());
            fire.tick(level, pos.above(), level.getRandom());
            boolean burning = level.getBlockState(pos.above()).is(Blocks.FIRE);
            proof.check(ExtensionBlock.fireAsked && burning,
                    "block extension fire source asked=" + ExtensionBlock.fireAsked + " burning=" + burning);
        } finally {
            rules.set(radius, previous, level.getServer());
            clear(pos, 1);
        }
    }

    private void checkSticky() {
        BlockPos piston = spot(5).above(24);
        BlockPos front = piston.east();
        BlockPos side = front.north();
        place(front, block.defaultBlockState());
        place(side, Blocks.STONE.defaultBlockState());
        try {
            PistonStructureResolver resolver = new PistonStructureResolver(level, piston, Direction.EAST, true);
            boolean resolved = resolver.resolve();
            boolean pulled = resolver.getToPush().contains(side);
            place(front, Blocks.STONE.defaultBlockState());
            PistonStructureResolver vanilla = new PistonStructureResolver(level, piston, Direction.EAST, true);
            vanilla.resolve();
            boolean vanillaLeaves = !vanilla.getToPush().contains(side);
            proof.check(resolved && pulled && vanillaLeaves,
                    "block extension sticky pulled=" + pulled + " stoneLeaves=" + vanillaLeaves);
        } finally {
            clear(front, 1);
        }
    }

    private void checkRedstone() throws ReflectiveOperationException {
        java.lang.reflect.Method connects = net.minecraft.world.level.block.RedstoneWireBlock.class.getDeclaredMethod(
                "shouldConnectTo", BlockState.class, BlockGetter.class, BlockPos.class, Direction.class);
        connects.setAccessible(true);
        BlockPos pos = spot(6);
        boolean extension = (boolean) connects.invoke(null, block.defaultBlockState(), level, pos, Direction.NORTH);
        boolean stone = (boolean) connects.invoke(null, Blocks.STONE.defaultBlockState(), level, pos, Direction.NORTH);
        proof.check(extension && !stone, "block extension redstone connects=" + extension + " stoneNot=" + !stone);
    }

    private void checkItemEntity() {
        BlockPos pos = spot(7).above(2);
        ItemEntity dropped = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, new ItemStack(item));
        ItemEntity stick = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, new ItemStack(Items.STICK));
        level.addFreshEntity(dropped);
        level.addFreshEntity(stick);
        try {
            ExtensionItem.itemTicks = 0;
            for (int i = 0; i < 25; i++) {
                if (!dropped.isRemoved()) {
                    dropped.tick();
                }
                stick.tick();
            }
            boolean despawned = dropped.isRemoved();
            boolean stickStays = !stick.isRemoved();
            proof.check(despawned && stickStays && ExtensionItem.itemTicks > 0, "item extension lifespan despawned="
                    + despawned + " stickStays=" + stickStays + " ticks=" + ExtensionItem.itemTicks);
        } finally {
            dropped.discard();
            stick.discard();
        }
    }

    private void checkPiglin() throws ReflectiveOperationException {
        java.lang.reflect.Method currency = net.minecraft.world.entity.monster.piglin.PiglinAi.class
                .getDeclaredMethod("isBarterCurrency", ItemStack.class);
        currency.setAccessible(true);
        boolean extension = (boolean) currency.invoke(null, new ItemStack(item));
        boolean gold = (boolean) currency.invoke(null, new ItemStack(Items.GOLD_INGOT));
        boolean stick = (boolean) currency.invoke(null, new ItemStack(Items.STICK));
        proof.check(extension && gold && !stick,
                "item extension piglin currency=" + extension + " gold=" + gold + " stickNot=" + !stick);
    }

    private void checkPlayerUse() {
        BlockPos pos = spot(8);
        place(pos, block.defaultBlockState());
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.nameUUIDFromBytes("weaver-extension-proof".getBytes()), "ExtensionProof"),
                ClientInformation.createDefault());
        try {
            player.setPos(Vec3.atBottomCenterOf(pos.above()));
            player.setShiftKeyDown(true);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);

            ItemStack wrench = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            ExtensionBlock.usedWithItem = false;
            player.gameMode.useItemOn(player, level, wrench, InteractionHand.MAIN_HAND, hit);
            boolean wrenchUses = ExtensionBlock.usedWithItem;

            ItemStack stick = new ItemStack(Items.STICK);
            player.setItemInHand(InteractionHand.MAIN_HAND, stick);
            ExtensionBlock.usedWithItem = false;
            player.gameMode.useItemOn(player, level, stick, InteractionHand.MAIN_HAND, hit);
            boolean stickSkips = !ExtensionBlock.usedWithItem;
            proof.check(wrenchUses && stickSkips,
                    "item extension sneak use wrench=" + wrenchUses + " stickSkips=" + stickSkips);

            player.getInventory().setSelectedItem(new ItemStack(item));
            player.drop(false);
            boolean kept = player.getInventory().getSelectedItem().is(item);
            proof.check(kept, "item extension drop kept=" + kept);
        } finally {
            clear(pos, 0);
        }
    }

    private void checkTrample() {
        BlockPos pos = spot(9);
        BlockPos other = pos.south(3);
        place(pos, Blocks.FARMLAND.defaultBlockState());
        place(other, Blocks.FARMLAND.defaultBlockState());
        ExtensionPig gentle = new ExtensionPig(EntityTypes.PIG, level);
        gentle.setPos(Vec3.atBottomCenterOf(pos.above()));
        level.addFreshEntity(gentle);
        Pig pig = pigAt(other.above());
        try {
            Blocks.FARMLAND.fallOn(level, level.getBlockState(pos), pos, gentle, 10.0);
            Blocks.FARMLAND.fallOn(level, level.getBlockState(other), other, pig, 10.0);
            boolean kept = level.getBlockState(pos).is(Blocks.FARMLAND);
            boolean trampled = level.getBlockState(other).is(Blocks.DIRT);
            proof.check(kept && trampled, "entity extension farmland kept=" + kept + " vanillaTrampled=" + trampled);
        } finally {
            gentle.discard();
            pig.discard();
            clear(pos, 0);
            clear(other, 0);
        }
    }

    private void checkPowderSnow() {
        BlockPos pos = spot(10).above(2);
        ExtensionPig light = new ExtensionPig(EntityTypes.PIG, level);
        light.setPos(Vec3.atBottomCenterOf(pos));
        Pig booted = pigAt(pos);
        Pig plain = pigAt(pos);
        try {
            booted.setItemSlot(EquipmentSlot.FEET, new ItemStack(item));
            boolean entity = PowderSnowBlock.canEntityWalkOnPowderSnow(light);
            boolean boots = PowderSnowBlock.canEntityWalkOnPowderSnow(booted);
            boolean sinks = !PowderSnowBlock.canEntityWalkOnPowderSnow(plain);
            proof.check(entity && boots && sinks,
                    "powder snow entity=" + entity + " boots=" + boots + " plainSinks=" + sinks);
        } finally {
            light.discard();
            booted.discard();
            plain.discard();
        }
    }

    private ServerPlayer player(String name) {
        return new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name), ClientInformation.createDefault());
    }

    /** The item shears a sheep, carves a pumpkin, and dispenses like shears; a stick does none of it. */
    private void checkShears() throws ReflectiveOperationException {
        BlockPos pos = spot(11);
        ServerPlayer player = player("ExtensionShears");
        player.setPos(Vec3.atBottomCenterOf(pos.above()));
        var sheep = EntityTypes.SHEEP.spawn(level, pos.above(), EntitySpawnReason.COMMAND);
        var other = EntityTypes.SHEEP.spawn(level, pos.above(), EntitySpawnReason.COMMAND);
        try {
            sheep.setNoAi(true);
            other.setNoAi(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            sheep.mobInteract(player, InteractionHand.MAIN_HAND);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            other.mobInteract(player, InteractionHand.MAIN_HAND);
            boolean sheared = sheep.isSheared();
            boolean stickNot = !other.isSheared();
            proof.check(sheared && stickNot, "item extension shears sheep=" + sheared + " stickNot=" + stickNot);

            place(pos, Blocks.PUMPKIN.defaultBlockState());
            ItemStack shears = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, shears);
            player.gameMode.useItemOn(player, level, shears, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
            boolean carved = level.getBlockState(pos).is(Blocks.CARVED_PUMPKIN);
            proof.check(carved, "item extension shears pumpkin carved=" + carved);

            java.lang.reflect.Method dispense = net.minecraft.world.level.block.DispenserBlock.class.getDeclaredMethod(
                    "getDispenseMethod", Level.class, ItemStack.class);
            dispense.setAccessible(true);
            Object behavior = dispense.invoke(Blocks.DISPENSER, level, new ItemStack(item));
            Object stickBehavior = dispense.invoke(Blocks.DISPENSER, level, new ItemStack(Items.STICK));
            Object shearsBehavior = net.minecraft.world.level.block.DispenserBlock.DISPENSER_REGISTRY.get(Items.SHEARS);
            boolean dispenses = shearsBehavior != null && behavior == shearsBehavior && stickBehavior != shearsBehavior;
            proof.check(dispenses, "item extension shears dispenser=" + dispenses);
        } finally {
            sheep.discard();
            other.discard();
            clear(pos, 1);
        }
    }

    /** An open trapdoor above the block is a ladder; above stone it is not. */
    private void checkTrapdoorLadder() {
        BlockPos pos = spot(12);
        BlockPos stone = pos.above(4);
        BlockState trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true);
        place(pos, block.defaultBlockState());
        place(pos.above(), trapdoor);
        place(stone, Blocks.STONE.defaultBlockState());
        place(stone.above(), trapdoor);
        Pig onBlock = pigAt(pos.above());
        Pig onStone = pigAt(stone.above());
        try {
            onBlock.setPos(Vec3.atBottomCenterOf(pos.above()));
            onStone.setPos(Vec3.atBottomCenterOf(stone.above()));
            boolean climbs = onBlock.onClimbable();
            boolean stoneNot = !onStone.onClimbable();
            proof.check(climbs && stoneNot, "block extension trapdoor ladder=" + climbs + " stoneNot=" + stoneNot);
        } finally {
            onBlock.discard();
            onStone.discard();
            clear(pos, 1);
            clear(stone, 1);
        }
    }

    /** The item cancels its swing and its attack; a stick swings. */
    private void checkSwingAndAttack() {
        BlockPos pos = spot(13);
        ServerPlayer player = player("ExtensionSwing");
        player.setPos(Vec3.atBottomCenterOf(pos));
        Pig pig = pigAt(pos);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
            boolean cancelled = !player.isSwinging();
            ExtensionItem.attacked = null;
            float health = pig.getHealth();
            player.attack(pig);
            boolean attackCancelled = ExtensionItem.attacked == pig && pig.getHealth() == health;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
            boolean stickSwings = player.isSwinging();
            proof.check(cancelled && stickSwings, "item extension swing cancelled=" + cancelled + " stickSwings=" + stickSwings);
            proof.check(attackCancelled, "item extension attack cancelled=" + attackCancelled);
        } finally {
            pig.discard();
        }
    }

    /** Fire reads the flammability and spread speed of the block, and tells it when it burned away. */
    private void checkFlammability() throws ReflectiveOperationException {
        ExtensionBlock.flammable = true;
        try {
            checkFlammabilityWhileFlammable();
        } finally {
            ExtensionBlock.flammable = false;
        }
    }

    private void checkFlammabilityWhileFlammable() throws ReflectiveOperationException {
        net.minecraft.world.level.block.FireBlock fire = (net.minecraft.world.level.block.FireBlock) Blocks.FIRE;
        java.lang.reflect.Method burnOdds = net.minecraft.world.level.block.FireBlock.class.getDeclaredMethod(
                "getBurnOdds", BlockState.class);
        java.lang.reflect.Method igniteOdds = net.minecraft.world.level.block.FireBlock.class.getDeclaredMethod(
                "getIgniteOdds", BlockState.class);
        burnOdds.setAccessible(true);
        igniteOdds.setAccessible(true);
        int burn = (int) burnOdds.invoke(fire, block.defaultBlockState());
        int ignite = (int) igniteOdds.invoke(fire, block.defaultBlockState());
        int stone = (int) burnOdds.invoke(fire, Blocks.STONE.defaultBlockState());
        proof.check(burn == 100 && ignite == 50 && stone == 0,
                "block extension flammability burn=" + burn + " ignite=" + ignite + " stone=" + stone);

        BlockPos pos = spot(14).above(4);
        place(pos, block.defaultBlockState());
        try {
            java.lang.reflect.Method checkBurnOut = net.minecraft.world.level.block.FireBlock.class.getDeclaredMethod(
                    "checkBurnOut", Level.class, BlockPos.class, int.class, net.minecraft.util.RandomSource.class, int.class);
            checkBurnOut.setAccessible(true);
            ExtensionBlock.burned = false;
            checkBurnOut.invoke(fire, level, pos, 1, level.getRandom(), 0);
            boolean gone = !level.getBlockState(pos).is(block);
            proof.check(ExtensionBlock.burned && gone, "block extension burned=" + ExtensionBlock.burned + " gone=" + gone);
        } finally {
            clear(pos, 1);
        }
    }

    /** The item leaves a bowl in the grid; a stick leaves nothing. */
    private void checkCraftingRemainder() {
        var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1,
                java.util.List.of(new ItemStack(item), new ItemStack(Items.STICK)));
        var remaining = net.minecraft.world.item.crafting.CraftingRecipe.defaultCraftingReminder(input);
        boolean bowl = remaining.get(0).is(Items.BOWL);
        boolean stickNothing = remaining.get(1).isEmpty();
        proof.check(bowl && stickNothing, "item extension crafting remainder bowl=" + bowl + " stickNothing=" + stickNothing);
    }

    /** A named stack of the item is added as its own entity; an unnamed one stays a plain item entity. */
    private void checkItemEntityReplaced() {
        BlockPos pos = base.above(3).south(3);
        ItemStack named = new ItemStack(item);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("sturdy"));
        ItemEntity plain = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, new ItemStack(item));
        ItemEntity replaced = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, named);
        ExtensionItem.created = null;
        boolean plainAdded = level.addFreshEntity(plain);
        boolean replacedAdded = level.addFreshEntity(replaced);
        Entity created = ExtensionItem.created;
        try {
            boolean sturdyIn = created instanceof SturdyItemEntity && level.getEntity(created.getUUID()) == created;
            boolean originalSkipped = level.getEntity(replaced.getUUID()) == null;
            boolean plainIn = plainAdded && level.getEntity(plain.getUUID()) == plain;
            proof.check(replacedAdded && sturdyIn && originalSkipped && plainIn, "item extension item entity replaced="
                    + sturdyIn + " added=" + replacedAdded + " originalSkipped=" + originalSkipped + " plainIn=" + plainIn);
        } finally {
            plain.discard();
            replaced.discard();
            if (created != null) {
                created.discard();
            }
        }
    }

    /** Mobs see the block as damaging; stone stays blocked. */
    private void checkPathType() throws ReflectiveOperationException {
        BlockPos pos = spot(16);
        BlockPos stone = pos.above(4);
        place(pos, block.defaultBlockState());
        place(stone, Blocks.STONE.defaultBlockState());
        try {
            java.lang.reflect.Method type = net.minecraft.world.level.pathfinder.WalkNodeEvaluator.class.getDeclaredMethod(
                    "getPathTypeFromState", BlockGetter.class, BlockPos.class);
            type.setAccessible(true);
            Object extension = type.invoke(null, level, pos);
            Object vanilla = type.invoke(null, level, stone);
            boolean damaging = extension == net.minecraft.world.level.pathfinder.PathType.DAMAGING;
            boolean stoneBlocked = vanilla == net.minecraft.world.level.pathfinder.PathType.BLOCKED;
            proof.check(damaging && stoneBlocked, "block extension path type=" + extension + " stone=" + vanilla);
        } finally {
            clear(pos, 0);
            clear(stone, 0);
        }
    }

    /** One block with power 15 next to a table gives the top cost of 30; one bookshelf does not. */
    private void checkEnchantPower() {
        BlockPos table = spot(17).above(30);
        clear(table, 3);
        place(table, Blocks.ENCHANTING_TABLE.defaultBlockState());
        place(table.offset(2, 0, 0), block.defaultBlockState());
        int withExtension = topCost(table);
        place(table.offset(2, 0, 0), Blocks.BOOKSHELF.defaultBlockState());
        int withBookshelf = topCost(table);
        try {
            proof.check(withExtension == 30 && withBookshelf < 30,
                    "block extension enchant power top=" + withExtension + " bookshelfTop=" + withBookshelf);
        } finally {
            clear(table, 3);
        }
    }

    /** A pig sprinting over and landing on the block: the block replaces both particle effects. */
    private void checkParticleEffects() throws ReflectiveOperationException {
        BlockPos pos = spot(18);
        place(pos, block.defaultBlockState());
        Pig pig = pigAt(pos.above());
        try {
            pig.setPos(Vec3.atBottomCenterOf(pos.above()));
            ExtensionBlock.ranOver = null;
            java.lang.reflect.Method sprint = Entity.class.getDeclaredMethod("spawnSprintParticle");
            sprint.setAccessible(true);
            sprint.invoke(pig);
            boolean running = ExtensionBlock.ranOver == pig;

            ExtensionBlock.landingCount = -1;
            pig.fallDistance = 10.0;
            pig.setOnGround(true);
            java.lang.reflect.Method fall = LivingEntity.class.getDeclaredMethod("checkFallDamage",
                    double.class, boolean.class, BlockState.class, BlockPos.class);
            fall.setAccessible(true);
            fall.invoke(pig, -1.0, true, block.defaultBlockState(), pos);
            boolean landing = ExtensionBlock.landingCount > 0;
            proof.check(running && landing, "block extension running=" + running
                    + " landingCount=" + ExtensionBlock.landingCount);
        } finally {
            pig.discard();
            clear(pos, 0);
        }
    }

    /**
     * A beam through the block takes its own color. Without a pyramid the beacon
     * reports no sections, so the computed sections are read from its field.
     */
    @SuppressWarnings("unchecked")
    private void checkBeaconColor() throws ReflectiveOperationException {
        BlockPos beacon = spot(19).above(40);
        clear(beacon, 2);
        place(beacon, Blocks.BEACON.defaultBlockState());
        place(beacon.above(), block.defaultBlockState());
        try {
            var entity = (net.minecraft.world.level.block.entity.BeaconBlockEntity) level.getBlockEntity(beacon);
            for (int i = 0; i < 4; i++) {
                net.minecraft.world.level.block.entity.BeaconBlockEntity.tick(level, beacon, level.getBlockState(beacon), entity);
            }
            java.lang.reflect.Field field = net.minecraft.world.level.block.entity.BeaconBlockEntity.class
                    .getDeclaredField("beamSections");
            field.setAccessible(true);
            var sections = (java.util.List<net.minecraft.world.level.block.entity.BeaconBeamOwner.Section>) field.get(entity);
            boolean colored = sections.stream().anyMatch(section -> section.getColor() == BEAM_COLOR);
            proof.check(colored, "block extension beacon color=" + colored + " colors="
                    + sections.stream().map(section -> Integer.toHexString(section.getColor())).toList());
        } finally {
            clear(beacon, 2);
        }
    }

    /** Stopping a use reports the remaining ticks to the item. */
    private void checkStopUsing() {
        ServerPlayer player = player("ExtensionUser");
        player.setPos(Vec3.atBottomCenterOf(spot(20)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        ExtensionItem.stoppedWith = -1;
        player.startUsingItem(InteractionHand.MAIN_HAND);
        boolean using = player.isUsingItem();
        player.stopUsingItem();
        proof.check(using && ExtensionItem.stoppedWith >= 0,
                "item extension stop using using=" + using + " remaining=" + ExtensionItem.stoppedWith);
    }

    /** At one position the block cannot be harvested: slower mining and no drops; elsewhere vanilla decides. */
    private void checkHarvest() {
        BlockPos refused = spot(21);
        BlockPos allowed = spot(21).above(4);
        place(refused, block.defaultBlockState());
        place(allowed, block.defaultBlockState());
        ServerPlayer player = player("ExtensionMiner");
        player.setPos(Vec3.atBottomCenterOf(refused.above()));
        ExtensionBlock.noHarvestAt = refused;
        try {
            float slow = level.getBlockState(refused).getDestroyProgress(player, level, refused);
            float fast = level.getBlockState(allowed).getDestroyProgress(player, level, allowed);
            ExtensionBlock.harvestedAt = null;
            player.gameMode.destroyBlock(refused);
            boolean refusedDrops = ExtensionBlock.harvestedAt == null;
            player.gameMode.destroyBlock(allowed);
            boolean allowedDrops = allowed.equals(ExtensionBlock.harvestedAt);
            proof.check(slow < fast && refusedDrops && allowedDrops, "block extension harvest slow=" + slow
                    + " fast=" + fast + " refusedNoDrops=" + refusedDrops + " allowedDrops=" + allowedDrops);
        } finally {
            ExtensionBlock.noHarvestAt = null;
            clear(refused, 0);
            clear(allowed, 0);
        }
    }

    /** The level tells the entity when it starts and stops tracking it. */
    private void checkTracking() {
        ExtensionPig pig = new ExtensionPig(EntityTypes.PIG, level);
        pig.setPos(Vec3.atBottomCenterOf(base.above(3).south(5)));
        ExtensionPig.added = 0;
        ExtensionPig.removed = 0;
        level.addFreshEntity(pig);
        int added = ExtensionPig.added;
        pig.discard();
        int removed = ExtensionPig.removed;
        proof.check(added == 1 && removed == 1, "entity extension tracking added=" + added + " removed=" + removed);
    }

    /** A player sleeps in the mod bed (it becomes occupied), wakes up, and can respawn at it. */
    private void checkModBed() throws ReflectiveOperationException {
        BlockPos foot = spot(22);
        BlockPos head = foot.north();
        clear(foot, 2);
        // A floor around the bed: respawning stands the player up next to it.
        for (BlockPos floor : BlockPos.betweenClosed(foot.offset(-2, -1, -3), foot.offset(2, -1, 2))) {
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockState footState = bed.defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH)
                .setValue(net.minecraft.world.level.block.AbstractBedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT);
        level.setBlock(foot, footState, Block.UPDATE_CLIENTS);
        level.setBlock(head, footState.setValue(net.minecraft.world.level.block.AbstractBedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD), Block.UPDATE_CLIENTS);
        ServerPlayer player = player("ExtensionSleeper");
        player.setPos(Vec3.atBottomCenterOf(foot.east()));
        try {
            boolean slept = player.startSleeping(head);
            boolean occupied = level.getBlockState(head).getValue(net.minecraft.world.level.block.AbstractBedBlock.OCCUPIED);
            Direction facing = player.getBedOrientation();
            player.stopSleeping();
            boolean freed = !level.getBlockState(head).getValue(net.minecraft.world.level.block.AbstractBedBlock.OCCUPIED);
            java.lang.reflect.Method respawn = ServerPlayer.class.getDeclaredMethod("findRespawnAndUseSpawnBlock",
                    ServerLevel.class, ServerPlayer.RespawnConfig.class, boolean.class);
            respawn.setAccessible(true);
            var config = new ServerPlayer.RespawnConfig(
                    net.minecraft.world.level.storage.LevelData.RespawnData.of(level.dimension(), head, 0.0F, 0.0F), false);
            boolean respawns = ((java.util.Optional<?>) respawn.invoke(null, level, config, false)).isPresent();
            proof.check(slept && occupied && facing != null && freed && respawns, "mod bed slept=" + slept
                    + " occupied=" + occupied + " facing=" + facing + " freed=" + freed + " respawns=" + respawns);
        } finally {
            clear(foot, 2);
            for (BlockPos floor : BlockPos.betweenClosed(foot.offset(-2, -1, -3), foot.offset(2, -1, 2))) {
                level.setBlock(floor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The proof adds WEAVERPROOF_MYTHIC to Rarity; vanilla code built from values() sees it. */
    private void checkEnumExtension() {
        net.minecraft.world.item.Rarity mythic = net.weaver.api.EnumExtensions.get(
                net.minecraft.world.item.Rarity.class, "WEAVERPROOF_MYTHIC");
        net.minecraft.world.item.Rarity[] values = net.minecraft.world.item.Rarity.values();
        boolean last = values[values.length - 1] == mythic && mythic.ordinal() == values.length - 1;
        var decoded = net.minecraft.world.item.Rarity.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                new com.google.gson.JsonPrimitive("weaverproof_mythic")).result().orElse(null);
        boolean byId = net.minecraft.world.item.Rarity.BY_ID.apply(mythic.ordinal()) == mythic;
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(net.minecraft.core.component.DataComponents.RARITY, mythic);
        boolean onItem = stack.getRarity() == mythic && mythic.color() == net.minecraft.ChatFormatting.LIGHT_PURPLE;
        proof.check(last && decoded == mythic && byId && onItem, "enum extension rarity=" + mythic
                + " ordinal=" + mythic.ordinal() + " of=" + values.length + " codec=" + decoded + " byId=" + byId
                + " onItem=" + onItem);
    }

    /** A player stepping on and a pig landing on the block get its sound by position. */
    private void checkSounds() throws ReflectiveOperationException {
        BlockPos pos = spot(23);
        place(pos, block.defaultBlockState());
        Pig pig = pigAt(pos.above());
        try {
            pig.setPos(Vec3.atBottomCenterOf(pos.above()));
            ExtensionBlock.soundAsks = 0;
            java.lang.reflect.Method step = Entity.class.getDeclaredMethod("playStepSound", BlockPos.class, BlockState.class);
            step.setAccessible(true);
            // Pigs play their own step sound; a player plays the sound of the block.
            ServerPlayer walker = player("ExtensionWalker");
            walker.setPos(Vec3.atBottomCenterOf(pos.above()));
            step.invoke(walker, pos, block.defaultBlockState());
            int afterStep = ExtensionBlock.soundAsks;
            java.lang.reflect.Method fall = LivingEntity.class.getDeclaredMethod("playBlockFallSound");
            fall.setAccessible(true);
            fall.invoke(pig);
            int afterFall = ExtensionBlock.soundAsks;
            proof.check(afterStep == 1 && afterFall == 2, "block extension sounds step=" + afterStep + " fall=" + afterFall);
        } finally {
            pig.discard();
            clear(pos, 0);
        }
    }

    private int topCost(BlockPos table) {
        ServerPlayer player = player("ExtensionEnchanter");
        var menu = new net.minecraft.world.inventory.EnchantmentMenu(1, player.getInventory(),
                net.minecraft.world.inventory.ContainerLevelAccess.create(level, table));
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
        return menu.costs[2];
    }

    private void guard(String name, ProofStep check) {
        try {
            check.run();
        } catch (Throwable failure) {
            proof.check(false, "extension " + name + " unexpected " + failure);
        }
    }

    private interface ProofStep {
        void run() throws Exception;
    }
}
