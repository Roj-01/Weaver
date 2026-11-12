package net.weaver.api.game.event.world;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired when a player is about to tame an animal ({@code TamableAnimal#tame}
 * for wolves, cats, parrots, and similar, and {@code AbstractHorse#tameWithName}
 * for horses, donkeys, mules, llamas, and camels). Cancelable: a canceled event
 * leaves the animal untamed.
 */
public final class AnimalTameEvent extends Event {
    private final Mob animal;
    private final Player tamer;

    public AnimalTameEvent(Mob animal, Player tamer) {
        super(true);
        this.animal = animal;
        this.tamer = tamer;
    }

    public Mob animal() {
        return animal;
    }

    public Player tamer() {
        return tamer;
    }
}
