package net.weaver.api.game.event.client;

import java.util.Objects;
import net.minecraft.client.player.LocalPlayer;
import net.weaver.api.event.Event;

/**
 * Fired on the client when the local player clicks at nothing (no block or
 * entity in reach): {@code LEFT} when attacking ({@code Minecraft#startAttack}),
 * {@code RIGHT} when using with an empty main hand ({@code Minecraft#startUseItem}).
 * Nothing is sent to the server for such clicks; send your own packet to act
 * on them there.
 */
public final class ClientClickEmptyEvent extends Event {
    /** Which button. */
    public enum Button {
        LEFT,
        RIGHT
    }

    private final Button button;
    private final LocalPlayer player;

    public ClientClickEmptyEvent(Button button, LocalPlayer player) {
        this.button = Objects.requireNonNull(button, "button");
        this.player = player;
    }

    public Button button() {
        return button;
    }

    public LocalPlayer player() {
        return player;
    }
}
