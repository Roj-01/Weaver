package net.weaver.api.game.event.living;

import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired when an enderman notices a player staring at it and is about to become
 * angry ({@code Enderman#isBeingStaredBy} returned {@code true}). Cancelable: a
 * canceled event treats the player as not staring.
 */
public final class EndermanAngerEvent extends Event {
    private final Enderman enderman;
    private final Player player;

    public EndermanAngerEvent(Enderman enderman, Player player) {
        super(true);
        this.enderman = enderman;
        this.player = player;
    }

    public Enderman enderman() {
        return enderman;
    }

    public Player player() {
        return player;
    }
}
