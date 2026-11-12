package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import net.weaver.api.game.extension.TriState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link BlockExtension#canHarvestBlock} for the drops of a block a player breaks. */
@Mixin(ServerPlayerGameMode.class)
public abstract class HarvestDropsExtensionsMixin {
    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean weaver$harvestDrops(ServerPlayer player, BlockState state, Operation<Boolean> original,
            @Local(argsOnly = true) BlockPos pos) {
        if (state.getBlock() instanceof BlockExtension extension) {
            TriState answer = extension.canHarvestBlock(state, player.level(), pos, player);
            if (answer != TriState.DEFAULT) {
                return answer == TriState.TRUE;
            }
        }
        return original.call(player, state);
    }
}
