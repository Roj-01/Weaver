package net.weaver.api.game.event.player;

import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.weaver.api.event.Event;

/**
 * Fired after a player completed a trade with a villager or wandering trader
 * ({@code AbstractVillager#notifyTrade}): the offer's use count went up and
 * trade experience was rewarded. Not cancelable.
 */
public final class TradeWithVillagerEvent extends Event {
    private final Player player;
    private final MerchantOffer offer;
    private final AbstractVillager villager;

    public TradeWithVillagerEvent(Player player, MerchantOffer offer, AbstractVillager villager) {
        this.player = player;
        this.offer = offer;
        this.villager = villager;
    }

    /** The trading player, or {@code null} if the trade had none. */
    public Player player() {
        return player;
    }

    public MerchantOffer offer() {
        return offer;
    }

    public AbstractVillager villager() {
        return villager;
    }
}
