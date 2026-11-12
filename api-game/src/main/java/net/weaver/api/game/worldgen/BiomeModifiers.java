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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.weaver.api.game.data.ModifierFiles;
import net.weaver.api.game.event.mixin.BiomeAccessor;
import net.weaver.api.game.event.mixin.BiomeGenerationSettingsAccessor;

/**
 * Biome modifiers: data pack files that add or remove features and mob spawns
 * in biomes, without replacing the biome files.
 *
 * <p>Files live at {@code data/<namespace>/weaver/biome_modifiers/<path>.json}.
 * {@code biomes} is a biome id, a list, or a {@code #tag}; {@code features}
 * and {@code entities} work the same way. Built-in types:</p>
 *
 * <ul>
 *   <li>{@code weaver:add_features}: {@code biomes}, {@code features},
 *       {@code step} (a generation step such as {@code vegetal_decoration});</li>
 *   <li>{@code weaver:remove_features}: {@code biomes}, {@code features},
 *       optional {@code steps} (default every step);</li>
 *   <li>{@code weaver:add_spawns}: {@code biomes}, {@code category} (such as
 *       {@code creature}), {@code spawners}: entries of {@code type},
 *       {@code count}, {@code weight};</li>
 *   <li>{@code weaver:remove_spawns}: {@code biomes}, {@code entities},
 *       optional {@code categories}.</li>
 * </ul>
 *
 * <p>Mods add kinds with {@link #registerType}. Feature changes are applied
 * once, when the world's data first loads, because biome features are fixed
 * for a running server; edit them and restart. Spawn changes apply to the
 * biome's own spawn list when spawns are chosen (natural spawning and the
 * creatures placed with new chunks; structure spawn overrides are left
 * alone), so they follow {@code /reload}. An {@code enabled: false} field
 * switches a file off.</p>
 */
public final class BiomeModifiers {
    private static final Map<Identifier, MapCodec<? extends BiomeModifier>> TYPES = new ConcurrentHashMap<>();

    /** What the last load read, in the order the files apply. */
    public record Loaded(List<Identifier> ids, List<BiomeModifier> modifiers) {
        static final Loaded EMPTY = new Loaded(List.of(), List.of());
    }

    private record Entry(Identifier id, BiomeModifier modifier) {
    }

    private record SpawnKey(Holder<Biome> biome, MobCategory category) {
    }

    private record CachedSpawns(WeightedList<MobSpawnSettings.SpawnerData> original,
                                WeightedList<MobSpawnSettings.SpawnerData> adjusted) {
    }

    private static volatile Loaded loaded = Loaded.EMPTY;
    private static final Map<SpawnKey, CachedSpawns> SPAWNS = new ConcurrentHashMap<>();
    private static final Set<Biome> FEATURES_APPLIED = Collections.newSetFromMap(new java.util.WeakHashMap<>());

    static {
        registerType(Identifier.fromNamespaceAndPath("weaver", "add_features"), AddFeatures.CODEC);
        registerType(Identifier.fromNamespaceAndPath("weaver", "remove_features"), RemoveFeatures.CODEC);
        registerType(Identifier.fromNamespaceAndPath("weaver", "add_spawns"), AddSpawns.CODEC);
        registerType(Identifier.fromNamespaceAndPath("weaver", "remove_spawns"), RemoveSpawns.CODEC);
    }

    private BiomeModifiers() {
    }

    /**
     * Registers a biome modifier type for files with {@code "type": "<id>"};
     * register during mod setup.
     *
     * @throws IllegalArgumentException when the id is taken
     */
    public static void registerType(Identifier id, MapCodec<? extends BiomeModifier> codec) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        if (TYPES.putIfAbsent(id, codec) != null) {
            throw new IllegalArgumentException("biome modifier type already registered: " + id);
        }
    }

    /** Adds {@code features} to {@code step}. */
    public record AddFeatures(HolderSet<Biome> biomes, HolderSet<PlacedFeature> features,
                              GenerationStep.Decoration step) implements BiomeModifier {
        static final MapCodec<AddFeatures> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(AddFeatures::biomes),
                PlacedFeature.LIST_CODEC.fieldOf("features").forGetter(AddFeatures::features),
                GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(AddFeatures::step)
        ).apply(instance, AddFeatures::new));

        @Override
        public void modifyFeatures(Holder<Biome> biome, BiomeFeatures current) {
            if (!biomes.contains(biome)) {
                return;
            }
            List<Holder<PlacedFeature>> list = current.step(step);
            for (Holder<PlacedFeature> feature : features) {
                if (!list.contains(feature)) {
                    list.add(feature);
                }
            }
        }
    }

    /** Removes {@code features} from {@code steps} (all when absent). */
    public record RemoveFeatures(HolderSet<Biome> biomes, HolderSet<PlacedFeature> features,
                                 Optional<List<GenerationStep.Decoration>> steps) implements BiomeModifier {
        static final MapCodec<RemoveFeatures> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(RemoveFeatures::biomes),
                PlacedFeature.LIST_CODEC.fieldOf("features").forGetter(RemoveFeatures::features),
                GenerationStep.Decoration.CODEC.listOf().optionalFieldOf("steps").forGetter(RemoveFeatures::steps)
        ).apply(instance, RemoveFeatures::new));

        @Override
        public void modifyFeatures(Holder<Biome> biome, BiomeFeatures current) {
            if (!biomes.contains(biome)) {
                return;
            }
            for (GenerationStep.Decoration decoration : GenerationStep.Decoration.values()) {
                if (steps.isEmpty() || steps.get().contains(decoration)) {
                    current.step(decoration).removeIf(features::contains);
                }
            }
        }
    }

    /** Adds weighted spawn entries to {@code category}. */
    public record AddSpawns(HolderSet<Biome> biomes, MobCategory category,
                            List<Weighted<MobSpawnSettings.SpawnerData>> spawners) implements BiomeModifier {
        static final MapCodec<AddSpawns> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(AddSpawns::biomes),
                MobCategory.CODEC.fieldOf("category").forGetter(AddSpawns::category),
                Weighted.codec(MobSpawnSettings.SpawnerData.CODEC).listOf().fieldOf("spawners")
                        .forGetter(AddSpawns::spawners)
        ).apply(instance, AddSpawns::new));

        @Override
        public void modifySpawns(Holder<Biome> biome, MobCategory spawnCategory,
                List<Weighted<MobSpawnSettings.SpawnerData>> spawns) {
            if (spawnCategory == category && biomes.contains(biome)) {
                spawns.addAll(spawners);
            }
        }
    }

    /** Removes spawn entries of {@code entities} from {@code categories} (all when absent). */
    public record RemoveSpawns(HolderSet<Biome> biomes, HolderSet<EntityType<?>> entities,
                               Optional<List<MobCategory>> categories) implements BiomeModifier {
        static final MapCodec<RemoveSpawns> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(RemoveSpawns::biomes),
                RegistryCodecs.holderSet(Registries.ENTITY_TYPE).fieldOf("entities").forGetter(RemoveSpawns::entities),
                MobCategory.CODEC.listOf().optionalFieldOf("categories").forGetter(RemoveSpawns::categories)
        ).apply(instance, RemoveSpawns::new));

        @Override
        @SuppressWarnings("deprecation")
        public void modifySpawns(Holder<Biome> biome, MobCategory category,
                List<Weighted<MobSpawnSettings.SpawnerData>> spawns) {
            if ((categories.isEmpty() || categories.get().contains(category)) && biomes.contains(biome)) {
                spawns.removeIf(entry -> entities.contains(entry.value().type().builtInRegistryHolder()));
            }
        }
    }

    /** The files the last data pack load read. */
    public static Loaded loaded() {
        return loaded;
    }

    /**
     * Reads modifier files, applies feature changes to biomes that have not
     * had them yet, and installs the spawn changes. Called on every data pack
     * load.
     */
    public static void load(Map<Identifier, JsonElement> files, HolderLookup.Provider lookup, Consumer<String> warn) {
        List<Entry> entries = ModifierFiles.parse("BIOME MODIFIER", files, TYPES,
                lookup.createSerializationContext(JsonOps.INSTANCE),
                parsed -> new Entry(parsed.id(), parsed.value()), warn);
        Loaded next = new Loaded(entries.stream().map(Entry::id).toList(),
                entries.stream().map(Entry::modifier).toList());
        applyFeatures(lookup, next, warn);
        loaded = next;
        SPAWNS.clear();
    }

    private static void applyFeatures(HolderLookup.Provider lookup, Loaded next, Consumer<String> warn) {
        int changed = 0;
        List<Holder.Reference<Biome>> biomes = lookup.lookupOrThrow(Registries.BIOME).listElements().toList();
        synchronized (FEATURES_APPLIED) {
            for (Holder.Reference<Biome> holder : biomes) {
                Biome biome = holder.value();
                if (!FEATURES_APPLIED.add(biome)) {
                    continue;
                }
                BiomeGenerationSettings settings = biome.getGenerationSettings();
                BiomeFeatures features = new BiomeFeatures(settings.features());
                for (BiomeModifier modifier : next.modifiers()) {
                    try {
                        modifier.modifyFeatures(holder, features);
                    } catch (RuntimeException | LinkageError | StackOverflowError failure) {
                        warn.accept("[weaver] BIOME MODIFIER FAILED biome=" + holder.getRegisteredName()
                                + " reason=" + failure);
                    }
                }
                if (features.changed()) {
                    BiomeGenerationSettings replacement = BiomeGenerationSettingsAccessor.weaver$create(
                            ((BiomeGenerationSettingsAccessor) settings).weaver$carvers(), features.build());
                    ((BiomeAccessor) (Object) biome).weaver$setGenerationSettings(replacement);
                    changed++;
                }
            }
        }
        if (changed > 0) {
            warn.accept("[weaver] BIOME MODIFIERS features changed biomes=" + changed);
        }
    }

    /**
     * The biome's spawn entries for {@code category} at {@code pos} after the
     * loaded spawn changes; {@code spawns} itself when nothing is loaded or
     * nothing changes. The biome is looked up only when modifiers are loaded.
     */
    public static WeightedList<MobSpawnSettings.SpawnerData> adjustSpawns(LevelReader level, BlockPos pos,
            MobCategory category, WeightedList<MobSpawnSettings.SpawnerData> spawns) {
        if (loaded.modifiers().isEmpty()) {
            return spawns;
        }
        return adjustSpawns(level.getBiome(pos), category, spawns);
    }

    /**
     * The spawn entries for {@code category} in {@code biome} after the loaded
     * spawn changes, cached per biome and category until the next load.
     */
    public static WeightedList<MobSpawnSettings.SpawnerData> adjustSpawns(Holder<Biome> biome, MobCategory category,
            WeightedList<MobSpawnSettings.SpawnerData> spawns) {
        List<BiomeModifier> modifiers = loaded.modifiers();
        if (modifiers.isEmpty()) {
            return spawns;
        }
        SpawnKey key = new SpawnKey(biome, category);
        CachedSpawns cached = SPAWNS.get(key);
        if (cached != null && cached.original() == spawns) {
            return cached.adjusted();
        }
        List<Weighted<MobSpawnSettings.SpawnerData>> entries = new ArrayList<>(spawns.unwrap());
        for (BiomeModifier modifier : modifiers) {
            try {
                modifier.modifySpawns(biome, category, entries);
            } catch (RuntimeException | LinkageError | StackOverflowError failure) {
                System.out.println("[weaver] BIOME MODIFIER FAILED biome=" + biome.getRegisteredName()
                        + " spawns=" + category + " reason=" + failure);
            }
        }
        WeightedList<MobSpawnSettings.SpawnerData> adjusted =
                entries.equals(spawns.unwrap()) ? spawns : WeightedList.of(entries);
        SPAWNS.put(key, new CachedSpawns(spawns, adjusted));
        return adjusted;
    }
}
