package net.weaver.api.game.event.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import net.weaver.api.game.entity.SpawnPlacementRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extra spawn conditions mods add to any type, checked after the type rule. */
@Mixin(SpawnPlacements.class)
public abstract class SpawnPlacementsMixin {
    @Inject(method = "checkSpawnRules", at = @At("RETURN"), cancellable = true)
    private static <T extends Entity> void weaver$extraRules(EntityType<T> type, ServerLevelAccessor level,
            EntitySpawnReason reason, BlockPos pos, RandomSource random, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() && !SpawnPlacementRules.check(type, level, reason, pos, random)) {
            callback.setReturnValue(false);
        }
    }
}
