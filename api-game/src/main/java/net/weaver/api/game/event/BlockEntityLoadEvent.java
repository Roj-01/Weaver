package net.weaver.api.game.event;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Typed variant of {@link net.weaver.api.event.events.BlockEntityLoadEvent}.
 *
 * <p>Fired at method entry to
 * {@code BlockEntity#loadWithComponents(ValueInput)V}, before the stored data
 * is applied. Observation-only and not cancelable. The payload is narrowed to
 * the game type. The inherited accessor stays available with its
 * {@link Object} descriptor for binary compatibility.
 */
public final class BlockEntityLoadEvent
        extends net.weaver.api.event.events.BlockEntityLoadEvent {

    /**
     * Creates a typed block-entity-load event.
     *
     * @param blockEntity the block entity loading its components
     */
    public BlockEntityLoadEvent(Object blockEntity) {
        super(blockEntity);
    }

    @Override
    public BlockEntity blockEntity() {
        return (BlockEntity) super.blockEntity();
    }
}
