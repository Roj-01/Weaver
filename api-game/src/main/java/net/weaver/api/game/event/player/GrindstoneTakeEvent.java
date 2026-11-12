package net.weaver.api.game.event.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a player tries to take the result out of a grindstone (the
 * result slot's {@code mayPickup}), before the result leaves the slot.
 * Cancelable: a canceled event refuses the take, so the inputs stay and no
 * experience is awarded. It can fire more than once per take.
 */
public final class GrindstoneTakeEvent extends Event {
    private final Player player;
    private final GrindstoneMenu menu;
    private final ItemStack output;

    public GrindstoneTakeEvent(Player player, GrindstoneMenu menu, ItemStack output) {
        super(true);
        this.player = player;
        this.menu = menu;
        this.output = output;
    }

    public Player player() {
        return player;
    }

    /** The menu; its slots 0 and 1 hold the inputs. */
    public GrindstoneMenu menu() {
        return menu;
    }

    public ItemStack output() {
        return output;
    }

    public ItemStack top() {
        return menu.getSlot(0).getItem();
    }

    public ItemStack bottom() {
        return menu.getSlot(1).getItem();
    }
}
