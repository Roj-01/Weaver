package net.weaver.api.game.event.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.Event;

/**
 * Fired when a living entity picks the ammunition for a projectile weapon ({@code getProjectile} of
 * {@code LivingEntity}, {@code Player}, and {@code Monster}): arrows for bows and crossbows, and any
 * modded weapon that asks. The projectile stack can be replaced; {@link ItemStack#EMPTY} means none.
 */
public final class LivingGetProjectileEvent extends Event {
    private final LivingEntity entity;
    private final ItemStack weapon;
    private ItemStack projectile;

    public LivingGetProjectileEvent(LivingEntity entity, ItemStack weapon, ItemStack projectile) {
        super(false);
        this.entity = entity;
        this.weapon = weapon;
        this.projectile = projectile;
    }

    /** The shooter. */
    public LivingEntity entity() {
        return entity;
    }

    /** The weapon. */
    public ItemStack weapon() {
        return weapon;
    }

    /** The ammunition chosen. */
    public ItemStack projectile() {
        return projectile;
    }

    public void setProjectile(ItemStack projectile) {
        this.projectile = projectile;
    }
}
