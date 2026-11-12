package net.weaver.api.game.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.client.hand.HandRenderContext;
import net.weaver.api.game.client.hand.HandRenderPhase;
import net.weaver.api.game.client.hand.HandRenderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches the Weaver first-person hand render listeners.
 *
 * <p>Targets verified against Minecraft 26.3:
 * <ul>
 *     <li>{@code FirstPersonHandsAndItemsRenderer#submitHandsWithItems(F,
 *     PoseStack, SubmitNodeCollector, PlayerRenderState,
 *     FirstPersonHandsAndItemsRenderState)V} at {@code HEAD} (cancellable) and
 *     {@code RETURN};</li>
 *     <li>the private {@code submitArmWithItem(PlayerRenderState,
 *     FirstPersonHandsAndItemsRenderState, F, F, InteractionHand, F, ItemStack,
 *     F, PoseStack, SubmitNodeCollector, I)V} at {@code HEAD} (cancellable,
 *     per hand).</li>
 * </ul>
 *
 * <p>The whole-pass callbacks carry no hand or item. The per-hand callback
 * carries both. A cancellation at the whole-pass head also dispatches the post
 * phase so listeners see a balanced pre/post pair.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HandRenderMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true, require = 1)
    private void weaverHandsPre(float partialTick, PoseStack poseStack,
                                SubmitNodeCollector collector, PlayerRenderState playerState,
                                FirstPersonHandsAndItemsRenderState handsState,
                                CallbackInfo callback)
    {
        try
        {
            Minecraft minecraft = Minecraft.getInstance();
            HandRenderContext pre = new HandRenderContext(null, null, poseStack, collector,
                    playerState, handsState, partialTick, minecraft, HandRenderPhase.PRE);
            if (HandRenderRegistry.dispatch(pre))
            {
                HandRenderContext post = new HandRenderContext(null, null, poseStack, collector,
                        playerState, handsState, partialTick, minecraft, HandRenderPhase.POST);
                HandRenderRegistry.dispatch(post);
                callback.cancel();
            }
        }
        catch (Throwable failure)
        {
            reportFailure("HAND RENDER PRE HOOK FAILED", failure);
        }
    }

    @Inject(method = "submitHandsWithItems", at = @At("RETURN"), require = 1)
    private void weaverHandsPost(float partialTick, PoseStack poseStack,
                                 SubmitNodeCollector collector, PlayerRenderState playerState,
                                 FirstPersonHandsAndItemsRenderState handsState,
                                 CallbackInfo callback)
    {
        try
        {
            HandRenderContext post = new HandRenderContext(null, null, poseStack, collector,
                    playerState, handsState, partialTick, Minecraft.getInstance(),
                    HandRenderPhase.POST);
            HandRenderRegistry.dispatch(post);
        }
        catch (Throwable failure)
        {
            reportFailure("HAND RENDER POST HOOK FAILED", failure);
        }
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void weaverHandPre(PlayerRenderState playerState,
                               FirstPersonHandsAndItemsRenderState handsState, float partialTick,
                               float xRot, InteractionHand hand, float swing, ItemStack itemStack,
                               float equipProgress, PoseStack poseStack,
                               SubmitNodeCollector collector, int light, CallbackInfo callback)
    {
        try
        {
            HandRenderContext context = new HandRenderContext(hand, itemStack, poseStack, collector,
                    playerState, handsState, partialTick, Minecraft.getInstance(),
                    HandRenderPhase.PRE);
            if (HandRenderRegistry.dispatch(context))
            {
                callback.cancel();
            }
        }
        catch (Throwable failure)
        {
            reportFailure("HAND RENDER HAND HOOK FAILED", failure);
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
