package net.weaver.api.game.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.weaver.api.game.client.hud.HudRenderContext;
import net.weaver.api.game.client.hud.HudRenderRegistry;
import net.weaver.api.game.client.hud.HudRenderStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches the early, crosshair, and hotbar HUD stages.
 *
 * <p>Targets verified against Minecraft 26.3:
 * <ul>
 *     <li>{@code Hud#extractRenderState(GuiGraphicsExtractor, DeltaTracker)V}
 *     at {@code HEAD};</li>
 *     <li>the private {@code Hud#extractCrosshair(GuiGraphicsExtractor,
 *     DeltaTracker)V} at {@code HEAD};</li>
 *     <li>the private {@code Hud#extractItemHotbar(GuiGraphicsExtractor,
 *     DeltaTracker)V} at {@code HEAD}.</li>
 * </ul>
 *
 * <p>After the crosshair and hotbar stages, the crosshair and the hotbar are
 * left out when a {@code HudLayerRenderEvent} for them is canceled; the stage
 * listeners have run by then, so they can draw a replacement.
 *
 * <p>The post-HUD stage is dispatched by {@code HudLayerMixin} at the tail of
 * {@code Hud#extractRenderState}, immediately before the legacy
 * {@code ClientRegistry.renderHud} call, so legacy HUD layers keep their
 * original position.
 */
@Mixin(Hud.class)
public abstract class HudStageMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 1)
    private void weaverHudPre(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                              CallbackInfo callback)
    {
        dispatch(HudRenderStage.PRE_HUD, graphics, deltaTracker);
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), require = 1, cancellable = true)
    private void weaverHudCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                    CallbackInfo callback)
    {
        dispatch(HudRenderStage.CROSSHAIR, graphics, deltaTracker);
        if (!net.weaver.api.game.event.support.HudLayers.shown(
                net.weaver.api.game.client.hud.VanillaHudLayers.CROSSHAIR))
        {
            callback.cancel();
        }
    }

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), require = 1, cancellable = true)
    private void weaverHudHotbar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                 CallbackInfo callback)
    {
        dispatch(HudRenderStage.HOTBAR, graphics, deltaTracker);
        if (!net.weaver.api.game.event.support.HudLayers.shown(
                net.weaver.api.game.client.hud.VanillaHudLayers.HOTBAR))
        {
            callback.cancel();
        }
    }

    private void dispatch(HudRenderStage stage, GuiGraphicsExtractor graphics,
                          DeltaTracker deltaTracker)
    {
        try
        {
            Minecraft minecraft = Minecraft.getInstance();
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            HudRenderContext context = new HudRenderContext(graphics, (Hud) (Object) this,
                    minecraft, graphics.guiWidth(), graphics.guiHeight(), partialTick, stage);
            HudRenderRegistry.dispatch(context);
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] HUD STAGE HOOK FAILED stage=" + stage
                        + " reason=" + failure);
            }
        }
    }
}
