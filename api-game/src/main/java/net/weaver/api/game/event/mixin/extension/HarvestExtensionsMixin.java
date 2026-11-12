package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import net.weaver.api.game.extension.TriState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link BlockExtension#canHarvestBlock} for the mining speed. */
@Mixin(BlockBehaviour.class)
public abstract class HarvestExtensionsMixin {
    @WrapOperation(method = "getDestroyProgress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean weaver$harvestSpeed(Player player, BlockState state, Operation<Boolean> original,
            @Local(argsOnly = true) BlockGetter level, @Local(argsOnly = true) BlockPos pos) {
        if (state.getBlock() instanceof BlockExtension extension) {
            TriState answer = extension.canHarvestBlock(state, level, pos, player);
            if (answer != TriState.DEFAULT) {
                return answer == TriState.TRUE;
            }
        }
        return original.call(player, state);
    }
}
