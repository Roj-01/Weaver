package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.weaver.api.game.event.support.CrashShield;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A feature that throws while placing is skipped for that chunk (see {@link CrashShield}). */
@Mixin(ChunkGenerator.class)
public abstract class CrashShieldWorldgenMixin {
    @WrapOperation(method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/placement/FeaturePlacer;placeWithBiomeCheck(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean weaver$guardFeature(FeaturePlacer placer, PlacedFeature feature, RandomSource random, BlockPos origin,
            Operation<Boolean> original) {
        if (!CrashShield.ENABLED) {
            return original.call(placer, feature, random, origin);
        }
        try {
            return original.call(placer, feature, random, origin);
        } catch (Throwable failure) {
            if (CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            CrashShield.report("feature", CrashShield.featureName(feature), "skipped-in-chunk", failure);
            return false;
        }
    }
}
