package net.weaver.api.game.event.mixin.extension;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link BlockExtension#hidesNeighborFace}: the neighbor may hide the face that touches it. */
@Mixin(Block.class)
public abstract class FaceHidingExtensionsMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void weaver$hiddenByNeighbor(BlockState state, BlockState neighbor, Direction direction,
            CallbackInfoReturnable<Boolean> callback) {
        if (neighbor.getBlock() instanceof BlockExtension extension
                && extension.hidesNeighborFace(neighbor, state, direction.getOpposite())) {
            callback.setReturnValue(false);
        }
    }
}
