package net.weaver.api.game.event.living;

import net.minecraft.world.entity.Mob;
import net.weaver.api.event.Event;

/**
 * Fired after a mob turned into another mob ({@code Mob#convertTo} returned the
 * new mob).
 */
public final class LivingConversionPostEvent extends Event {
    private final Mob original;
    private final Mob outcome;

    public LivingConversionPostEvent(Mob original, Mob outcome) {
        this.original = original;
        this.outcome = outcome;
    }

    public Mob original() {
        return original;
    }

    public Mob outcome() {
        return outcome;
    }
}
