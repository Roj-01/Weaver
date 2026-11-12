package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A living entity is about to release a used item.
 *
 * <p>Fired at method entry to {@code LivingEntity#releaseUsingItem}, before
 * the item release is applied. Observation-only and not cancelable. The spec
 * is installed on the logical server only, but {@code LivingEntity} is a
 * shared class: in a process that hosts an integrated client, client-side
 * invocations can also reach the injected body. The spec instruments the base
 * class body only; a subclass override that does not call {@code super}
 * bypasses the hook.
 *
 * <p>The base payload is a game object carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.
 */
public class ItemReleaseEvent extends Event {

    private final Object livingEntity;

    /**
     * Creates an item-release event.
     *
     * @param livingEntity the living entity releasing the item
     */
    public ItemReleaseEvent(Object livingEntity) {
        this.livingEntity = Objects.requireNonNull(livingEntity, "livingEntity");
    }

    /** The living entity releasing the item. */
    public Object livingEntity() {
        return livingEntity;
    }
}
