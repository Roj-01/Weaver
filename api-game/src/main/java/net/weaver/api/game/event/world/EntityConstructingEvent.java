package net.weaver.api.game.event.world;

import net.minecraft.world.entity.Entity;
import net.weaver.api.event.Event;

/**
 * Fired at the end of every entity's base constructor ({@code Entity(EntityType, Level)}), before
 * the subclass constructor finishes and before the entity is in a level. Use it to set up
 * per-entity state such as capability defaults; do not read subclass fields here. Both sides;
 * this is a hot path, so keep listeners cheap.
 */
public final class EntityConstructingEvent extends Event {
    private final Entity entity;

    public EntityConstructingEvent(Entity entity) {
        super(false);
        this.entity = entity;
    }

    /** The entity being built. */
    public Entity entity() {
        return entity;
    }
}
