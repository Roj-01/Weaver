package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ExtractBlockScreenEffectEvent;
import net.weaver.api.game.event.client.ExtractLevelRenderStateEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The end of each frame's level render-state extraction, and the full-screen overlays. */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorEventsMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "extract(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V", at = @At("RETURN"))
    private void weaver$extracted(DeltaTracker delta, Camera camera, float partialTick, CallbackInfo callback) {
        if (GameEvents.hasListeners(ExtractLevelRenderStateEvent.class)) {
            GameEvents.post(new ExtractLevelRenderStateEvent((LevelExtractor) (Object) this, camera, delta), LogicalSide.CLIENT);
        }
    }

    @WrapOperation(method = "extractPlayerState(Lnet/minecraft/client/Camera;Lnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/renderer/state/level/PlayerRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;getViewBlockingState(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState weaver$blockOverlay(LocalPlayer player, Frustum frustum, Operation<BlockState> original,
            @Local(argsOnly = true) PlayerRenderState state) {
        BlockState blocking = original.call(player, frustum);
        if (blocking == null || !GameEvents.hasListeners(ExtractBlockScreenEffectEvent.class)) {
            return blocking;
        }
        ExtractBlockScreenEffectEvent event = GameEvents.post(new ExtractBlockScreenEffectEvent(
                player, ExtractBlockScreenEffectEvent.Type.BLOCK, blocking, state), LogicalSide.CLIENT);
        return GameEvents.canceled(event) ? null : event.blockState();
    }

    @Inject(method = "extractPlayerState(Lnet/minecraft/client/Camera;Lnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/renderer/state/level/PlayerRenderState;)V",
            at = @At("RETURN"))
    private void weaver$liquidOverlays(Camera camera, DeltaTracker delta, float partialTick, PlayerRenderState state,
            CallbackInfo callback) {
        LocalPlayer player = minecraft.player;
        if (player == null || !GameEvents.hasListeners(ExtractBlockScreenEffectEvent.class)) {
            return;
        }
        if (state.isOnFire && GameEvents.canceled(GameEvents.post(new ExtractBlockScreenEffectEvent(
                player, ExtractBlockScreenEffectEvent.Type.FIRE, null, state), LogicalSide.CLIENT))) {
            state.isOnFire = false;
        }
        if (state.waterOverlay != null && GameEvents.canceled(GameEvents.post(new ExtractBlockScreenEffectEvent(
                player, ExtractBlockScreenEffectEvent.Type.WATER, null, state), LogicalSide.CLIENT))) {
            state.waterOverlay = null;
        }
    }
}
