package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ClientLeftClickBlockEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The local player starting and continuing to mine a block. */
@Mixin(MultiPlayerGameMode.class)
public abstract class GameModeEventsMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void weaver$startDestroy(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> callback) {
        weaver$leftClick(ClientLeftClickBlockEvent.Phase.START, pos, face, callback);
    }

    @Inject(method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private void weaver$continueDestroy(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> callback) {
        weaver$leftClick(ClientLeftClickBlockEvent.Phase.CONTINUE, pos, face, callback);
    }

    @Unique
    private void weaver$leftClick(ClientLeftClickBlockEvent.Phase phase, BlockPos pos, Direction face,
                                  CallbackInfoReturnable<Boolean> callback) {
        if (minecraft.player == null || !GameEvents.hasListeners(ClientLeftClickBlockEvent.class)) {
            return;
        }
        if (GameEvents.canceled(GameEvents.post(new ClientLeftClickBlockEvent(phase, minecraft.player, pos, face),
                LogicalSide.CLIENT))) {
            callback.setReturnValue(false);
        }
    }
}
