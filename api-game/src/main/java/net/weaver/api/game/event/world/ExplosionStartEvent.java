package net.weaver.api.game.event.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.event.Event;

/**
 * Fired before an explosion happens in a server level ({@code ServerLevel#explode}).
 * Cancelable: a canceled event prevents the explosion, its damage, and its
 * block breaking.
 */
public final class ExplosionStartEvent extends Event {
    private final ServerLevel level;
    private final Entity source;
    private final Vec3 position;
    private final float radius;
    private final boolean fire;

    public ExplosionStartEvent(ServerLevel level, Entity source, Vec3 position, float radius,
                               boolean fire) {
        super(true);
        this.level = level;
        this.source = source;
        this.position = position;
        this.radius = radius;
        this.fire = fire;
    }

    public ServerLevel level() {
        return level;
    }

    /** The exploding entity (for example a creeper or TNT), or {@code null}. */
    public Entity source() {
        return source;
    }

    public Vec3 position() {
        return position;
    }

    public float radius() {
        return radius;
    }

    public boolean fire() {
        return fire;
    }
}
