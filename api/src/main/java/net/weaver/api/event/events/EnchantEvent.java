package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * An enchantment menu is about to apply an enchantment.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code EnchantmentMenu#clickMenuButton} applies an enchantment.
 * Observation-only and not cancelable. The base payloads are game objects
 * carried as {@link Object}; the typed {@code api-game} event exposes game
 * types.
 */
public class EnchantEvent extends Event {

    private final Object menu;
    private final Object player;

    /**
     * Creates an enchant event.
     *
     * @param menu   the enchantment menu applying the enchantment
     * @param player the player buying the enchantment
     */
    public EnchantEvent(Object menu, Object player) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.player = Objects.requireNonNull(player, "player");
    }

    /** The enchantment menu applying the enchantment. */
    public Object menu() {
        return menu;
    }

    /** The player buying the enchantment. */
    public Object player() {
        return player;
    }
}
