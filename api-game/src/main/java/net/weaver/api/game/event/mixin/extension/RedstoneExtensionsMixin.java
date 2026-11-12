package net.weaver.api.game.event.mixin.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import net.weaver.api.game.extension.TriState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link BlockExtension#connectsToRedstone}: whether dust next to the block connects to it. */
@Mixin(RedstoneWireBlock.class)
public abstract class RedstoneExtensionsMixin {
    @Inject(method = "shouldConnectTo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void weaver$connects(BlockState state, BlockGetter level, BlockPos pos, Direction direction,
            CallbackInfoReturnable<Boolean> callback) {
        if (state.getBlock() instanceof BlockExtension extension) {
            TriState answer = extension.connectsToRedstone(state, level, pos, direction);
            if (answer != TriState.DEFAULT) {
                callback.setReturnValue(answer == TriState.TRUE);
            }
        }
    }
}
