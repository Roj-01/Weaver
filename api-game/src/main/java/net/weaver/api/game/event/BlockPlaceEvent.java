package net.weaver.api.game.event;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;

/**
 * Typed variant of {@link net.weaver.api.event.events.BlockPlaceEvent}.
 *
 * <p>Fired at method entry to {@code BlockItem#place}, before the placement is
 * resolved. Result-based and not cancelable: a non-null result set with
 * {@link #setResultOverride(Object)} replaces the method result and skips the
 * vanilla body. The payloads are narrowed to the game types. The inherited
 * accessors stay available with their {@link Object} descriptors for binary
 * compatibility.
 */
public final class BlockPlaceEvent extends net.weaver.api.event.events.BlockPlaceEvent {

    /**
     * Creates a typed block-place event.
     *
     * @param placeContext the placement context
     */
    public BlockPlaceEvent(Object placeContext) {
        super(placeContext);
    }

    @Override
    public BlockPlaceContext placeContext() {
        return (BlockPlaceContext) super.placeContext();
    }

    /**
     * The replacement interaction result chosen by a listener.
     *
     * @return the replacement result, or {@code null} to let vanilla run
     */
    @Override
    public InteractionResult resultOverride() {
        return (InteractionResult) super.resultOverride();
    }
}
