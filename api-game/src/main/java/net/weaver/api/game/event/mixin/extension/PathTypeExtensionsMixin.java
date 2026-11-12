package net.weaver.api.game.event.mixin.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.weaver.api.game.extension.BlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link BlockExtension#getPathType}: how walking mobs see the block. */
@Mixin(WalkNodeEvaluator.class)
public abstract class PathTypeExtensionsMixin {
    @Inject(method = "getPathTypeFromState", at = @At("HEAD"), cancellable = true)
    private static void weaver$pathType(BlockGetter level, BlockPos pos, CallbackInfoReturnable<PathType> callback) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockExtension extension) {
            PathType type = extension.getPathType(state, level, pos);
            if (type != null) {
                callback.setReturnValue(type);
            }
        }
    }
}
