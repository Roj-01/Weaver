package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ClientClickEmptyEvent;
import net.weaver.api.game.event.client.ClientInteractionInputEvent;
import net.weaver.api.game.event.client.FlipFrameEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Attack, use, and pick-block key actions, clicks at nothing, and the end of each frame. */
@Mixin(Minecraft.class)
public abstract class MinecraftInputEventsMixin {
    @Unique
    private Minecraft weaver$self() {
        return (Minecraft) (Object) this;
    }

    @Unique
    private boolean weaver$aimsAtNothing() {
        HitResult hit = weaver$self().hitResult;
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }

    @Inject(method = "startAttack()Z", at = @At("HEAD"), cancellable = true)
    private void weaver$attack(CallbackInfoReturnable<Boolean> callback) {
        Minecraft self = weaver$self();
        if (self.player == null) {
            return;
        }
        if (GameEvents.canceled(GameEvents.post(new ClientInteractionInputEvent(ClientInteractionInputEvent.Kind.ATTACK,
                InteractionHand.MAIN_HAND), LogicalSide.CLIENT))) {
            callback.setReturnValue(false);
            return;
        }
        if (weaver$aimsAtNothing()) {
            GameEvents.post(new ClientClickEmptyEvent(ClientClickEmptyEvent.Button.LEFT, self.player), LogicalSide.CLIENT);
        }
    }

    @Inject(method = "startUseItem()V", at = @At("HEAD"), cancellable = true)
    private void weaver$use(CallbackInfo callback) {
        Minecraft self = weaver$self();
        if (self.player == null) {
            return;
        }
        if (GameEvents.canceled(GameEvents.post(new ClientInteractionInputEvent(ClientInteractionInputEvent.Kind.USE,
                InteractionHand.MAIN_HAND), LogicalSide.CLIENT))) {
            callback.cancel();
            return;
        }
        if (weaver$aimsAtNothing() && self.player.getMainHandItem().isEmpty()) {
            GameEvents.post(new ClientClickEmptyEvent(ClientClickEmptyEvent.Button.RIGHT, self.player), LogicalSide.CLIENT);
        }
    }

    @Inject(method = "pickBlockOrEntity()V", at = @At("HEAD"), cancellable = true)
    private void weaver$pick(CallbackInfo callback) {
        if (weaver$self().player == null || weaver$aimsAtNothing()) {
            return;
        }
        if (GameEvents.canceled(GameEvents.post(new ClientInteractionInputEvent(ClientInteractionInputEvent.Kind.PICK_BLOCK,
                InteractionHand.MAIN_HAND), LogicalSide.CLIENT))) {
            callback.cancel();
        }
    }

    @Inject(method = "renderFrame(Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;endFrame()V", shift = At.Shift.AFTER))
    private void weaver$flipFrame(boolean advanceGameTime, CallbackInfo callback) {
        if (GameEvents.hasListeners(FlipFrameEvent.class)) {
            GameEvents.post(new FlipFrameEvent(), LogicalSide.CLIENT);
        }
    }
}
