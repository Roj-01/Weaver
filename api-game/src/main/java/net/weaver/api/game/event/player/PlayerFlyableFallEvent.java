package net.weaver.api.game.event.player;

import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired when a player who is allowed to fly (creative, spectator, or any
 * player with the {@code mayfly} ability) lands ({@code Player#causeFallDamage}).
 * Such players take no fall damage, so the regular fall event is not posted
 * for them; this event lets listeners react to the landing anyway.
 */
public final class PlayerFlyableFallEvent extends Event {
    private final Player player;
    private final double distance;
    private final float multiplier;

    public PlayerFlyableFallEvent(Player player, double distance, float multiplier) {
        this.player = player;
        this.distance = distance;
        this.multiplier = multiplier;
    }

    public Player player() {
        return player;
    }

    public double distance() {
        return distance;
    }

    public float multiplier() {
        return multiplier;
    }
}
