package net.weaver.api.game.event.player;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a player starts drawing a bow ({@code BowItem#use}). Cancelable:
 * a canceled event does not draw the bow.
 */
public final class ArrowNockEvent extends Event {
    private final Player player;
    private final ItemStack bow;
    private final InteractionHand hand;

    public ArrowNockEvent(Player player, ItemStack bow, InteractionHand hand) {
        super(true);
        this.player = player;
        this.bow = bow;
        this.hand = hand;
    }

    public Player player() {
        return player;
    }

    public ItemStack bow() {
        return bow;
    }

    public InteractionHand hand() {
        return hand;
    }
}
