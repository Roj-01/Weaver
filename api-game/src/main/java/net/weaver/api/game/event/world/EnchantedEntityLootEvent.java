package net.weaver.api.game.event.world;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.weaver.api.event.Event;

/**
 * Fired when an entity loot table reads the attacker's enchantment level for extra drops
 * ({@code EnchantedCountIncreaseFunction}: Looting). The level can be changed. Server side.
 */
public final class EnchantedEntityLootEvent extends Event {
    private final LootContext context;
    private final Holder<Enchantment> enchantment;
    private final LivingEntity attacker;
    private int level;

    public EnchantedEntityLootEvent(LootContext context, Holder<Enchantment> enchantment, LivingEntity attacker, int level) {
        super(false);
        this.context = context;
        this.enchantment = enchantment;
        this.attacker = attacker;
        this.level = level;
    }

    /** The loot roll. */
    public LootContext context() {
        return context;
    }

    /** The enchantment read. */
    public Holder<Enchantment> enchantment() {
        return enchantment;
    }

    /** The attacker. */
    public LivingEntity attacker() {
        return attacker;
    }

    /** The enchantment level used. */
    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(0, Math.min(255, level));
    }
}
