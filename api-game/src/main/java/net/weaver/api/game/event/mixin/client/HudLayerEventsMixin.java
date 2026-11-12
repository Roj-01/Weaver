package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.weaver.api.game.client.hud.VanillaHudLayers;
import net.weaver.api.game.event.support.HudLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code HudLayerRenderEvent} for each part of the game's HUD: the private
 * {@code Hud#extract...} method of each part is skipped when its event is
 * canceled. The crosshair and the hotbar are asked in {@code HudStageMixin},
 * after their {@code HudRenderRegistry} stage.
 */
@Mixin(Hud.class)
public abstract class HudLayerEventsMixin {
    @Inject(method = "extractCameraOverlays", at = @At("HEAD"), cancellable = true)
    private void weaver$cameraOverlays(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.CAMERA_OVERLAYS)) callback.cancel();
    }

    @Inject(method = "extractHearts", at = @At("HEAD"), cancellable = true)
    private void weaver$hearts(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.PLAYER_HEALTH)) callback.cancel();
    }

    @Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true)
    private static void weaver$armor(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.ARMOR_LEVEL)) callback.cancel();
    }

    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void weaver$food(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.FOOD_LEVEL)) callback.cancel();
    }

    @Inject(method = "extractAirBubbles", at = @At("HEAD"), cancellable = true)
    private void weaver$air(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.AIR_LEVEL)) callback.cancel();
    }

    @Inject(method = "extractVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void weaver$vehicleHealth(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.VEHICLE_HEALTH)) callback.cancel();
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void weaver$selectedItemName(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.SELECTED_ITEM_NAME)) callback.cancel();
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void weaver$effects(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.EFFECTS)) callback.cancel();
    }

    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    private void weaver$bossOverlay(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.BOSS_OVERLAY)) callback.cancel();
    }

    @Inject(method = "extractSleepOverlay", at = @At("HEAD"), cancellable = true)
    private void weaver$sleepOverlay(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.SLEEP_OVERLAY)) callback.cancel();
    }

    @Inject(method = "extractDemoOverlay", at = @At("HEAD"), cancellable = true)
    private void weaver$demoOverlay(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.DEMO_OVERLAY)) callback.cancel();
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void weaver$scoreboard(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.SCOREBOARD_SIDEBAR)) callback.cancel();
    }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void weaver$overlayMessage(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.OVERLAY_MESSAGE)) callback.cancel();
    }

    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
    private void weaver$title(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.TITLE)) callback.cancel();
    }

    @Inject(method = "extractChat", at = @At("HEAD"), cancellable = true)
    private void weaver$chat(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.CHAT)) callback.cancel();
    }

    @Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
    private void weaver$tabList(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.TAB_LIST)) callback.cancel();
    }

    @Inject(method = "extractSubtitleOverlay", at = @At("HEAD"), cancellable = true)
    private void weaver$subtitles(CallbackInfo callback) {
        if (!HudLayers.shown(VanillaHudLayers.SUBTITLE_OVERLAY)) callback.cancel();
    }

    @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void weaver$barBackground(ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker delta,
                                      Operation<Void> original) {
        if (HudLayers.shown(VanillaHudLayers.CONTEXTUAL_INFO_BAR)) original.call(bar, graphics, delta);
    }

    @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private void weaver$bar(ContextualBar bar, GuiGraphicsExtractor graphics, DeltaTracker delta,
                            Operation<Void> original) {
        if (HudLayers.shown(VanillaHudLayers.CONTEXTUAL_INFO_BAR)) original.call(bar, graphics, delta);
    }

    @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
    private void weaver$experienceLevel(GuiGraphicsExtractor graphics, Font font, int level,
                                        Operation<Void> original) {
        if (HudLayers.shown(VanillaHudLayers.EXPERIENCE_LEVEL)) original.call(graphics, font, level);
    }
}
