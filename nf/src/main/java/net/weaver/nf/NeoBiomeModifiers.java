package net.weaver.nf;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.weaver.api.game.worldgen.BiomeModifier;
import net.weaver.api.game.worldgen.BiomeModifiers;

/**
 * NeoForge's {@code "type": "neoforge:add_spawns"} biome modifier files, read by
 * Weaver's biome modifiers from {@code data/<namespace>/weaver/biome_modifiers/}.
 * Each spawner joins the category of its entity type. Call {@link #register()}
 * once from mod setup; later calls do nothing.
 */
public final class NeoBiomeModifiers {
    private static boolean registered;

    private NeoBiomeModifiers() {
    }

    /** Spawners added to the biomes, each to the category of its entity type. */
    public record AddSpawns(HolderSet<Biome> biomes, List<Weighted<MobSpawnSettings.SpawnerData>> spawners)
            implements BiomeModifier {
        private static final Codec<List<Weighted<MobSpawnSettings.SpawnerData>>> SPAWNERS = Codec.either(
                        Weighted.codec(MobSpawnSettings.SpawnerData.CODEC).listOf(),
                        Weighted.codec(MobSpawnSettings.SpawnerData.CODEC))
                .xmap(either -> either.map(list -> list, List::of), Either::left);

        static final MapCodec<AddSpawns> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Biome.LIST_CODEC.fieldOf("biomes").forGetter(AddSpawns::biomes),
                SPAWNERS.fieldOf("spawners").forGetter(AddSpawns::spawners)
        ).apply(instance, AddSpawns::new));

        @Override
        public void modifySpawns(Holder<Biome> biome, MobCategory category,
                List<Weighted<MobSpawnSettings.SpawnerData>> spawns) {
            if (!biomes.contains(biome)) {
                return;
            }
            for (Weighted<MobSpawnSettings.SpawnerData> spawner : spawners) {
                if (spawner.value().type().getCategory() == category) {
                    spawns.add(spawner);
                }
            }
        }
    }

    /** Registers the {@code neoforge:add_spawns} type, once for every mod. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        try {
            BiomeModifiers.registerType(Identifier.fromNamespaceAndPath("neoforge", "add_spawns"), AddSpawns.CODEC);
        } catch (IllegalArgumentException taken) {
            // A copy inside another mod registered the type first; it reads the same files.
        }
    }
}
