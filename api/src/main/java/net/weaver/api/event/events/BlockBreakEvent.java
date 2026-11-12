package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server player is about to break a block.
 *
 * <p>Fired at method entry to {@code ServerPlayerGameMode#destroyBlock}, before
 * the block is removed. The hook creates the cancelable form; canceling the
 * event makes the injected call return {@code false} and skips the block break
 * for that call. The player payload is read from the game mode's {@code player}
 * field. The spec is installed on the logical server only.
 *
 * <p>The two-argument constructor is observation-only. The base payloads are
 * game objects carried as {@link Object}; the typed {@code api-game} event
 * exposes game types.
 */
public class BlockBreakEvent extends Event {

    private final Object player;
    private final Object pos;

    /**
     * Creates an observation-only block-break event.
     *
     * @param player the breaking player
     * @param pos    the block position being broken
     */
    public BlockBreakEvent(Object player, Object pos) {
        this(player, pos, false);
    }

    /**
     * Creates a block-break event with an explicit cancelable flag.
     *
     * <p>The hook uses the cancelable form for the
     * {@code ServerPlayerGameMode#destroyBlock} injection point.
     *
     * @param player     the breaking player
     * @param pos        the block position being broken
     * @param cancelable whether listeners may cancel the block break for this
     *                   call
     */
    public BlockBreakEvent(Object player, Object pos, boolean cancelable) {
        super(cancelable);
        this.player = Objects.requireNonNull(player, "player");
        this.pos = Objects.requireNonNull(pos, "pos");
    }

    /** The breaking player. */
    public Object player() {
        return player;
    }

    /** The block position being broken. */
    public Object pos() {
        return pos;
    }
}
