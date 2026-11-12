package net.weaver.api.game.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.weaver.api.game.client.hud.HudRenderContext;
import net.weaver.api.game.client.hud.HudRenderRegistry;
import net.weaver.api.game.client.hud.HudRenderStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches the post-GUI stage.
 *
 * <p>Target verified against Minecraft 26.3:
 * {@code Gui#extractRenderState(DeltaTracker, boolean, boolean)V} at
 * {@code TAIL}. {@code Gui} does not expose its internal
 * {@code GuiGraphicsExtractor} after extraction, so the stage context has a
 * {@code null} graphics object and reports the scaled window size.
 */
@Mixin(Gui.class)
public abstract class GuiStageMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 1)
    private void weaverGuiPost(DeltaTracker deltaTracker, boolean unused, boolean unused2,
                               CallbackInfo callback)
    {
        try
        {
            Minecraft minecraft = Minecraft.getInstance();
            int width = minecraft == null ? 0 : minecraft.getWindow().getGuiScaledWidth();
            int height = minecraft == null ? 0 : minecraft.getWindow().getGuiScaledHeight();
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            Gui gui = (Gui) (Object) this;
            HudRenderContext context = new HudRenderContext(null, gui.hud, minecraft, width, height,
                    partialTick, HudRenderStage.POST_GUI);
            HudRenderRegistry.dispatch(context);
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] GUI STAGE HOOK FAILED reason=" + failure);
            }
        }
    }
}
