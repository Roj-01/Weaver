package net.weaver.api.game.event.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.event.Event;

/**
 * Fired when an entity is about to teleport: random teleports of endermen,
 * shulkers, and chorus fruit ({@code LivingEntity#randomTeleport}), ender pearls
 * landing ({@code ThrownEnderpearl#onHit}), entities moved by
 * {@code /spreadplayers}, and every entity moved by {@code /tp} or
 * {@code /teleport}. Cancelable: a canceled event does not teleport (a canceled
 * pearl is removed without teleporting). The target can be changed for random,
 * spread-players, and command teleports. A player moved by a command also gets
 * {@code PlayerTeleportEvent}; portal teleports of players are only
 * {@code PlayerTeleportEvent}.
 */
public final class EntityTeleportEvent extends Event {
    /** Why the entity teleports. */
    public enum Cause {
        RANDOM,
        ENDER_PEARL,
        /** The {@code /spreadplayers} command. */
        SPREAD_PLAYERS,
        /** The {@code /tp} and {@code /teleport} commands; the target is absolute. */
        COMMAND
    }

    private final Entity entity;
    private final Cause cause;
    private Vec3 target;

    public EntityTeleportEvent(Entity entity, Cause cause, Vec3 target) {
        super(true);
        this.entity = entity;
        this.cause = cause;
        this.target = target;
    }

    /** The teleporting entity; for an ender pearl, the pearl's owner. */
    public Entity entity() {
        return entity;
    }

    public Cause cause() {
        return cause;
    }

    public Vec3 target() {
        return target;
    }

    /** Changes the target; honored for {@link Cause#RANDOM}, {@link Cause#SPREAD_PLAYERS}, and {@link Cause#COMMAND}. */
    public void setTarget(Vec3 target) {
        this.target = java.util.Objects.requireNonNull(target, "target");
    }
}
