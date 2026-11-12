package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.event.support.CrashShield;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A block entity whose ticker throws is suspended instead of crashing the game (see {@link CrashShield}). */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class CrashShieldBlockEntityMixin {
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"))
    private void weaver$guardBlockEntity(BlockEntityTicker<BlockEntity> ticker, Level level, BlockPos pos,
            BlockState state, BlockEntity blockEntity, Operation<Void> original) {
        if (!CrashShield.ENABLED) {
            original.call(ticker, level, pos, state, blockEntity);
            return;
        }
        if (CrashShield.suspended(blockEntity)) {
            return;
        }
        try {
            original.call(ticker, level, pos, state, blockEntity);
        } catch (Throwable failure) {
            if (CrashShield.fatal(failure)) {
                throw CrashShield.rethrow(failure);
            }
            CrashShield.suspend(blockEntity, "block-entity-tick",
                    blockEntity.getType().builtInRegistryHolder().getRegisteredName() + "@" + pos.toShortString(), failure);
        }
    }
}
