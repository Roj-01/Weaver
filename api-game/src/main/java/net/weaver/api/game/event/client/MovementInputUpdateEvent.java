package net.weaver.api.game.event.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.weaver.api.event.Event;

/**
 * Fired every client tick after the local player's movement input was read
 * from the keys ({@code LocalPlayer#aiStep}, after {@code ClientInput#tick}),
 * before it is applied. Change {@link #input()} to steer the player.
 */
public final class MovementInputUpdateEvent extends Event {
    private final LocalPlayer player;
    private final ClientInput input;

    public MovementInputUpdateEvent(LocalPlayer player, ClientInput input) {
        this.player = player;
        this.input = input;
    }

    public LocalPlayer player() {
        return player;
    }

    /** The mutable input: key presses and the movement vector. */
    public ClientInput input() {
        return input;
    }
}
