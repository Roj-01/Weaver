package net.weaver.api.game.event.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.weaver.api.event.Event;

/**
 * Fired when lightning is about to strike an entity ({@code Entity#thunderHit}).
 * Cancelable: a canceled event spares the entity (no damage, fire, or
 * conversion such as pig to piglin).
 */
public final class EntityStruckByLightningEvent extends Event {
    private final Entity entity;
    private final LightningBolt lightning;

    public EntityStruckByLightningEvent(Entity entity, LightningBolt lightning) {
        super(true);
        this.entity = entity;
        this.lightning = lightning;
    }

    public Entity entity() {
        return entity;
    }

    public LightningBolt lightning() {
        return lightning;
    }
}
