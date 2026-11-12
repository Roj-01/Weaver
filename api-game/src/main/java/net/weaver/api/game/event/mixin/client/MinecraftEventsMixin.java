package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ClientPauseChangedEvent;
import net.weaver.api.game.event.client.ClientPlayerNetworkEvent;
import net.weaver.api.game.event.client.RenderFrameEvent;
import net.weaver.api.game.event.client.WindowResizeEvent;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.GameShuttingDownEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame rendering, window resizes, pausing, leaving a world, and shutdown. */
@Mixin(Minecraft.class)
public abstract class MinecraftEventsMixin {
    @Shadow
    private volatile boolean pause;

    @Unique
    private boolean weaver$wasPaused;

    /** The pause state a listener canceled (1 paused, 0 running), held until vanilla's own value changes; -1 none. */
    @Unique
    private int weaver$canceledTarget = -1;

    @Unique
    private Minecraft weaver$self() {
        return (Minecraft) (Object) this;
    }

    @WrapOperation(method = "renderFrame(Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render()V"))
    private void weaver$renderFrame(GameRenderer renderer, Operation<Void> original) {
        if (!GameEvents.hasListeners(RenderFrameEvent.class)) {
            original.call(renderer);
            return;
        }
        DeltaTracker delta = weaver$self().getDeltaTracker();
        GameEvents.post(new RenderFrameEvent(RenderFrameEvent.Phase.PRE, delta), LogicalSide.CLIENT);
        original.call(renderer);
        GameEvents.post(new RenderFrameEvent(RenderFrameEvent.Phase.POST, delta), LogicalSide.CLIENT);
    }

    @Inject(method = "resizeGui()V", at = @At("RETURN"))
    private void weaver$resize(CallbackInfo callback) {
        GameEvents.post(new WindowResizeEvent(weaver$self().getWindow()), LogicalSide.CLIENT);
    }

    @Inject(method = "runTick(Z)V", at = @At("HEAD"))
    private void weaver$pauseBefore(boolean advanceGameTime, CallbackInfo callback) {
        weaver$wasPaused = pause;
    }

    @Inject(method = "runTick(Z)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;pause:Z", opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER))
    private void weaver$pauseAfter(boolean advanceGameTime, CallbackInfo callback) {
        if (pause == weaver$wasPaused) {
            weaver$canceledTarget = -1;
            return;
        }
        // Vanilla computes the pause every frame. A canceled change stays canceled (without
        // asking again every frame) until vanilla's value goes back.
        int target = pause ? 1 : 0;
        if (weaver$canceledTarget == target) {
            pause = weaver$wasPaused;
            return;
        }
        // Right after the store: putting the old value back here cancels the change.
        if (GameEvents.hasListeners(net.weaver.api.game.event.client.ClientPauseChangingEvent.class)
                && GameEvents.canceled(GameEvents.post(
                        new net.weaver.api.game.event.client.ClientPauseChangingEvent(pause), LogicalSide.CLIENT))) {
            weaver$canceledTarget = target;
            pause = weaver$wasPaused;
            return;
        }
        weaver$canceledTarget = -1;
        weaver$wasPaused = pause;
        GameEvents.post(new ClientPauseChangedEvent(pause), LogicalSide.CLIENT);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;resetData()V"))
    private void weaver$logoutDisconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo callback) {
        weaver$logout();
    }

    @Inject(method = "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;resetData()V"))
    private void weaver$logoutClear(Screen screen, CallbackInfo callback) {
        weaver$logout();
    }

    @Unique
    private void weaver$logout() {
        Minecraft self = weaver$self();
        if (self.player != null || self.gameMode != null) {
            GameEvents.post(new ClientPlayerNetworkEvent(ClientPlayerNetworkEvent.Kind.LOGGED_OUT, null, self.player),
                    LogicalSide.CLIENT);
        }
    }

    @Inject(method = "stop()V", at = @At("HEAD"))
    private void weaver$shutdown(CallbackInfo callback) {
        if (weaver$self().isRunning()) {
            GameEvents.post(new GameShuttingDownEvent(), LogicalSide.CLIENT);
        }
    }
}
