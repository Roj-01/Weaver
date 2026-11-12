package net.weaver.api.game.event.player;

import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired on the server when a player touches an experience orb
 * ({@code ExperienceOrb#playerTouch}). Cancelable: a canceled event leaves the
 * orb in the world for this tick.
 */
public final class ExperiencePickupEvent extends Event {
    private final Player player;
    private final ExperienceOrb orb;

    public ExperiencePickupEvent(Player player, ExperienceOrb orb) {
        super(true);
        this.player = player;
        this.orb = orb;
    }

    public Player player() {
        return player;
    }

    public ExperienceOrb orb() {
        return orb;
    }
}
