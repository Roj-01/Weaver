package net.weaver.proof;

import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.weaver.api.game.loot.LootModifiers;
import net.weaver.api.game.worldgen.BiomeModifiers;

/**
 * Loot and biome modifiers from the proof mod's data
 * ({@code data/weaverproof/weaver/loot_modifiers} and {@code .../biome_modifiers}).
 */
final class GameEventsModifiersProof {
    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;

    GameEventsModifiersProof(ServerLevel level, BlockPos base, GameEventsProof proof) {
        this.level = level;
        this.base = base;
        this.proof = proof;
    }

    void run() {
        guard("loot modifiers loaded", this::checkLoaded);
        guard("loot modifiers applied", this::checkLoot);
        guard("biome features", this::checkFeatures);
        guard("biome spawns", this::checkSpawns);
        guard("data maps", () -> ProofDataMaps.check(proof, level, base));
        guard("structure modifiers", this::checkStructures);
    }

    /** The structure modifier files of the proof mod, read back from the live structure registry. */
    @SuppressWarnings("deprecation")
    private void checkStructures() {
        var structures = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        var biomes = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        java.util.function.Function<String, net.minecraft.world.level.levelgen.structure.Structure> structure = path ->
                structures.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(path));
        var desert = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.DESERT);
        var jungle = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.JUNGLE);
        var igloo = structure.apply("igloo");
        boolean added = igloo.biomes().contains(desert) && !igloo.biomes().contains(jungle);
        boolean originalKept = !net.weaver.api.game.worldgen.StructureModifiers.original(igloo).biomes().contains(desert);
        boolean generation = igloo.step() == net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_STRUCTURES
                && igloo.terrainAdaptation() == net.minecraft.world.level.levelgen.structure.TerrainAdjustment.BEARD_THIN;
        boolean removed = !structure.apply("desert_pyramid").biomes().contains(desert);
        boolean disabled = structure.apply("ruined_portal_desert").biomes().size() == 0;
        var hut = structure.apply("swamp_hut").spawnOverrides()
                .get(net.minecraft.world.entity.MobCategory.MONSTER);
        boolean hutSpawns = hut != null && hut.spawns().unwrap().stream()
                .anyMatch(entry -> entry.value().type() == net.minecraft.world.entity.EntityTypes.ZOMBIE)
                && hut.spawns().unwrap().stream().anyMatch(entry -> entry.value().type() == net.minecraft.world.entity.EntityTypes.WITCH);
        var monument = structure.apply("monument").spawnOverrides()
                .get(net.minecraft.world.entity.MobCategory.MONSTER);
        boolean noGuardians = monument != null && monument.spawns().unwrap().stream()
                .noneMatch(entry -> entry.value().type() == net.minecraft.world.entity.EntityTypes.GUARDIAN);
        boolean outpostCleared = !structure.apply("pillager_outpost").spawnOverrides()
                .containsKey(net.minecraft.world.entity.MobCategory.MONSTER);
        boolean loaded = net.weaver.api.game.worldgen.StructureModifiers.loaded().size() == 7;
        proof.check(added && originalKept && generation && removed && disabled && hutSpawns && noGuardians
                && outpostCleared && loaded, "structure modifiers added=" + added + " originalKept=" + originalKept
                + " generation=" + generation + " removed=" + removed + " disabled=" + disabled + " hutSpawns=" + hutSpawns
                + " noGuardians=" + noGuardians + " outpostCleared=" + outpostCleared + " loaded="
                + net.weaver.api.game.worldgen.StructureModifiers.loaded());
    }

    private void checkLoaded() {
        List<String> ids = LootModifiers.loaded().stream().map(entry -> entry.id().toString()).toList();
        // dirt_disabled is switched off, broken has an unknown type.
        boolean ok = ids.containsAll(List.of("weaverproof:dirt_diamond", "weaverproof:dirt_never",
                "weaverproof:stone_mossy", "weaverproof:coarse_dirt_none"))
                && !ids.contains("weaverproof:dirt_disabled") && !ids.contains("weaverproof:broken")
                && BiomeModifiers.loaded().ids().size() == 5 && BiomeModifiers.loaded().ids().stream()
                        .anyMatch(id -> id.toString().equals("weaverproof:nf_goats"));
        proof.check(ok, "loot modifiers loaded ids=" + ids + " biome=" + BiomeModifiers.loaded().ids());
    }

    private void checkLoot() {
        List<ItemStack> dirt = drops(Blocks.DIRT);
        List<ItemStack> stone = drops(Blocks.STONE);
        List<ItemStack> coarse = drops(Blocks.COARSE_DIRT);
        List<ItemStack> sand = drops(Blocks.SAND);
        boolean added = count(dirt, Items.DIRT) == 1 && count(dirt, Items.DIAMOND) == 2
                && count(dirt, Items.EMERALD) == 0 && count(dirt, Items.GOLD_INGOT) == 0;
        boolean replaced = count(stone, Items.MOSSY_COBBLESTONE) == 1 && count(stone, Items.COBBLESTONE) == 0;
        boolean removed = coarse.isEmpty();
        boolean untouched = count(sand, Items.SAND) == 1 && sand.size() == 1;
        Identifier dirtTable = LootModifiers.tableId(level, level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("blocks/dirt"))));
        proof.check(added && replaced && removed && untouched && "minecraft:blocks/dirt".equals(String.valueOf(dirtTable)),
                "loot modifiers applied dirt=" + dirt + " stone=" + stone + " coarse=" + coarse + " sand=" + sand);
    }

    private void checkFeatures() {
        Holder<Biome> biome = level.getBiome(base);
        List<net.minecraft.core.HolderSet<PlacedFeature>> features = biome.value().getGenerationSettings().features();
        boolean added = has(features, GenerationStep.Decoration.VEGETAL_DECORATION, "weaverproof:proof_noop");
        boolean removed = !has(features, GenerationStep.Decoration.UNDERGROUND_ORES, "minecraft:ore_coal_upper");
        boolean keptOthers = has(features, GenerationStep.Decoration.UNDERGROUND_ORES, "minecraft:ore_coal_lower");
        proof.check(added && removed && keptOthers, "biome features biome=" + biome.getRegisteredName()
                + " added=" + added + " removed=" + removed + " kept=" + keptOthers);
    }

    private void checkSpawns() throws ReflectiveOperationException {
        Method mobsAt = NaturalSpawner.class.getDeclaredMethod("mobsAt", ServerLevel.class, StructureManager.class,
                ChunkGenerator.class, MobCategory.class, BlockPos.class);
        mobsAt.setAccessible(true);
        WeightedList<?> creatures = (WeightedList<?>) mobsAt.invoke(null, level, level.structureManager(),
                level.getChunkSource().getGenerator(), MobCategory.CREATURE, base);
        WeightedList<?> monsters = (WeightedList<?>) mobsAt.invoke(null, level, level.structureManager(),
                level.getChunkSource().getGenerator(), MobCategory.MONSTER, base);
        boolean mooshroom = creatures.unwrap().stream()
                .anyMatch(entry -> ((MobSpawnSettings.SpawnerData) entry.value()).type() == EntityTypes.MOOSHROOM
                        && entry.weight() == 5);
        boolean noWitch = monsters.unwrap().stream()
                .noneMatch(entry -> ((MobSpawnSettings.SpawnerData) entry.value()).type() == EntityTypes.WITCH);
        boolean otherMonsters = !monsters.unwrap().isEmpty();
        // The adjusted list is cached per biome and category.
        WeightedList<?> again = (WeightedList<?>) mobsAt.invoke(null, level, level.structureManager(),
                level.getChunkSource().getGenerator(), MobCategory.CREATURE, base);
        boolean cached = again == creatures;
        proof.check(mooshroom && noWitch && otherMonsters && cached,
                "biome spawns mooshroom=" + mooshroom + " noWitch=" + noWitch + " monsters=" + monsters.unwrap().size()
                        + " cached=" + cached);
    }

    private List<ItemStack> drops(Block block) {
        return Block.getDrops(block.defaultBlockState(), level, base, null);
    }

    private static int count(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static boolean has(List<net.minecraft.core.HolderSet<PlacedFeature>> features,
            GenerationStep.Decoration step, String id) {
        if (step.ordinal() >= features.size()) {
            return false;
        }
        return features.get(step.ordinal()).stream()
                .anyMatch(holder -> holder.unwrapKey().map(key -> key.identifier().toString().equals(id)).orElse(false));
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
