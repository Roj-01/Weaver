package net.weaver.api.game.event.player;

import java.util.Objects;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a grindstone's inputs change and it computes its result
 * ({@code GrindstoneMenu#createResult}). {@link #result()} starts as vanilla's
 * result (possibly empty); set another stack to offer a different one.
 * Cancelable: a canceled event shows no result. Fires on both logical sides;
 * return the same result on both.
 */
public final class GrindstoneResultEvent extends Event {
    private final GrindstoneMenu menu;
    private final ItemStack top;
    private final ItemStack bottom;
    private ItemStack result;

    public GrindstoneResultEvent(GrindstoneMenu menu, ItemStack top, ItemStack bottom, ItemStack result) {
        super(true);
        this.menu = menu;
        this.top = top;
        this.bottom = bottom;
        this.result = result;
    }

    public GrindstoneMenu menu() {
        return menu;
    }

    public ItemStack top() {
        return top;
    }

    public ItemStack bottom() {
        return bottom;
    }

    public ItemStack result() {
        return result;
    }

    public void setResult(ItemStack result) {
        this.result = Objects.requireNonNull(result, "result");
    }
}
