package net.weaver.api.game.event.world;

import net.minecraft.world.entity.Entity;
import net.weaver.api.event.Event;

/**
 * Fired when an entity is about to mount or dismount another entity
 * ({@code Entity#startRiding} and {@code Entity#removeVehicle}). Cancelable: a
 * canceled mount does not happen, and a canceled dismount keeps the rider on
 * its vehicle.
 */
public final class EntityMountEvent extends Event {
    private final Entity rider;
    private final Entity vehicle;
    private final boolean mounting;

    public EntityMountEvent(Entity rider, Entity vehicle, boolean mounting) {
        super(true);
        this.rider = rider;
        this.vehicle = vehicle;
        this.mounting = mounting;
    }

    public Entity rider() {
        return rider;
    }

    public Entity vehicle() {
        return vehicle;
    }

    /** {@code true} when mounting, {@code false} when dismounting. */
    public boolean mounting() {
        return mounting;
    }
}
