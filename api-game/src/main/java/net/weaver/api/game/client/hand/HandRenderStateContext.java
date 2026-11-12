package net.weaver.api.game.client.hand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;

import java.util.Objects;

/**
 * A mutable view of the first-person hands and items render state extraction.
 *
 * <p>The callback runs at the return of
 * {@code FirstPersonHandsAndItems#extractRenderState}. Listeners may write the
 * public fields of {@link #state()} to change how the hands render in the same
 * frame. The player and client references are live render-thread objects and
 * must not be retained.
 *
 * @param player      the local player, normally non-null in a live game
 * @param state       the mutable first-person hands state, never {@code null}
 * @param partialTick the frame's partial tick
 * @param minecraft   the client instance, normally non-null in a live game
 */
public record HandRenderStateContext(LocalPlayer player, FirstPersonHandsAndItemsRenderState state,
                                     float partialTick, Minecraft minecraft)
{
    /**
     * Validates the state and the partial tick.
     *
     * @throws NullPointerException     when {@code state} is {@code null}
     */
    public HandRenderStateContext
    {
        Objects.requireNonNull(state, "state");
        // Vanilla can pass a frame time a hair below zero (rounding in its timer):
        // it means the start of the tick, not an error that would cancel the hook.
        if (!(partialTick >= 0.0F))
        {
            partialTick = 0.0F;
        }
    }
}
