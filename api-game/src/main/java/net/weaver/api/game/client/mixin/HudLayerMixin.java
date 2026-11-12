package net.weaver.api.game.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.weaver.api.client.ClientRegistry;
import net.weaver.api.client.HudRenderContext;
import net.weaver.api.game.client.hud.HudRenderRegistry;
import net.weaver.api.game.client.hud.HudRenderStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Forwards the vanilla HUD render pass to the Weaver client HUD layers.
 *
 * <p>The hook is installed by the loader's built-in client mixin configuration;
 * a mod only has to register a {@link net.weaver.api.client.HudLayer}. The
 * injection runs at the tail of {@code Hud.extractRenderState}, so layers draw
 * after vanilla HUD elements.
 *
 * <p>The same tail also dispatches {@link HudRenderStage#POST_HUD} to the
 * game-typed {@link HudRenderRegistry} before the legacy HUD layers run. The
 * legacy layers therefore retain their original top-of-HUD order.
 */
@Mixin(Hud.class)
public abstract class HudLayerMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean REPORTED_STAGE_FAILURE = new AtomicBoolean();

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 1)
    private void weaverRenderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                                 CallbackInfo callback)
    {
        if (graphics == null || deltaTracker == null)
        {
            return;
        }
        Minecraft minecraft;
        try
        {
            minecraft = Minecraft.getInstance();
        }
        catch (Throwable failure)
        {
            report(REPORTED_FAILURE, "CLIENT HUD LAYER HOOK FAILED", failure);
            return;
        }
        if (minecraft == null)
        {
            return;
        }
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        try
        {
            HudRenderRegistry.dispatch(new net.weaver.api.game.client.hud.HudRenderContext(
                    graphics, (Hud) (Object) this, minecraft, graphics.guiWidth(),
                    graphics.guiHeight(), partialTick, HudRenderStage.POST_HUD));
        }
        catch (Throwable failure)
        {
            report(REPORTED_STAGE_FAILURE, "HUD STAGE HOOK FAILED stage=POST_HUD", failure);
        }
        try
        {
            ClientRegistry.renderHud(new HudRenderContext(graphics, minecraft,
                    graphics.guiWidth(), graphics.guiHeight(), partialTick));
        }
        catch (Throwable failure)
        {
            report(REPORTED_FAILURE, "CLIENT HUD LAYER HOOK FAILED", failure);
        }
    }

    private static void report(AtomicBoolean reported, String label, Throwable failure)
    {
        if (reported.compareAndSet(false, true))
        {
            System.err.println("[weaver] " + label + " reason=" + failure);
        }
    }
}
