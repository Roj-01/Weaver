package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server player is about to use an item.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code ServerPlayerGameMode#useItem}. Observation-only and not cancelable.
 * The base payloads are game objects carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.
 */
public class ItemUseEvent extends Event {

    private final Object player;
    private final Object level;
    private final Object stack;
    private final Object hand;

    /**
     * Creates an item-use event.
     *
     * @param player the player using the item
     * @param level  the level the item is used in
     * @param stack  the item stack being used
     * @param hand   the hand holding the stack
     */
    public ItemUseEvent(Object player, Object level, Object stack, Object hand) {
        this.player = Objects.requireNonNull(player, "player");
        this.level = Objects.requireNonNull(level, "level");
        this.stack = Objects.requireNonNull(stack, "stack");
        this.hand = Objects.requireNonNull(hand, "hand");
    }

    /** The player using the item. */
    public Object player() {
        return player;
    }

    /** The level the item is used in. */
    public Object level() {
        return level;
    }

    /** The item stack being used. */
    public Object stack() {
        return stack;
    }

    /** The hand holding the stack. */
    public Object hand() {
        return hand;
    }
}
