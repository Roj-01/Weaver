package net.weaver.api.game.event.mixin.extension;

import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link BlockExtension#isStickyBlock} and {@link BlockExtension#canStickTo} for pistons. */
@Mixin(PistonStructureResolver.class)
public abstract class PistonExtensionsMixin {
    @Inject(method = "isSticky", at = @At("HEAD"), cancellable = true)
    private static void weaver$sticky(BlockState state, CallbackInfoReturnable<Boolean> callback) {
        if (state.getBlock() instanceof BlockExtension extension) {
            callback.setReturnValue(extension.isStickyBlock(state));
        }
    }

    @Inject(method = "canStickToEachOther", at = @At("HEAD"), cancellable = true)
    private static void weaver$stick(BlockState state, BlockState other, CallbackInfoReturnable<Boolean> callback) {
        if (state.getBlock() instanceof BlockExtension extension && !extension.canStickTo(state, other)
                || other.getBlock() instanceof BlockExtension otherExtension && !otherExtension.canStickTo(other, state)) {
            callback.setReturnValue(false);
        }
    }
}
