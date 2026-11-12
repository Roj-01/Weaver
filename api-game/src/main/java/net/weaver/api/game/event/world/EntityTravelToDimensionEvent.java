package net.weaver.api.game.event.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.weaver.api.event.Event;

/**
 * Fired when an entity (including a player) is about to move to another
 * dimension ({@code Entity#teleport} and {@code ServerPlayer#teleport} with a
 * transition into a different level). Cancelable: a canceled event keeps the
 * entity where it is.
 */
public final class EntityTravelToDimensionEvent extends Event {
    private final Entity entity;
    private final ResourceKey<Level> dimension;

    public EntityTravelToDimensionEvent(Entity entity, ResourceKey<Level> dimension) {
        super(true);
        this.entity = entity;
        this.dimension = dimension;
    }

    public Entity entity() {
        return entity;
    }

    /** The target dimension. */
    public ResourceKey<Level> dimension() {
        return dimension;
    }
}
