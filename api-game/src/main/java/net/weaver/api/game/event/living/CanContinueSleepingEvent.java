package net.weaver.api.game.event.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired every tick for a sleeping entity (players and villagers) when the
 * game checks whether it may keep sleeping: its bed still exists
 * ({@code LivingEntity#checkBedExists}), and for players, whether the bed's
 * rule still allows sleep at this time ({@code Player#tick}). Set
 * {@link #setContinueSleeping(boolean)} to wake the entity or keep it asleep.
 */
public final class CanContinueSleepingEvent extends Event {
    private final LivingEntity entity;
    private final Player.BedSleepingProblem problem;
    private boolean continueSleeping;

    public CanContinueSleepingEvent(LivingEntity entity, Player.BedSleepingProblem problem) {
        this.entity = entity;
        this.problem = problem;
        this.continueSleeping = problem == null;
    }

    public LivingEntity entity() {
        return entity;
    }

    /** Why vanilla would wake the entity, or {@code null} when it would not. */
    public Player.BedSleepingProblem problem() {
        return problem;
    }

    public boolean continueSleeping() {
        return continueSleeping;
    }

    public void setContinueSleeping(boolean continueSleeping) {
        this.continueSleeping = continueSleeping;
    }
}
