package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server player is about to use an item on a block.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code ServerPlayerGameMode#useItemOn}. Observation-only and not cancelable.
 * The base payloads are game objects carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.
 */
public class BlockInteractEvent extends Event {

    private final Object player;
    private final Object level;
    private final Object stack;
    private final Object hand;
    private final Object hitResult;

    /**
     * Creates a block-interaction event.
     *
     * @param player    the interacting player
     * @param level     the level the interaction happens in
     * @param stack     the item stack used for the interaction
     * @param hand      the hand holding the stack
     * @param hitResult the block hit result
     */
    public BlockInteractEvent(Object player, Object level, Object stack, Object hand,
                              Object hitResult) {
        this.player = Objects.requireNonNull(player, "player");
        this.level = Objects.requireNonNull(level, "level");
        this.stack = Objects.requireNonNull(stack, "stack");
        this.hand = Objects.requireNonNull(hand, "hand");
        this.hitResult = Objects.requireNonNull(hitResult, "hitResult");
    }

    /** The interacting player. */
    public Object player() {
        return player;
    }

    /** The level the interaction happens in. */
    public Object level() {
        return level;
    }

    /** The item stack used for the interaction. */
    public Object stack() {
        return stack;
    }

    /** The hand holding the stack. */
    public Object hand() {
        return hand;
    }

    /** The block hit result. */
    public Object hitResult() {
        return hitResult;
    }
}
