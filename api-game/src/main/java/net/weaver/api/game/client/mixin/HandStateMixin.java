package net.weaver.api.game.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.weaver.api.game.client.hand.HandRenderRegistry;
import net.weaver.api.game.client.hand.HandRenderStateContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches first-person hands state mutation after vanilla extraction.
 *
 * <p>Target verified against Minecraft 26.3:
 * {@code FirstPersonHandsAndItems#extractRenderState(LocalPlayer, float,
 * FirstPersonHandsAndItemsRenderState)V} at {@code RETURN}. Listeners may write
 * the public fields of the render state before the same frame renders.
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class HandStateMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "extractRenderState", at = @At("RETURN"), require = 1)
    private void weaverHandState(LocalPlayer player, float partialTick,
                                 FirstPersonHandsAndItemsRenderState state,
                                 CallbackInfo callback)
    {
        try
        {
            HandRenderRegistry.dispatchState(new HandRenderStateContext(player, state,
                    partialTick, Minecraft.getInstance()));
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] HAND RENDER STATE HOOK FAILED reason=" + failure);
            }
        }
    }
}
