package net.weaver.api.game.worldgen;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.weaver.api.game.data.ModifierFiles;
import net.weaver.api.game.event.mixin.StructureAccessor;

/**
 * Structure modifiers: data pack files under
 * {@code data/<namespace>/weaver/structure_modifiers/} that change vanilla's and
 * other mods' structures without replacing their files. Two mods that both let
 * villages generate in their biomes, or both add mobs to a fortress, no longer
 * overwrite each other.
 *
 * <p>On every data pack load each structure's settings are rebuilt from its own
 * file's settings with the loaded modifiers, so spawn, step, and terrain changes
 * follow {@code /reload}. Biome changes decide where a structure is planned; the
 * plan is made when a level starts, so they take effect after a restart, for
 * chunks generated afterwards.</p>
 */
public final class StructureModifiers {
    private static final Map<Identifier, MapCodec<? extends StructureModifier>> TYPES = new ConcurrentHashMap<>();
    private static final Map<Structure, Structure.StructureSettings> ORIGINALS =
            Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static volatile List<Identifier> loaded = List.of();

    private static final MapCodec<HolderSet<Structure>> STRUCTURES =
            RegistryCodecs.holderSet(Registries.STRUCTURE).fieldOf("structures");

    static {
        registerType(id("add_biomes"), AddBiomes.CODEC);
        registerType(id("remove_biomes"), RemoveBiomes.CODEC);
        registerType(id("disable"), Disable.CODEC);
        registerType(id("add_spawns"), AddSpawns.CODEC);
        registerType(id("remove_spawns"), RemoveSpawns.CODEC);
        registerType(id("clear_spawns"), ClearSpawns.CODEC);
        registerType(id("set_generation"), SetGeneration.CODEC);
    }

    private StructureModifiers() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("weaver", path);
    }

    /**
     * Registers a structure modifier type for files with {@code "type": "<id>"};
     * register during mod setup.
     *
     * @throws IllegalArgumentException when the id is taken
     */
    public static void registerType(Identifier id, MapCodec<? extends StructureModifier> codec) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        if (TYPES.putIfAbsent(id, codec) != null) {
            throw new IllegalArgumentException("structure modifier type already registered: " + id);
        }
    }

    /** The ids of the files the last data pack load applied, in order. */
    public static List<Identifier> loaded() {
        return loaded;
    }

    /** The settings a structure had before any modifier (its own file's). */
    public static Structure.StructureSettings original(Structure structure) {
        synchronized (ORIGINALS) {
            return ORIGINALS.getOrDefault(structure, ((StructureAccessor) structure).weaver$settings());
        }
    }

    private record Entry(Identifier id, StructureModifier modifier) {
    }

    /** Reads modifier files and rebuilds every structure's settings. Called on every data pack load. */
    public static void load(Map<Identifier, JsonElement> files, HolderLookup.Provider lookup, Consumer<String> warn) {
        List<Entry> entries = ModifierFiles.parse("STRUCTURE MODIFIER", files, TYPES,
                lookup.createSerializationContext(JsonOps.INSTANCE),
                parsed -> new Entry(parsed.id(), parsed.value()), warn);
        int changed = 0;
        for (Holder.Reference<Structure> holder : lookup.lookupOrThrow(Registries.STRUCTURE).listElements().toList()) {
            Structure structure = holder.value();
            StructureAccessor access = (StructureAccessor) structure;
            Structure.StructureSettings original;
            synchronized (ORIGINALS) {
                original = ORIGINALS.computeIfAbsent(structure, key -> access.weaver$settings());
            }
            StructureSettingsEditor editor = new StructureSettingsEditor(original);
            for (Entry entry : entries) {
                try {
                    entry.modifier().modify(holder, editor);
                } catch (RuntimeException | LinkageError | StackOverflowError failure) {
                    warn.accept("[weaver] STRUCTURE MODIFIER FAILED id=" + entry.id() + " structure="
                            + holder.getRegisteredName() + " reason=" + failure);
                }
            }
            Structure.StructureSettings next = editor.changed() ? editor.build() : original;
            if (access.weaver$settings() != next) {
                access.weaver$setSettings(next);
            }
            if (next != original) {
                changed++;
            }
        }
        loaded = entries.stream().map(Entry::id).toList();
        if (!entries.isEmpty()) {
            warn.accept("[weaver] STRUCTURE MODIFIERS files=" + entries.size() + " structures changed=" + changed);
        }
    }

    /** Lets the structures generate in {@code biomes} as well. */
    public record AddBiomes(HolderSet<Structure> structures, HolderSet<Biome> biomes) implements StructureModifier {
        static final MapCodec<AddBiomes> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                STRUCTURES.forGetter(AddBiomes::structures),
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(AddBiomes::biomes)
        ).apply(instance, AddBiomes::new));

        @Override
        public void modify(Holder<Structure> structure, StructureSettingsEditor settings) {
            if (structures.contains(structure)) {
                for (Holder<Biome> biome : biomes) {
                    if (!settings.biomes().contains(biome)) {
                        settings.biomes().add(biome);
                    }
                }
            }
        }
    }

    /** Stops the structures generating in {@code biomes}. */
    public record RemoveBiomes(HolderSet<Structure> structures, HolderSet<Biome> biomes) implements StructureModifier {
        static final MapCodec<RemoveBiomes> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                STRUCTURES.forGetter(RemoveBiomes::structures),
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(RemoveBiomes::biomes)
        ).apply(instance, RemoveBiomes::new));

        @Override
        public void modify(Holder<Structure> structure, StructureSettingsEditor settings) {
            if (structures.contains(structure)) {
                settings.biomes().removeIf(biomes::contains);
            }
        }
    }

    /** Stops the structures generating anywhere. */
    public record Disable(HolderSet<Structure> structures) implements StructureModifier {
        static final MapCodec<Disable> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                STRUCTURES.forGetter(Disable::structures)
        ).apply(instance, Disable::new));

        @Override
        public void modify(Holder<Structure> structure, StructureSettingsEditor settings) {
            if (structures.contains(structure)) {
                settings.biomes().clear();
            }
        }
    }

    /**
     * Adds mobs that spawn inside the structures. Without an override for the
     * category one is created (inside the structure's pieces, or its whole box with
     * {@code "bounding_box": "full"}), which replaces the biome's spawns there.
     */
    public record AddSpawns(HolderSet<Structure> structures, MobCategory category,
                            List<Weighted<MobSpawnSettings.SpawnerData>> spawners,
                            Optional<StructureSpawnOverride.BoundingBoxType> boundingBox) implements StructureModifier {
        static final MapCodec<AddSpawns> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                STRUCTURES.forGetter(AddSpawns::structures),
                MobCategory.CODEC.fieldOf("category").forGetter(AddSpawns::category),
                Weighted.codec(MobSpawnSettings.SpawnerData.CODEC).listOf().fieldOf("spawners").forGetter(AddSpawns::spawners),
                StructureSpawnOverride.BoundingBoxType.CODEC.optionalFieldOf("bounding_box").forGetter(AddSpawns::boundingBox)
        ).apply(instance, AddSpawns::new));

        @Override
        public void modify(Holder<Structure> structure, StructureSettingsEditor settings) {
            if (!structures.contains(structure)) {
                return;
            }
            StructureSpawnOverride current = settings.spawnOverrides().get(category);
            List<Weighted<MobSpawnSettings.SpawnerData>> entries = new ArrayList<>(
                    current == null ? List.of() : current.spawns().unwrap());
            entries.addAll(spawners);
            StructureSpawnOverride.BoundingBoxType box = boundingBox.orElse(
                    current == null ? StructureSpawnOverride.BoundingBoxType.PIECE : current.boundingBox());
            settings.spawnOverrides().put(category, new StructureSpawnOverride(box, WeightedList.of(entries)));
        }
    }

    /** Removes mobs of {@code entities} from the structures' spawn overrides ({@code categories}, or all). */
    public record RemoveSpawns(HolderSet<Structure> structures, HolderSet<EntityType<?>> entities,
                               Optional<List<MobCategory>> categories) implements StructureModifier {
        static final MapCodec<RemoveSpawns> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                STRUCTURES.forGetter(RemoveSpawns::structures),
                RegistryCodecs.holderSet(Registries.ENTITY_TYPE).fieldOf("entities").forGetter(RemoveSpawns::entities),
                MobCategory.CODEC.listOf().optionalFieldOf("categories").forGetter(RemoveSpawns::categories)
        ).apply(instance, RemoveSpawns::new));

        @Override
        @SuppressWarnings("deprecation")
        public void modify(Holder<Structure> structure, StructureSettingsEditor settings) {
            if (!structures.contains(structure)) {
                return;
            }
            for (Map.Entry<MobCategory, StructureSpawnOverride> entry : List.copyOf(settings.spawnOverrides().entrySet())) {
                if (categories.isPresent() && !categories.get().contains(entry.getKey())) {
                    continue;
                }
                List<Weighted<MobSpawnSettings.SpawnerData>> kept = new ArrayList<>(entry.getValue().spawns().unwrap());
                if (kept.removeIf(spawn -> entities.contains(spawn.value().type().builtInRegistryHolder()))) {
                    settings.spawnOverrides().put(entry.getKey(),
                            new StructureSpawnOverride(entry.getValue().boundingBox(), WeightedList.of(kept)));
                }
            }
        }
    }

    /** Removes the structures' spawn overrides for {@code categories}: the biome's spawns apply inside them again. */
    public record ClearSpawns(HolderSet<Structure> structures, List<MobCategory> categories) implements StructureModifier {
        static final MapCodec<ClearSpawns> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                STRUCTURES.forGetter(ClearSpawns::structures),
                MobCategory.CODEC.listOf().fieldOf("categories").forGetter(ClearSpawns::categories)
        ).apply(instance, ClearSpawns::new));

        @Override
        public void modify(Holder<Structure> structure, StructureSettingsEditor settings) {
            if (structures.contains(structure)) {
                categories.forEach(settings.spawnOverrides()::remove);
            }
        }
    }

    /** Changes the generation step and/or the terrain adaptation of the structures. */
    public record SetGeneration(HolderSet<Structure> structures, Optional<GenerationStep.Decoration> step,
                                Optional<TerrainAdjustment> terrainAdaptation) implements StructureModifier {
        static final MapCodec<SetGeneration> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                STRUCTURES.forGetter(SetGeneration::structures),
                GenerationStep.Decoration.CODEC.optionalFieldOf("step").forGetter(SetGeneration::step),
                TerrainAdjustment.CODEC.optionalFieldOf("terrain_adaptation").forGetter(SetGeneration::terrainAdaptation)
        ).apply(instance, SetGeneration::new));

        @Override
        public void modify(Holder<Structure> structure, StructureSettingsEditor settings) {
            if (structures.contains(structure)) {
                step.ifPresent(settings::setStep);
                terrainAdaptation.ifPresent(settings::setTerrainAdaptation);
            }
        }
    }
}
