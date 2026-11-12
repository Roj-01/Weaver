package net.weaver.api.game.event.player;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired when a player attack decides whether it sweeps nearby entities
 * ({@code Player#attack}, the {@code isSweepAttack} result). Set
 * {@link #setSweeping(boolean)} to force or prevent the sweep.
 */
public final class SweepAttackEvent extends Event {
    private final Player player;
    private final Entity target;
    private final boolean vanillaSweeping;
    private boolean sweeping;

    public SweepAttackEvent(Player player, Entity target, boolean sweeping) {
        this.player = player;
        this.target = target;
        this.vanillaSweeping = sweeping;
        this.sweeping = sweeping;
    }

    public Player player() {
        return player;
    }

    public Entity target() {
        return target;
    }

    public boolean vanillaSweeping() {
        return vanillaSweeping;
    }

    public boolean sweeping() {
        return sweeping;
    }

    public void setSweeping(boolean sweeping) {
        this.sweeping = sweeping;
    }
}
