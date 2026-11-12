package net.weaver.api.game.event;

import net.minecraft.world.entity.LivingEntity;

/**
 * Typed variant of {@link net.weaver.api.event.events.ItemUseFinishEvent}.
 *
 * <p>Fired at method entry to {@code LivingEntity#completeUsingItem}, before
 * the item-use completion is applied. Observation-only and not cancelable.
 * The payload is narrowed to the game type. The inherited accessor stays
 * available with its {@link Object} descriptor for binary compatibility.
 */
public final class ItemUseFinishEvent extends net.weaver.api.event.events.ItemUseFinishEvent {

    /**
     * Creates a typed item-use-finish event.
     *
     * @param livingEntity the living entity finishing the item use
     */
    public ItemUseFinishEvent(Object livingEntity) {
        super(livingEntity);
    }

    @Override
    public LivingEntity livingEntity() {
        return (LivingEntity) super.livingEntity();
    }
}
