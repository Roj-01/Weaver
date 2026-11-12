package net.weaver.api.game.event.world;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.weaver.api.event.Event;

/**
 * Fired when a block loot table reads the tool's enchantment level for a bonus count
 * ({@code ApplyBonusCount}: Fortune on ores, crops, and similar). The level can be changed. Server side.
 */
public final class EnchantedBlockLootEvent extends Event {
    private final LootContext context;
    private final Holder<Enchantment> enchantment;
    private final ItemInstance tool;
    private int level;

    public EnchantedBlockLootEvent(LootContext context, Holder<Enchantment> enchantment, ItemInstance tool, int level) {
        super(false);
        this.context = context;
        this.enchantment = enchantment;
        this.tool = tool;
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

    /** The tool, possibly {@code null}. */
    public ItemInstance tool() {
        return tool;
    }

    /** The enchantment level used. */
    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(0, Math.min(255, level));
    }
}
