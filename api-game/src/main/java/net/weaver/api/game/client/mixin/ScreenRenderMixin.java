package net.weaver.api.game.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.weaver.api.game.client.screen.ScreenRenderContext;
import net.weaver.api.game.client.screen.ScreenRenderPhase;
import net.weaver.api.game.client.screen.ScreenRenderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches the Weaver screen render phases.
 *
 * <p>Targets verified against Minecraft 26.3:
 * <ul>
 *     <li>{@code Screen#extractRenderState(GuiGraphicsExtractor, int, int,
 *     float)V} at {@code HEAD} and {@code RETURN};</li>
 *     <li>{@code Screen#extractBackground(GuiGraphicsExtractor, int, int,
 *     float)V} at {@code HEAD}, cancellable.</li>
 * </ul>
 *
 * <p>The target is the general {@code Screen} class, not a concrete screen, so
 * mod screens inherit the hooks. A subclass override that does not call the
 * super implementation bypasses the corresponding hook for its own drawing.
 */
@Mixin(Screen.class)
public abstract class ScreenRenderMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 1)
    private void weaverScreenPre(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                 float partialTick, CallbackInfo callback)
    {
        dispatch(ScreenRenderPhase.PRE, graphics, mouseX, mouseY, partialTick);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"), require = 1)
    private void weaverScreenPost(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                  float partialTick, CallbackInfo callback)
    {
        dispatch(ScreenRenderPhase.POST, graphics, mouseX, mouseY, partialTick);
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true, require = 1)
    private void weaverScreenBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                        float partialTick, CallbackInfo callback)
    {
        try
        {
            ScreenRenderContext context = new ScreenRenderContext((Screen) (Object) this,
                    graphics, mouseX, mouseY, partialTick, ScreenRenderPhase.BACKGROUND,
                    Minecraft.getInstance());
            if (ScreenRenderRegistry.dispatch(context))
            {
                callback.cancel();
            }
        }
        catch (Throwable failure)
        {
            reportFailure("SCREEN BACKGROUND HOOK FAILED", failure);
        }
    }

    private void dispatch(ScreenRenderPhase phase, GuiGraphicsExtractor graphics, int mouseX,
                          int mouseY, float partialTick)
    {
        try
        {
            ScreenRenderContext context = new ScreenRenderContext((Screen) (Object) this, graphics,
                    mouseX, mouseY, partialTick, phase, Minecraft.getInstance());
            ScreenRenderRegistry.dispatch(context);
        }
        catch (Throwable failure)
        {
            reportFailure("SCREEN RENDER HOOK FAILED phase=" + phase, failure);
        }
    }

    private static void reportFailure(String label, Throwable failure)
    {
        if (REPORTED_FAILURE.compareAndSet(false, true))
        {
            System.err.println("[weaver] " + label + " reason=" + failure);
        }
    }
}
