package net.weaver.api.game.event.player;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired when a player attack decides whether it is a critical hit
 * ({@code Player#attack}, the {@code canCriticalAttack} result for a full
 * strength attack). Set {@link #setCritical(boolean)} to force or prevent the
 * critical hit; a critical hit multiplies the attack's base damage by
 * {@link #damageMultiplier()}, vanilla's 1.5 unless a listener changed it
 * ({@link #setDamageMultiplier(float)}).
 */
public final class CriticalHitEvent extends Event {
    private final Player player;
    private final Entity target;
    private final boolean vanillaCritical;
    private boolean critical;
    private float damageMultiplier = 1.5F;

    public CriticalHitEvent(Player player, Entity target, boolean critical) {
        this.player = player;
        this.target = target;
        this.vanillaCritical = critical;
        this.critical = critical;
    }

    public Player player() {
        return player;
    }

    public Entity target() {
        return target;
    }

    public boolean vanillaCritical() {
        return vanillaCritical;
    }

    public boolean critical() {
        return critical;
    }

    public void setCritical(boolean critical) {
        this.critical = critical;
    }

    /** The factor a critical hit multiplies the base damage by; 1.5 in vanilla. */
    public float damageMultiplier() {
        return damageMultiplier;
    }

    /** Changes the critical damage factor (used only when {@link #critical()} is {@code true}). */
    public void setDamageMultiplier(float damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }
}
