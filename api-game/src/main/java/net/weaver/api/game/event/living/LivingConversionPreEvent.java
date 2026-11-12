package net.weaver.api.game.event.living;

import net.minecraft.world.entity.ConversionType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.weaver.api.event.Event;

/**
 * Fired when a mob is about to turn into another mob ({@code Mob#convertTo}:
 * zombie to drowned, villager to witch, piglin zombification, slime splitting,
 * and so on). Cancelable: a canceled event keeps the original mob and returns
 * no new mob.
 */
public final class LivingConversionPreEvent extends Event {
    private final Mob mob;
    private final EntityType<?> outcome;
    private final ConversionType type;

    public LivingConversionPreEvent(Mob mob, EntityType<?> outcome, ConversionType type) {
        super(true);
        this.mob = mob;
        this.outcome = outcome;
        this.type = type;
    }

    public Mob mob() {
        return mob;
    }

    /** The type of mob it will become. */
    public EntityType<?> outcome() {
        return outcome;
    }

    /** Single conversion or split on death. */
    public ConversionType type() {
        return type;
    }
}
