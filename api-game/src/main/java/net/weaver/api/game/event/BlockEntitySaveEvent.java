package net.weaver.api.game.event;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Typed variant of {@link net.weaver.api.event.events.BlockEntitySaveEvent}.
 *
 * <p>Fired at method entry to
 * {@code BlockEntity#saveWithFullMetadata(ValueOutput)V}, before the stored
 * data is written. Observation-only and not cancelable. The payload is
 * narrowed to the game type. The inherited accessor stays available with its
 * {@link Object} descriptor for binary compatibility.
 */
public final class BlockEntitySaveEvent
        extends net.weaver.api.event.events.BlockEntitySaveEvent {

    /**
     * Creates a typed block-entity-save event.
     *
     * @param blockEntity the block entity saving its metadata
     */
    public BlockEntitySaveEvent(Object blockEntity) {
        super(blockEntity);
    }

    @Override
    public BlockEntity blockEntity() {
        return (BlockEntity) super.blockEntity();
    }
}
