package net.weaver.api.game.event.mixin;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.registry.BlockEntityValidBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link BlockEntityValidBlocks}: blocks added for a block entity type are valid for it. */
@Mixin(BlockEntityType.class)
public abstract class BlockEntityValidBlocksMixin {
    @Inject(method = "isValid", at = @At("RETURN"), cancellable = true)
    private void weaver$extraBlocks(BlockState state, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() && BlockEntityValidBlocks.has((BlockEntityType<?>) (Object) this, state.getBlock())) {
            callback.setReturnValue(true);
        }
    }
}
