package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.living.SpawnClusterSizeEvent;
import net.weaver.api.game.event.world.PotentialSpawnsEvent;
import net.weaver.api.game.worldgen.BiomeModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mob candidates spawning picks from ({@code PotentialSpawnsEvent}), and
 * biome spawn modifiers for the creatures placed with a new chunk. Biome spawn
 * modifiers for natural spawning are applied in {@code ChunkGeneratorSpawnsMixin}.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerEventsMixin {
    @Inject(method = "mobsAt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/util/random/WeightedList;",
            at = @At("RETURN"), cancellable = true)
    private static void weaver$potentialSpawns(ServerLevel level, StructureManager structures, ChunkGenerator generator,
                                               MobCategory category, BlockPos pos,
                                               CallbackInfoReturnable<WeightedList<MobSpawnSettings.SpawnerData>> callback) {
        if (!GameEvents.hasListeners(PotentialSpawnsEvent.class)) {
            return;
        }
        PotentialSpawnsEvent event = GameEvents.post(
                new PotentialSpawnsEvent(level, category, pos, callback.getReturnValue().unwrap()), level);
        try {
            java.util.List<net.minecraft.util.random.Weighted<MobSpawnSettings.SpawnerData>> spawns =
                    new java.util.ArrayList<>(event.spawns());
            spawns.removeIf(entry -> entry == null || entry.value() == null);
            callback.setReturnValue(WeightedList.of(spawns));
        } catch (RuntimeException invalid) {
            // An overflowing total weight or a bad entry keeps the vanilla candidates.
            System.out.println("[weaver] POTENTIAL SPAWNS INVALID reason=" + invalid + " action=vanilla-kept");
        }
    }

    @WrapOperation(method = "spawnMobsForChunkGeneration(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/MobSpawnSettings;getMobsToSpawn(Lnet/minecraft/world/entity/MobCategory;)Lnet/minecraft/util/random/WeightedList;"))
    private static WeightedList<MobSpawnSettings.SpawnerData> weaver$chunkSpawns(MobSpawnSettings settings,
            MobCategory category, Operation<WeightedList<MobSpawnSettings.SpawnerData>> original,
            @Local(argsOnly = true) ServerLevelAccessor level, @Local(argsOnly = true) BlockPos pos) {
        return BiomeModifiers.adjustSpawns(level, pos, category, original.call(settings, category));
    }

    @WrapOperation(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getMaxSpawnClusterSize()I"))
    private static int weaver$clusterSize(Mob mob, Operation<Integer> original) {
        int size = original.call(mob);
        if (!GameEvents.hasListeners(SpawnClusterSizeEvent.class)) {
            return size;
        }
        return GameEvents.post(new SpawnClusterSizeEvent(mob, size), mob.level()).size();
    }
}
