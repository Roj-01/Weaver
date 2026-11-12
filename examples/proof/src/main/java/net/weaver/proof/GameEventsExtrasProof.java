package net.weaver.proof;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.WeaverMods;
import net.weaver.api.game.event.block.FluidPlaceBlockEvent;
import net.weaver.api.game.event.living.EffectParticleModificationEvent;
import net.weaver.api.game.event.living.GetEquipmentDropChanceEvent;
import net.weaver.api.game.event.living.LivingGetProjectileEvent;
import net.weaver.api.game.event.living.LivingVisibilityEvent;
import net.weaver.api.game.event.support.ModMismatchCheck;
import net.weaver.api.game.event.world.EnchantedBlockLootEvent;
import net.weaver.api.game.event.world.EntityConstructingEvent;
import net.weaver.api.game.event.world.EntityEnteringSectionEvent;
import net.weaver.api.game.event.world.EntitySizeEvent;
import net.weaver.api.game.event.world.EntityTeleportEvent;
import net.weaver.api.game.event.world.ModMismatchEvent;

/**
 * Events added from the deferred list: entity shape and sections, perception,
 * ammunition, drop chances, loot enchantment levels, fluid placement,
 * /spreadplayers and the recorded mod list. Events that need world generation,
 * a player or a kill with loot (orb targeting, spawn cluster size, ground
 * alteration, looting level, lava spreading) are covered by the mixin-applied check.
 */
final class GameEventsExtrasProof {
    private static final AtomicInteger COMPONENTS_BOUND = new AtomicInteger();
    private static volatile boolean componentsReady;

    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;

    GameEventsExtrasProof(ServerLevel level, BlockPos base, GameEventsProof proof) {
        this.level = level;
        this.base = base;
        this.proof = proof;
    }

    /** Listens for events that fire before the world exists. */
    static void installEarly(net.weaver.api.ModContext context) {
        context.events().addListener(net.weaver.api.game.event.world.DefaultDataComponentsBoundEvent.class, event -> {
            if (!event.client() && event.registries() != null) {
                COMPONENTS_BOUND.incrementAndGet();
                componentsReady = !Items.DIAMOND_SWORD.components().isEmpty();
            }
        });
        context.events().addListener(net.weaver.api.game.event.world.RegisterStructureConversionsEvent.class, event -> {
            event.register("weaverproof_tower", "minecraft:village_plains");
            event.registerBiomeMapped("weaverproof_ruin", java.util.Map.of(
                    java.util.List.of("minecraft:desert"), "minecraft:desert_pyramid"), "minecraft:jungle_pyramid");
        });
    }

    void run() {
        guard("entity constructing", this::checkConstructing);
        guard("entity size", this::checkSize);
        guard("entity section", this::checkSection);
        guard("living visibility", this::checkVisibility);
        guard("living projectile", this::checkProjectile);
        guard("effect particles", this::checkEffectParticles);
        guard("equipment drop chance", this::checkDropChance);
        guard("enchanted block loot", this::checkBlockLoot);
        guard("fluid place block", this::checkFluidPlace);
        guard("spread players", this::checkSpreadPlayers);
        guard("mod list", this::checkModList);
        guard("structure conversions", this::checkStructureConversions);
        guard("crash shield", this::checkCrashShield);
        guard("lookup cache", this::checkLookupCache);
        guard("conventional tags", this::checkConventionalTags);
        guard("fluid type", this::checkFluidType);
        guard("vanilla fluid handlers", this::checkVanillaFluids);
        guard("teleport command", this::checkTeleportCommand);
        guard("attach on first use", this::checkAttach);
        proof.check(COMPONENTS_BOUND.get() >= 1 && componentsReady,
                "default components bound fired=" + COMPONENTS_BOUND.get() + " ready=" + componentsReady);
    }

    private void checkConstructing() {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(EntityConstructingEvent.class, event -> {
            if (event.entity() instanceof Pig) {
                fired.incrementAndGet();
            }
        });
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        proof.check(pig != null && fired.get() == 1, "entity constructing fired=" + fired.get());
    }

    private void checkSize() {
        Pig pig = spawnPig(base);
        try {
            float vanilla = pig.getBbWidth();
            proof.listen(EntitySizeEvent.class, event -> {
                if (event.entity() == pig) {
                    event.setNewDimensions(EntityDimensions.scalable(2.5F, 3.0F));
                }
            });
            pig.refreshDimensions();
            boolean changed = pig.getBbWidth() == 2.5F && pig.getBbHeight() == 3.0F;
            proof.clearListeners();
            pig.refreshDimensions();
            boolean restored = pig.getBbWidth() == vanilla;
            proof.check(changed && restored, "entity size changed=" + changed + " restored=" + restored);
        } finally {
            pig.discard();
        }
    }

    private void checkSection() {
        Pig pig = spawnPig(base);
        try {
            AtomicReference<EntityEnteringSectionEvent> seen = new AtomicReference<>();
            proof.listen(EntityEnteringSectionEvent.class, event -> {
                if (event.entity() == pig) {
                    seen.set(event);
                }
            });
            long before = SectionPos.asLong(pig.blockPosition());
            pig.setPos(pig.getX() + 16.0, pig.getY(), pig.getZ());
            EntityEnteringSectionEvent event = seen.get();
            boolean ok = event != null && event.oldSection() == before
                    && event.newSection() == SectionPos.asLong(pig.blockPosition()) && event.didChunkChange();
            proof.check(ok, "entity section event=" + (event == null ? null : event.oldPos() + "->" + event.newPos()));
        } finally {
            pig.discard();
        }
    }

    private void checkVisibility() {
        Mob zombie = EntityTypes.ZOMBIE.spawn(level, base, EntitySpawnReason.COMMAND);
        Pig pig = spawnPig(base);
        try {
            double vanilla = zombie.getVisibilityPercent(level, pig);
            proof.listen(LivingVisibilityEvent.class, event -> {
                if (event.entity() == zombie) {
                    event.modifyVisibility(0.25);
                }
            });
            double changed = zombie.getVisibilityPercent(level, pig);
            proof.check(vanilla > 0 && Math.abs(changed - vanilla * 0.25) < 1.0E-9,
                    "living visibility vanilla=" + vanilla + " changed=" + changed);
        } finally {
            zombie.discard();
            pig.discard();
        }
    }

    private void checkProjectile() {
        Mob skeleton = EntityTypes.SKELETON.spawn(level, base, EntitySpawnReason.COMMAND);
        Mob zombie = EntityTypes.ZOMBIE.spawn(level, base, EntitySpawnReason.COMMAND);
        try {
            ItemStack bow = new ItemStack(Items.BOW);
            boolean vanillaArrow = skeleton.getProjectile(bow).is(Items.ARROW);
            AtomicInteger fired = new AtomicInteger();
            proof.listen(LivingGetProjectileEvent.class, event -> {
                fired.incrementAndGet();
                event.setProjectile(new ItemStack(Items.SPECTRAL_ARROW));
            });
            boolean monster = skeleton.getProjectile(bow).is(Items.SPECTRAL_ARROW);
            boolean living = zombie.getProjectile(bow).is(Items.SPECTRAL_ARROW);
            proof.check(vanillaArrow && monster && living && fired.get() == 2,
                    "living projectile vanilla=" + vanillaArrow + " monster=" + monster + " living=" + living);
        } finally {
            skeleton.discard();
            zombie.discard();
        }
    }

    private void checkEffectParticles() throws ReflectiveOperationException {
        Mob zombie = EntityTypes.ZOMBIE.spawn(level, base, EntitySpawnReason.COMMAND);
        try {
            zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
            AtomicInteger hidden = new AtomicInteger();
            proof.listen(EffectParticleModificationEvent.class, event -> {
                if (event.entity() == zombie && event.effect().is(MobEffects.SPEED)) {
                    event.setVisible(false);
                    hidden.incrementAndGet();
                }
            });
            Method update = net.minecraft.world.entity.LivingEntity.class
                    .getDeclaredMethod("updateSynchronizedMobEffectParticles");
            update.setAccessible(true);
            update.invoke(zombie);
            java.lang.reflect.Field field = net.minecraft.world.entity.LivingEntity.class
                    .getDeclaredField("DATA_EFFECT_PARTICLES");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<?> particles = (List<?>) zombie.getEntityData()
                    .get((net.minecraft.network.syncher.EntityDataAccessor<List<?>>) field.get(null));
            proof.check(hidden.get() == 1 && particles.isEmpty(),
                    "effect particles hidden=" + hidden.get() + " particles=" + particles);
        } finally {
            zombie.discard();
        }
    }

    private void checkDropChance() {
        Mob zombie = EntityTypes.ZOMBIE.spawn(level, base, EntitySpawnReason.COMMAND);
        try {
            proof.listen(GetEquipmentDropChanceEvent.class, event -> {
                if (event.entity() == zombie) {
                    event.setDropChance(1.0F);
                }
            });
            float chance = EnchantmentHelper.processEquipmentDropChance(level, zombie,
                    level.damageSources().generic(), 0.085F);
            proof.check(chance == 1.0F, "equipment drop chance=" + chance);
        } finally {
            zombie.discard();
        }
    }

    private void checkBlockLoot() {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(EnchantedBlockLootEvent.class, event -> {
            fired.incrementAndGet();
            event.setLevel(0);
        });
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        List<ItemStack> drops = Block.getDrops(Blocks.COAL_ORE.defaultBlockState(), level, base, null, null, pickaxe);
        boolean coal = drops.stream().anyMatch(stack -> stack.is(Items.COAL));
        proof.check(fired.get() >= 1 && coal, "enchanted block loot fired=" + fired.get() + " drops=" + drops);
    }

    private void checkFluidPlace() {
        BlockPos lava = base.above(6);
        BlockPos water = lava.east();
        try {
            clearAround(lava);
            level.setBlock(water, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
            AtomicInteger fired = new AtomicInteger();
            proof.listen(FluidPlaceBlockEvent.class, event -> {
                if (event.pos().equals(lava)) {
                    fired.incrementAndGet();
                    event.setNewState(Blocks.GOLD_BLOCK.defaultBlockState());
                }
            });
            level.setBlockAndUpdate(lava, Blocks.LAVA.defaultBlockState());
            boolean gold = level.getBlockState(lava).is(Blocks.GOLD_BLOCK);
            proof.check(fired.get() >= 1 && gold, "fluid place block fired=" + fired.get()
                    + " state=" + level.getBlockState(lava));
        } finally {
            clearAround(lava);
        }
    }

    private void clearAround(BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void checkSpreadPlayers() {
        Pig pig = spawnPig(base);
        try {
            pig.addTag("weaver_spread");
            Vec3 start = pig.position();
            AtomicInteger fired = new AtomicInteger();
            proof.listen(EntityTeleportEvent.class, event -> {
                if (event.entity() == pig && event.cause() == EntityTeleportEvent.Cause.SPREAD_PLAYERS) {
                    fired.incrementAndGet();
                    event.setCanceled(true);
                }
            });
            CommandSourceStack source = level.getServer().createCommandSourceStack()
                    .withLevel(level).withPosition(Vec3.atCenterOf(base)).withSuppressedOutput();
            level.getServer().getCommands().performPrefixedCommand(source,
                    "spreadplayers " + base.getX() + " " + base.getZ() + " 0 8 false @e[tag=weaver_spread]");
            proof.check(fired.get() == 1 && pig.position().equals(start),
                    "spread players fired=" + fired.get() + " moved=" + !pig.position().equals(start));
        } finally {
            pig.discard();
        }
    }

    /** /tp of a non-player entity: canceled once, then moved to a changed absolute target. */
    private void checkTeleportCommand() {
        Pig pig = spawnPig(base);
        try {
            Vec3 start = pig.position();
            AtomicInteger fired = new AtomicInteger();
            Vec3 redirected = start.add(0, 3, 0);
            proof.listen(EntityTeleportEvent.class, event -> {
                if (event.entity() == pig && event.cause() == EntityTeleportEvent.Cause.COMMAND) {
                    if (fired.incrementAndGet() == 1) {
                        event.setCanceled(true);
                    } else {
                        event.setTarget(redirected);
                    }
                }
            });
            CommandSourceStack source = level.getServer().createCommandSourceStack()
                    .withLevel(level).withPosition(Vec3.atCenterOf(base)).withSuppressedOutput();
            String command = "tp " + pig.getStringUUID() + " ~5 ~ ~";
            level.getServer().getCommands().performPrefixedCommand(source, command);
            boolean canceled = fired.get() == 1 && pig.position().equals(start);
            level.getServer().getCommands().performPrefixedCommand(source, command);
            boolean redirectedOk = fired.get() == 2 && pig.position().distanceTo(redirected) < 0.01;
            proof.check(canceled && redirectedOk, "teleport command fired=" + fired.get() + " canceled=" + canceled
                    + " redirected=" + redirectedOk + " at=" + pig.position());
        } finally {
            pig.discard();
        }
    }

    private static final net.weaver.api.capability.AttachmentType<String> ATTACHED =
            net.weaver.api.capability.AttachmentType.builder("weaverproof:attached", String.class)
                    .codec(net.weaver.api.data.DataCodecs.utf8String(64), 64)
                    .build();

    /** A capability attached to every pig on first use: computed from the pig, stored, then explicit. */
    private void checkAttach() {
        net.weaver.api.capability.AttachmentRegistry registry = net.weaver.api.capability.AttachmentRegistry.global();
        if (registry.attacherCount(ATTACHED) == 0) {
            registry.attach(ATTACHED, Pig.class, attachedPig -> "pig:" + attachedPig.getStringUUID().substring(0, 8));
        }
        Pig pig = spawnPig(base);
        try {
            boolean before = !registry.has(pig, ATTACHED);
            String value = registry.get(pig, ATTACHED);
            boolean computed = ("pig:" + pig.getStringUUID().substring(0, 8)).equals(value);
            boolean stored = registry.has(pig, ATTACHED) && value.equals(registry.find(pig, ATTACHED).orElse(null));
            boolean notOthers = registry.get(level, ATTACHED) == null;
            proof.check(before && computed && stored && notOthers, "attach on first use before=" + before
                    + " computed=" + computed + " stored=" + stored + " notOthers=" + notOthers);
        } finally {
            pig.discard();
        }
    }

    private void checkModList() {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        Map<String, String> recorded = ModMismatchCheck.recorded(overworld);
        boolean installed = WeaverMods.find("weaverproof").isPresent();
        boolean saved = recorded.containsKey("weaverproof");
        List<ModMismatchEvent.Mismatch> diff = ModMismatchCheck.compare(
                Map.of("a", "1", "b", "1", "c", "1"), Map.of("a", "1", "b", "2", "d", "1"));
        boolean compared = diff.size() == 3
                && diff.get(0).modId().equals("b") && !diff.get(0).added() && !diff.get(0).removed()
                && diff.get(1).modId().equals("c") && diff.get(1).removed()
                && diff.get(2).modId().equals("d") && diff.get(2).added();
        proof.check(installed && saved && compared,
                "mod list installed=" + installed + " recorded=" + recorded.keySet() + " compare=" + diff);
    }

    private void checkStructureConversions() throws ReflectiveOperationException {
        Method table = net.minecraft.util.datafix.fixes.StructuresBecomeConfiguredFix.class
                .getDeclaredMethod("weaver$table");
        table.setAccessible(true);
        Map<?, ?> conversions = (Map<?, ?>) table.invoke(null);
        boolean mod = conversions.containsKey("weaverproof_tower") && conversions.containsKey("weaverproof_ruin");
        boolean vanilla = conversions.containsKey("mineshaft");
        proof.check(mod && vanilla, "structure conversions mod=" + mod + " vanilla=" + vanilla
                + " size=" + conversions.size());
    }

    /**
     * A modded fluid with a FluidType: an entity in it is slowed by its drag (instead of
     * falling as through air) and loses air with its eyes inside.
     */
    private void checkFluidType() {
        BlockPos bottom = base.above(20);
        java.util.List<BlockPos> column = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(bottom.offset(-1, 0, -1), bottom.offset(1, 3, 1))) {
            column.add(pos.immutable());
        }
        // A floor keeps the pig in the column: the column floats, and a pig sinking out of
        // its bottom would end with its eyes below the fluid.
        java.util.List<BlockPos> floor = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(bottom.offset(-1, -1, -1), bottom.offset(1, -1, 1))) {
            floor.add(pos.immutable());
        }
        Pig pig = null;
        try {
            for (BlockPos pos : column) {
                level.setBlock(pos, GooFluid.block.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            for (BlockPos pos : floor) {
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            String placed = level.getBlockState(bottom).toString() + " fluid=" + level.getFluidState(bottom)
                    + " tagged=" + level.getFluidState(bottom).getType().builtInRegistryHolder().is(GooFluid.TAG);
            pig = spawnPig(bottom);
            pig.setPos(bottom.getX() + 0.5, bottom.getY() + 1.0, bottom.getZ() + 0.5);
            pig.setDeltaMovement(0, -0.5, 0);
            int airBefore = pig.getAirSupply();
            for (int tick = 0; tick < 10; tick++) {
                pig.tick();
            }
            boolean inside = pig.getFluidHeight(GooFluid.TAG) > 0.0;
            double fall = pig.getDeltaMovement().y;
            boolean slowed = Math.abs(fall) < 0.2;
            boolean drowning = pig.getAirSupply() < airBefore;
            boolean registered = net.weaver.api.game.fluid.FluidTypes.of(GooFluid.source) != null;
            proof.check(inside && slowed && drowning && registered, "fluid type inside=" + inside + " fall=" + fall
                    + " air=" + airBefore + "->" + pig.getAirSupply() + " registered=" + registered + " eye=" + pig.isEyeInFluid(GooFluid.TAG) + " placed=" + placed);
        } finally {
            if (pig != null) {
                pig.discard();
            }
            for (BlockPos pos : column) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            for (BlockPos pos : floor) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Cauldrons are one-bucket tanks; buckets fill and empty through FluidContainers. */
    private void checkVanillaFluids() {
        BlockPos pos = base.above(26);
        net.weaver.api.game.lookup.FluidAmount water = net.weaver.api.game.lookup.FluidAmount.of(
                net.minecraft.world.level.material.Fluids.WATER, 1000);
        try {
            level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_CLIENTS);
            net.weaver.api.game.lookup.FluidHandler cauldron = net.weaver.api.game.lookup.Lookups.BLOCK_FLUIDS.find(
                    level, pos, net.minecraft.core.Direction.UP);
            boolean found = cauldron != null;
            boolean simulated = found && cauldron.fill(water, true) == 1000 && level.getBlockState(pos).is(Blocks.CAULDRON);
            boolean filled = found && cauldron.fill(water, false) == 1000 && level.getBlockState(pos).is(Blocks.WATER_CAULDRON)
                    && cauldron.fluid(0).amount() == 1000;
            boolean tooLittle = found && cauldron.drain(500, false).isEmpty();
            boolean drained = found && cauldron.drain(1000, false).amount() == 1000 && level.getBlockState(pos).is(Blocks.CAULDRON);
            boolean lava = found && cauldron.fill(net.weaver.api.game.lookup.FluidAmount.of(
                    net.minecraft.world.level.material.Fluids.LAVA, 1000), false) == 1000
                    && level.getBlockState(pos).is(Blocks.LAVA_CAULDRON);
            net.weaver.api.game.lookup.FluidContainers.Result poured = net.weaver.api.game.lookup.FluidContainers.drain(
                    new ItemStack(Items.WATER_BUCKET), 1000, false);
            boolean bucketOut = poured.fluid().amount() == 1000
                    && poured.fluid().fluid() == net.minecraft.world.level.material.Fluids.WATER
                    && poured.container().is(Items.BUCKET);
            net.weaver.api.game.lookup.FluidContainers.Result scooped = net.weaver.api.game.lookup.FluidContainers.fill(
                    new ItemStack(Items.BUCKET), water, false);
            boolean bucketIn = scooped.moved() && scooped.container().is(Items.WATER_BUCKET);
            boolean lavaBucket = net.weaver.api.game.lookup.FluidContainers.contents(new ItemStack(Items.LAVA_BUCKET))
                    .fluid() == net.minecraft.world.level.material.Fluids.LAVA;
            boolean noBucketForGoo = !net.weaver.api.game.lookup.FluidContainers.fill(new ItemStack(Items.BUCKET),
                    net.weaver.api.game.lookup.FluidAmount.of(GooFluid.source, 1000), false).moved();
            boolean notContainer = !net.weaver.api.game.lookup.FluidContainers.drain(new ItemStack(Items.STONE), 1000, false).moved();
            proof.check(found && simulated && filled && tooLittle && drained && lava && bucketOut && bucketIn
                    && lavaBucket && noBucketForGoo && notContainer, "vanilla fluid handlers found=" + found
                    + " simulated=" + simulated + " filled=" + filled + " tooLittle=" + tooLittle + " drained=" + drained
                    + " lava=" + lava + " bucketOut=" + bucketOut + " bucketIn=" + bucketIn + " lavaBucket=" + lavaBucket
                    + " noBucketForGoo=" + noBucketForGoo + " notContainer=" + notContainer);
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Weaver's built-in data pack gives the vanilla entries of the shared c: tags. */
    private void checkConventionalTags() {
        boolean ingot = new ItemStack(Items.IRON_INGOT).is(net.weaver.api.game.tag.ConventionalTags.INGOTS)
                && new ItemStack(Items.COPPER_INGOT).is(net.weaver.api.game.tag.ConventionalTags.item("ingots/copper"));
        boolean ore = Blocks.DEEPSLATE_IRON_ORE.defaultBlockState().is(net.weaver.api.game.tag.ConventionalTags.ORES);
        boolean dye = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("red_dye"))).is(net.weaver.api.game.tag.ConventionalTags.item("dyes/red"));
        boolean water = net.minecraft.world.level.material.Fluids.WATER.builtInRegistryHolder()
                .is(net.weaver.api.game.tag.ConventionalTags.WATER);
        boolean notIngot = !new ItemStack(Items.DIRT).is(net.weaver.api.game.tag.ConventionalTags.INGOTS);
        proof.check(ingot && ore && dye && water && notIngot, "conventional tags ingot=" + ingot + " ore=" + ore
                + " dye=" + dye + " water=" + water + " notIngot=" + notIngot);
    }

    /** A cached lookup answers from the cache and is invalidated when the block changes. */
    private void checkLookupCache() {
        BlockPos chest = base.above(10);
        try {
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            AtomicInteger invalidations = new AtomicInteger();
            net.weaver.api.game.lookup.BlockLookupCache<net.weaver.api.game.lookup.ItemHandler, net.minecraft.core.Direction> cache =
                    net.weaver.api.game.lookup.BlockLookupCache.create(net.weaver.api.game.lookup.Lookups.BLOCK_ITEMS,
                            level, chest, net.minecraft.core.Direction.UP, invalidations::incrementAndGet);
            boolean found = cache.get() != null;
            boolean cached = cache.isValid() && cache.get() == cache.get();
            level.setBlockAndUpdate(chest, Blocks.AIR.defaultBlockState());
            boolean invalidated = !cache.isValid() && invalidations.get() >= 1;
            boolean gone = cache.get() == null;
            proof.check(found && cached && invalidated && gone, "lookup cache found=" + found + " cached=" + cached
                    + " invalidated=" + invalidated + " gone=" + gone);
        } finally {
            level.setBlockAndUpdate(chest, Blocks.AIR.defaultBlockState());
        }
    }

    /** An entity whose tick throws is suspended; the game keeps running. */
    private void checkCrashShield() {
        Pig pig = spawnPig(base);
        try {
            boolean survived;
            try {
                level.guardEntityTick(entity -> {
                    throw new IllegalStateException("weaver proof: a mod entity tick that throws");
                }, pig);
                survived = true;
            } catch (RuntimeException crashed) {
                survived = false;
            }
            AtomicInteger later = new AtomicInteger();
            level.guardEntityTick(entity -> later.incrementAndGet(), pig);
            boolean suspended = later.get() == 0 && !pig.isRemoved();
            proof.check(survived && suspended, "crash shield survived=" + survived + " suspended=" + suspended);
        } finally {
            pig.discard();
        }
    }

    private Pig spawnPig(BlockPos pos) {
        Pig pig = EntityTypes.PIG.spawn(level, pos, EntitySpawnReason.COMMAND);
        if (pig == null) {
            throw new IllegalStateException("pig did not spawn");
        }
        return pig;
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
