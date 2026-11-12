package net.weaver.api.game.event.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.weaver.api.game.lookup.BlockLookupCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps {@link BlockLookupCache}s honest: a changed block, block entity, or unloaded chunk invalidates them. */
@Mixin(LevelChunk.class)
public abstract class LookupCacheInvalidationMixin {
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"))
    private void weaver$blockChanged(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> callback) {
        if (callback.getReturnValue() != null && BlockLookupCache.anyCaches()) {
            BlockLookupCache.invalidate(((LevelChunk) (Object) this).getLevel(), pos);
        }
    }

    @Inject(method = "setBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)V", at = @At("RETURN"))
    private void weaver$blockEntitySet(BlockEntity blockEntity, CallbackInfo callback) {
        if (BlockLookupCache.anyCaches()) {
            BlockLookupCache.invalidate(((LevelChunk) (Object) this).getLevel(), blockEntity.getBlockPos());
        }
    }

    @Inject(method = "removeBlockEntity(Lnet/minecraft/core/BlockPos;)V", at = @At("RETURN"))
    private void weaver$blockEntityRemoved(BlockPos pos, CallbackInfo callback) {
        if (BlockLookupCache.anyCaches()) {
            BlockLookupCache.invalidate(((LevelChunk) (Object) this).getLevel(), pos);
        }
    }

    @Inject(method = "setLoaded(Z)V", at = @At("RETURN"))
    private void weaver$chunkLoaded(boolean loaded, CallbackInfo callback) {
        if (!loaded && BlockLookupCache.anyCaches()) {
            LevelChunk chunk = (LevelChunk) (Object) this;
            BlockLookupCache.invalidateChunk(chunk.getLevel(), chunk.getPos());
        }
    }
}
