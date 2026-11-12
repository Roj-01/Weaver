package net.weaver.api.game.event.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.weaver.api.event.Event;

/**
 * Fired when an entity works out its size again ({@code Entity#refreshDimensions}: on spawn, pose
 * change, and when a mob grows). The new dimensions, which include the eye height, can be
 * replaced; for example {@code event.setNewDimensions(event.newDimensions().scale(2))}. Both sides.
 */
public final class EntitySizeEvent extends Event {
    private final Entity entity;
    private final Pose pose;
    private final EntityDimensions oldDimensions;
    private EntityDimensions newDimensions;

    public EntitySizeEvent(Entity entity, Pose pose, EntityDimensions oldDimensions, EntityDimensions newDimensions) {
        super(false);
        this.entity = entity;
        this.pose = pose;
        this.oldDimensions = oldDimensions;
        this.newDimensions = newDimensions;
    }

    /** The entity. */
    public Entity entity() {
        return entity;
    }

    /** The pose the size is for. */
    public Pose pose() {
        return pose;
    }

    /** The size before. */
    public EntityDimensions oldDimensions() {
        return oldDimensions;
    }

    /** The size the entity takes. */
    public EntityDimensions newDimensions() {
        return newDimensions;
    }

    public void setNewDimensions(EntityDimensions newDimensions) {
        this.newDimensions = newDimensions;
    }
}
