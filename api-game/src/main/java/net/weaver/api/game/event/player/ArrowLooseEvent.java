package net.weaver.api.game.event.player;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a bow is released and is about to shoot ({@code BowItem#releaseUsing}).
 * Cancelable: a canceled event shoots nothing. The charge (ticks drawn) can be
 * changed; vanilla reaches full power at 20.
 */
public final class ArrowLooseEvent extends Event {
    private final LivingEntity shooter;
    private final ItemStack bow;
    private int charge;

    public ArrowLooseEvent(LivingEntity shooter, ItemStack bow, int charge) {
        super(true);
        this.shooter = shooter;
        this.bow = bow;
        this.charge = charge;
    }

    public LivingEntity shooter() {
        return shooter;
    }

    public ItemStack bow() {
        return bow;
    }

    public int charge() {
        return charge;
    }

    public void setCharge(int charge) {
        this.charge = charge;
    }
}
