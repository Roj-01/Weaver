package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A block entity is about to save its full metadata.
 *
 * <p>Fired at method entry to
 * {@code BlockEntity#saveWithFullMetadata(ValueOutput)V}, before the stored
 * data is written. Observation-only and not cancelable. The spec is installed
 * on the logical server only, but {@code BlockEntity} is a shared class: in a
 * process that hosts an integrated client, client-side saves can also reach the
 * injected body. The base class body is instrumented; a subclass override that
 * does not call {@code super} bypasses the hook.
 *
 * <p>The base payload is a game object carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.
 */
public class BlockEntitySaveEvent extends Event {

    private final Object blockEntity;

    /**
     * Creates a block-entity-save event.
     *
     * @param blockEntity the block entity saving its metadata
     */
    public BlockEntitySaveEvent(Object blockEntity) {
        this.blockEntity = Objects.requireNonNull(blockEntity, "blockEntity");
    }

    /** The block entity saving its metadata. */
    public Object blockEntity() {
        return blockEntity;
    }
}
