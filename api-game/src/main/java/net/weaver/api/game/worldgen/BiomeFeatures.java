package net.weaver.api.game.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** A biome's placed features per generation step, open for {@link BiomeModifier}s to change. */
public final class BiomeFeatures {
    private final List<List<Holder<PlacedFeature>>> steps;
    private final List<List<Holder<PlacedFeature>>> original;

    BiomeFeatures(List<HolderSet<PlacedFeature>> current) {
        int count = GenerationStep.Decoration.values().length;
        steps = new ArrayList<>(count);
        original = new ArrayList<>(count);
        for (int step = 0; step < count; step++) {
            List<Holder<PlacedFeature>> features = new ArrayList<>();
            if (step < current.size()) {
                current.get(step).forEach(features::add);
            }
            steps.add(features);
            original.add(List.copyOf(features));
        }
    }

    /** The features of {@code step}, in order; changes go straight to the biome. */
    public List<Holder<PlacedFeature>> step(GenerationStep.Decoration step) {
        return steps.get(step.ordinal());
    }

    boolean changed() {
        for (int step = 0; step < steps.size(); step++) {
            if (!steps.get(step).equals(original.get(step))) {
                return true;
            }
        }
        return false;
    }

    List<HolderSet<PlacedFeature>> build() {
        List<HolderSet<PlacedFeature>> built = new ArrayList<>(steps.size());
        for (List<Holder<PlacedFeature>> features : steps) {
            built.add(HolderSet.direct(features));
        }
        return List.copyOf(built);
    }
}
