package net.weaver.api.game.event.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.event.Event;

/**
 * Fired when an explosion is about to push an entity
 * ({@code Entity#pushFromExplosion} inside {@code ServerExplosion#hurtEntities}).
 * The knockback vector can be changed; set {@link Vec3#ZERO} for no push.
 */
public final class ExplosionKnockbackEvent extends Event {
    private final ServerLevel level;
    private final ServerExplosion explosion;
    private final Entity entity;
    private Vec3 knockback;

    public ExplosionKnockbackEvent(ServerLevel level, ServerExplosion explosion, Entity entity, Vec3 knockback) {
        this.level = level;
        this.explosion = explosion;
        this.entity = entity;
        this.knockback = knockback;
    }

    public ServerLevel level() {
        return level;
    }

    public ServerExplosion explosion() {
        return explosion;
    }

    public Entity entity() {
        return entity;
    }

    public Vec3 knockback() {
        return knockback;
    }

    public void setKnockback(Vec3 knockback) {
        this.knockback = java.util.Objects.requireNonNull(knockback, "knockback");
    }
}
