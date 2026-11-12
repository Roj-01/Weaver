package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.weaver.api.game.worldgen.BiomeModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Biome spawn modifiers on the biome's own spawn list. Structure spawn
 * overrides (swamp huts, monuments, outposts) take a different branch of
 * {@code getMobsAt} and are left alone.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorSpawnsMixin {
    @WrapOperation(
            method = "getMobsAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/util/random/WeightedList;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/MobSpawnSettings;getMobsToSpawn(Lnet/minecraft/world/entity/MobCategory;)Lnet/minecraft/util/random/WeightedList;"))
    private WeightedList<MobSpawnSettings.SpawnerData> weaver$biomeSpawns(MobSpawnSettings settings,
            MobCategory category, Operation<WeightedList<MobSpawnSettings.SpawnerData>> original,
            @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        return BiomeModifiers.adjustSpawns(level, pos, category, original.call(settings, category));
    }
}
