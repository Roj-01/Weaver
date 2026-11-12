package net.weaver.api.game.worldgen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;

/**
 * The settings of one structure while {@link StructureModifier}s change them: the
 * biomes it may generate in, its spawn overrides per mob category (the mobs that
 * spawn inside it instead of the biome's), its generation step, and its terrain
 * adaptation. The lists and map are mutable; the result replaces the structure's
 * settings only when something changed.
 */
public final class StructureSettingsEditor {
    private final Structure.StructureSettings original;
    private final List<Holder<Biome>> biomes;
    private final Map<MobCategory, StructureSpawnOverride> spawnOverrides = new EnumMap<>(MobCategory.class);
    private GenerationStep.Decoration step;
    private TerrainAdjustment terrainAdaptation;

    StructureSettingsEditor(Structure.StructureSettings original) {
        this.original = original;
        this.biomes = new ArrayList<>(original.biomes().stream().toList());
        this.spawnOverrides.putAll(original.spawnOverrides());
        this.step = original.step();
        this.terrainAdaptation = original.terrainAdaptation();
    }

    /** The biomes the structure may generate in (mutable). */
    public List<Holder<Biome>> biomes() {
        return biomes;
    }

    /** The spawn overrides by mob category (mutable); a category without one uses the biome's spawns. */
    public Map<MobCategory, StructureSpawnOverride> spawnOverrides() {
        return spawnOverrides;
    }

    public GenerationStep.Decoration step() {
        return step;
    }

    public void setStep(GenerationStep.Decoration step) {
        this.step = Objects.requireNonNull(step, "step");
    }

    public TerrainAdjustment terrainAdaptation() {
        return terrainAdaptation;
    }

    public void setTerrainAdaptation(TerrainAdjustment adaptation) {
        this.terrainAdaptation = Objects.requireNonNull(adaptation, "adaptation");
    }

    boolean changed() {
        return !biomes.equals(original.biomes().stream().toList())
                || !spawnOverrides.equals(original.spawnOverrides())
                || step != original.step()
                || terrainAdaptation != original.terrainAdaptation();
    }

    Structure.StructureSettings build() {
        HolderSet<Biome> biomeSet = biomes.equals(original.biomes().stream().toList())
                ? original.biomes() : HolderSet.direct(List.copyOf(biomes));
        return new Structure.StructureSettings(biomeSet, Map.copyOf(spawnOverrides), step, terrainAdaptation);
    }
}
