package net.weaver.api.game.event.world;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import net.weaver.api.event.Event;

/**
 * Fired when a projectile (arrow, trident, snowball, fireball, firework,
 * fishing hook, llama spit, shulker bullet, ...) is about to hit something
 * ({@code Projectile#hitTargetOrDeflectSelf}). Cancelable: a canceled event
 * ignores the hit, so the projectile keeps flying through it.
 */
public final class ProjectileImpactEvent extends Event {
    private final Projectile projectile;
    private final HitResult hit;

    public ProjectileImpactEvent(Projectile projectile, HitResult hit) {
        super(true);
        this.projectile = projectile;
        this.hit = hit;
    }

    public Projectile projectile() {
        return projectile;
    }

    /** A {@code BlockHitResult} or an {@code EntityHitResult}. */
    public HitResult hit() {
        return hit;
    }
}
