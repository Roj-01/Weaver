package net.weaver.api.game.event.world;

import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired when an experience orb looks for a player to fly to ({@code ExperienceOrb#followNearbyPlayer}).
 * The target can be replaced, or set to {@code null} so the orb follows no one. Server side.
 */
public final class XpOrbTargetingEvent extends Event {
    private final ExperienceOrb orb;
    private Player target;

    public XpOrbTargetingEvent(ExperienceOrb orb, Player target) {
        super(false);
        this.orb = orb;
        this.target = target;
    }

    /** The orb. */
    public ExperienceOrb orb() {
        return orb;
    }

    /** The player the orb follows, or {@code null}. */
    public Player target() {
        return target;
    }

    public void setTarget(Player target) {
        this.target = target;
    }
}
