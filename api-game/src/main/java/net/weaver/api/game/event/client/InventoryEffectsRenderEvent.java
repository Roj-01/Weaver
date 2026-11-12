package net.weaver.api.game.event.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.weaver.api.event.Event;

/**
 * Fired on the client when an inventory screen is about to draw the player's
 * active effects beside the inventory ({@code EffectsInInventory#extractRenderState}).
 * Cancelable: a canceled event draws no effects, so a mod can draw its own.
 */
public final class InventoryEffectsRenderEvent extends Event {
    private final AbstractContainerScreen<?> screen;

    public InventoryEffectsRenderEvent(AbstractContainerScreen<?> screen) {
        super(true);
        this.screen = screen;
    }

    public AbstractContainerScreen<?> screen() {
        return screen;
    }
}
