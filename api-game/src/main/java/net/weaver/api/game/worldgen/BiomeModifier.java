package net.weaver.api.game.worldgen;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * One biome change, read from a data pack file under
 * {@code data/<namespace>/weaver/biome_modifiers/}. Mods add kinds with
 * {@link BiomeModifiers#registerType}.
 */
public interface BiomeModifier {
    /**
     * Changes the features of {@code biome}. Called once per biome when a
     * world's data first loads, before any level exists.
     */
    default void modifyFeatures(Holder<Biome> biome, BiomeFeatures features) {
    }

    /**
     * Changes the spawn entries of {@code category} in {@code biome}, in place.
     * The result is cached per biome and category until the next data load, so
     * the answer must depend only on those two.
     */
    default void modifySpawns(Holder<Biome> biome, MobCategory category,
            List<Weighted<MobSpawnSettings.SpawnerData>> spawns) {
    }
}
