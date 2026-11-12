package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.weaver.api.game.extension.BlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * {@link BlockExtension#shouldDisplayFluidOverlay}. Vanilla draws the overlay when
 * the block on the face is a half-transparent block or leaves, by checking the
 * block's class; for a block that asks for the overlay, that check sees glass.
 */
@Mixin(FluidRenderer.class)
public abstract class FluidOverlayExtensionsMixin {
    @WrapOperation(method = "tesselate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;", ordinal = 0),
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/FluidModel;overlayMaterial()Lnet/minecraft/client/resources/model/sprite/Material$Baked;")))
    private Block weaver$fluidOverlay(BlockState faceState, Operation<Block> original,
            @Local(argsOnly = true) BlockAndTintGetter level, @Local(argsOnly = true) BlockPos pos,
            @Local(argsOnly = true) FluidState fluid, @Local(name = "faceDir") Direction faceDir) {
        Block block = original.call(faceState);
        if (block instanceof BlockExtension extension
                && extension.shouldDisplayFluidOverlay(faceState, level, pos.relative(faceDir), fluid)) {
            return Blocks.GLASS;
        }
        return block;
    }
}
