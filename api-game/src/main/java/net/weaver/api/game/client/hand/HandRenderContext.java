package net.weaver.api.game.client.hand;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * A snapshot of one first-person hand render callback.
 *
 * <p>The referenced game objects are live render-thread objects. Use them only
 * inside the listener call and never retain them. The context is immutable, but
 * the referenced render states and drawing objects are not.
 *
 * <p>The whole-pass callbacks use a {@code null} {@link #hand()} and a
 * {@code null} {@link #itemStack()}. The per-hand callback supplies both.
 *
 * @param hand        the hand being drawn, or {@code null} for a whole-pass
 *                    callback
 * @param itemStack   the item held by {@code hand}, or {@code null} for a
 *                    whole-pass callback
 * @param poseStack   the live pose stack, never {@code null}
 * @param collector   the submit collector for this pass, or {@code null} in a
 *                    test
 * @param playerState the first-person player render state, never {@code null}
 * @param handsState  the first-person hands and items state, never {@code null}
 * @param partialTick the frame's partial tick
 * @param minecraft   the client instance, normally non-null in a live game
 * @param phase       the callback phase, never {@code null}
 */
public record HandRenderContext(InteractionHand hand, ItemStack itemStack, PoseStack poseStack,
                                SubmitNodeCollector collector, PlayerRenderState playerState,
                                FirstPersonHandsAndItemsRenderState handsState, float partialTick,
                                Minecraft minecraft, HandRenderPhase phase)
{
    /**
     * Validates the non-null values and the partial tick.
     *
     * @throws NullPointerException     when a required value is {@code null}
     */
    public HandRenderContext
    {
        Objects.requireNonNull(poseStack, "poseStack");
        Objects.requireNonNull(playerState, "playerState");
        Objects.requireNonNull(handsState, "handsState");
        Objects.requireNonNull(phase, "phase");
        // Vanilla can pass a frame time a hair below zero (rounding in its timer):
        // it means the start of the tick, not an error that would cancel the hook.
        if (!(partialTick >= 0.0F))
        {
            partialTick = 0.0F;
        }
    }

    /**
     * Whether this callback targets one individual hand.
     *
     * @return {@code true} when {@link #hand()} is non-null
     */
    public boolean perHand()
    {
        return hand != null;
    }
}
