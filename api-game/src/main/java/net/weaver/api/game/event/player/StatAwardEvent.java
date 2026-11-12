package net.weaver.api.game.event.player;

import net.minecraft.stats.Stat;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired when a player's statistic is set to a new value
 * ({@code StatsCounter#setValue}, which every {@code awardStat} goes through).
 * Fires very often (distance walked, time played); keep listeners cheap.
 * Cancelable: a canceled event keeps the old value. {@link #setValue(int)}
 * stores a different value.
 */
public final class StatAwardEvent extends Event {
    private final Player player;
    private final Stat<?> stat;
    private int value;

    public StatAwardEvent(Player player, Stat<?> stat, int value) {
        super(true);
        this.player = player;
        this.stat = stat;
        this.value = value;
    }

    public Player player() {
        return player;
    }

    public Stat<?> stat() {
        return stat;
    }

    /** The new total, not the increment. */
    public int value() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
