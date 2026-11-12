package net.weaver.api.game.event.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a player clicks a slot while holding a stack on the cursor,
 * before items get a chance to handle it themselves (bundles, for example)
 * ({@code AbstractContainerMenu#tryItemClickBehaviourOverride}). Fires on
 * both logical sides. Cancelable: a canceled event marks the click as
 * handled, so vanilla does nothing more with it; the listener does the work.
 */
public final class ItemStackedOnOtherEvent extends Event {
    private final Player player;
    private final ItemStack carried;
    private final ItemStack stackedOn;
    private final Slot slot;
    private final ClickAction action;

    public ItemStackedOnOtherEvent(Player player, ItemStack carried, ItemStack stackedOn, Slot slot, ClickAction action) {
        super(true);
        this.player = player;
        this.carried = carried;
        this.stackedOn = stackedOn;
        this.slot = slot;
        this.action = action;
    }

    public Player player() {
        return player;
    }

    /** The stack on the cursor. */
    public ItemStack carried() {
        return carried;
    }

    /** The stack in the clicked slot. */
    public ItemStack stackedOn() {
        return stackedOn;
    }

    public Slot slot() {
        return slot;
    }

    /** {@code PRIMARY} for a left click, {@code SECONDARY} for a right click. */
    public ClickAction action() {
        return action;
    }
}
