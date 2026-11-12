package net.weaver.api.game.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Typed variant of {@link net.weaver.api.event.events.BlockBreakEvent}.
 *
 * <p>Fired at method entry to {@code ServerPlayerGameMode#destroyBlock}, before
 * the block is removed. The hook creates the cancelable form; canceling makes
 * the injected call return {@code false} and skips the block break for that
 * call. The payloads are narrowed to the game types. The inherited accessors
 * stay available with their {@link Object} descriptors for binary
 * compatibility.
 */
public final class BlockBreakEvent extends net.weaver.api.event.events.BlockBreakEvent {

    /**
     * Creates an observation-only typed block-break event.
     *
     * @param player the breaking player
     * @param pos    the block position being broken
     */
    public BlockBreakEvent(Object player, Object pos) {
        this(player, pos, false);
    }

    /**
     * Creates a typed block-break event with an explicit cancelable flag.
     *
     * @param player     the breaking player
     * @param pos        the block position being broken
     * @param cancelable whether listeners may cancel the block break for this
     *                   call
     */
    public BlockBreakEvent(Object player, Object pos, boolean cancelable) {
        super(player, pos, cancelable);
    }

    @Override
    public ServerPlayer player() {
        return (ServerPlayer) super.player();
    }

    @Override
    public BlockPos pos() {
        return (BlockPos) super.pos();
    }
}
