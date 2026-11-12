package net.weaver.api.game.event;

import net.minecraft.world.entity.LivingEntity;

/**
 * Typed variant of {@link net.weaver.api.event.events.ItemReleaseEvent}.
 *
 * <p>Fired at method entry to {@code LivingEntity#releaseUsingItem}, before
 * the item release is applied. Observation-only and not cancelable. The
 * payload is narrowed to the game type. The inherited accessor stays available
 * with its {@link Object} descriptor for binary compatibility.
 */
public final class ItemReleaseEvent extends net.weaver.api.event.events.ItemReleaseEvent {

    /**
     * Creates a typed item-release event.
     *
     * @param livingEntity the living entity releasing the item
     */
    public ItemReleaseEvent(Object livingEntity) {
        super(livingEntity);
    }

    @Override
    public LivingEntity livingEntity() {
        return (LivingEntity) super.livingEntity();
    }
}
