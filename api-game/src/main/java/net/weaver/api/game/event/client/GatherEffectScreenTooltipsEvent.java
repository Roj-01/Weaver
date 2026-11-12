package net.weaver.api.game.event.client;

import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.weaver.api.event.Event;

/**
 * Fired on the client while the mouse is over an active effect drawn beside an
 * inventory screen ({@code EffectsInInventory}). The tooltip starts with the
 * effect name and remaining time; add, change, or remove lines. A tooltip is
 * only drawn while this event has a listener and the list is not empty, so
 * vanilla screens look unchanged without mods.
 */
public final class GatherEffectScreenTooltipsEvent extends Event {
    private final AbstractContainerScreen<?> screen;
    private final MobEffectInstance effect;
    private final List<Component> tooltip;

    public GatherEffectScreenTooltipsEvent(AbstractContainerScreen<?> screen, MobEffectInstance effect, List<Component> tooltip) {
        this.screen = screen;
        this.effect = effect;
        this.tooltip = tooltip;
    }

    public AbstractContainerScreen<?> screen() {
        return screen;
    }

    public MobEffectInstance effect() {
        return effect;
    }

    /** The tooltip lines (mutable). */
    public List<Component> tooltip() {
        return tooltip;
    }
}
