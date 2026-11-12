package net.weaver.api.game.event.player;

import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired when a player leaves a bed ({@code Player#stopSleepInBed}).
 */
public final class PlayerWakeUpEvent extends Event {
    private final Player player;
    private final boolean wakeImmediately;
    private final boolean updateLevel;

    public PlayerWakeUpEvent(Player player, boolean wakeImmediately, boolean updateLevel) {
        this.player = player;
        this.wakeImmediately = wakeImmediately;
        this.updateLevel = updateLevel;
    }

    public Player player() {
        return player;
    }

    /** Whether the player woke up at once instead of after the sleep timer. */
    public boolean wakeImmediately() {
        return wakeImmediately;
    }

    /** Whether the level updates its sleeping-player count for this wake-up. */
    public boolean updateLevel() {
        return updateLevel;
    }
}
