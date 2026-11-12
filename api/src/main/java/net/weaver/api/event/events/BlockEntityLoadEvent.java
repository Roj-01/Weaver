package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A block entity is about to load its persisted components.
 *
 * <p>Fired at method entry to
 * {@code BlockEntity#loadWithComponents(ValueInput)V}, before the stored data
 * is applied. Observation-only and not cancelable. The spec is installed on the
 * logical server only, but {@code BlockEntity} is a shared class: in a process
 * that hosts an integrated client, client-side loads can also reach the
 * injected body. The base class body is instrumented; a subclass override that
 * does not call {@code super} bypasses the hook.
 *
 * <p>The base payload is a game object carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.
 */
public class BlockEntityLoadEvent extends Event {

    private final Object blockEntity;

    /**
     * Creates a block-entity-load event.
     *
     * @param blockEntity the block entity loading its components
     */
    public BlockEntityLoadEvent(Object blockEntity) {
        this.blockEntity = Objects.requireNonNull(blockEntity, "blockEntity");
    }

    /** The block entity loading its components. */
    public Object blockEntity() {
        return blockEntity;
    }
}
