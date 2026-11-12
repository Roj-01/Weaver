package net.weaver.api.game.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;

/**
 * Typed variant of {@link net.weaver.api.event.events.EnchantEvent}.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code EnchantmentMenu#clickMenuButton} applies an enchantment.
 * Observation-only and not cancelable. The payloads are narrowed to the game
 * types. The inherited accessors stay available with their {@link Object}
 * descriptors for binary compatibility.
 */
public final class EnchantEvent extends net.weaver.api.event.events.EnchantEvent {

    /**
     * Creates a typed enchant event.
     *
     * @param menu   the enchantment menu applying the enchantment
     * @param player the player buying the enchantment
     */
    public EnchantEvent(Object menu, Object player) {
        super(menu, player);
    }

    @Override
    public EnchantmentMenu menu() {
        return (EnchantmentMenu) super.menu();
    }

    @Override
    public Player player() {
        return (Player) super.player();
    }
}
