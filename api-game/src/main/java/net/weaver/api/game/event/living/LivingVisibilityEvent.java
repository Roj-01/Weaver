package net.weaver.api.game.event.living;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired when a mob works out how visible a living entity is ({@code LivingEntity#getVisibilityPercent}),
 * which scales the range a mob notices it from (sneaking, invisibility, mob heads). The visibility can
 * be changed; {@link #modifyVisibility} multiplies it. Server side.
 */
public final class LivingVisibilityEvent extends Event {
    private final LivingEntity entity;
    private final Entity lookingEntity;
    private double visibility;

    public LivingVisibilityEvent(LivingEntity entity, Entity lookingEntity, double visibility) {
        super(false);
        this.entity = entity;
        this.lookingEntity = lookingEntity;
        this.visibility = visibility;
    }

    /** The entity being seen. */
    public LivingEntity entity() {
        return entity;
    }

    /** The entity looking, possibly {@code null}. */
    public Entity lookingEntity() {
        return lookingEntity;
    }

    /** The visibility factor; 1 is fully visible. */
    public double visibility() {
        return visibility;
    }

    public void setVisibility(double visibility) {
        net.weaver.api.game.event.support.Finite.check(visibility, "visibility");
        this.visibility = visibility;
    }

    /** Multiplies the visibility by {@code factor}. */
    public void modifyVisibility(double factor) {
        net.weaver.api.game.event.support.Finite.check(factor, "factor");
        visibility *= factor;
    }
}
