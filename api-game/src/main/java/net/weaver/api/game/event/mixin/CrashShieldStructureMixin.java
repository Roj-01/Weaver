package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.weaver.api.game.event.support.CrashShield;
import org.spongepowered.asm.mixin.Mixin;

/** A structure whose pieces throw while placing is skipped for that chunk (see {@link CrashShield}). */
@Mixin(StructureStart.class)
public abstract class CrashShieldStructureMixin {
    @WrapMethod(method = "placeInChunk(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;)V")
    private void weaver$guardStructure(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunk, Operation<Void> original) {
        if (!CrashShield.ENABLED) {
            original.call(level, structures, generator, random, box, chunk);
            return;
        }
        try {
            original.call(level, structures, generator, random, box, chunk);
        } catch (Throwable failure) {
            if (CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            CrashShield.report("structure", ((StructureStart) (Object) this).getStructure().getClass().getName()
                    + "@" + chunk, "skipped-in-chunk", failure);
        }
    }
}
