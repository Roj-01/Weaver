package net.weaver.api.game.event.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.weaver.api.event.Event;

/**
 * Fired on the client when the game computes a player's field-of-view
 * multiplier from speed, flying, and drawing a bow
 * ({@code AbstractClientPlayer#getFieldOfViewModifier}). Fires every frame.
 * {@link #setModifier(float)} changes the multiplier; {@code 1} is no change.
 * For the camera's final field of view use {@code FovModifierRegistry}.
 */
public final class FovModifierEvent extends Event {
    private final AbstractClientPlayer player;
    private final float originalModifier;
    private float modifier;

    public FovModifierEvent(AbstractClientPlayer player, float modifier) {
        this.player = player;
        this.originalModifier = modifier;
        this.modifier = modifier;
    }

    public AbstractClientPlayer player() {
        return player;
    }

    public float originalModifier() {
        return originalModifier;
    }

    public float modifier() {
        return modifier;
    }

    public void setModifier(float modifier) {
        net.weaver.api.game.event.support.Finite.check(modifier, "modifier");
        this.modifier = modifier;
    }
}
