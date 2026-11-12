package net.weaver.api.game.event.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.weaver.api.event.Event;

/**
 * Fired on the server when an enchanting table computes the experience level
 * of one of its three offers ({@code EnchantmentMenu#slotsChanged}). Set
 * {@link #setEnchantLevel(int)} to change it. Vanilla then clears an offer
 * whose level is below its row number plus one, so a value that low hides
 * the offer.
 */
public final class EnchantmentLevelSetEvent extends Event {
    private final Level level;
    private final BlockPos pos;
    private final int row;
    private final int power;
    private final ItemStack item;
    private final int originalLevel;
    private int enchantLevel;

    public EnchantmentLevelSetEvent(Level level, BlockPos pos, int row, int power, ItemStack item, int enchantLevel) {
        this.level = level;
        this.pos = pos;
        this.row = row;
        this.power = power;
        this.item = item;
        this.originalLevel = enchantLevel;
        this.enchantLevel = enchantLevel;
    }

    public Level level() {
        return level;
    }

    /** The enchanting table position. */
    public BlockPos pos() {
        return pos;
    }

    /** The offer row, 0 to 2. */
    public int row() {
        return row;
    }

    /** The number of bookshelves counted, capped at 15. */
    public int power() {
        return power;
    }

    public ItemStack item() {
        return item;
    }

    public int originalLevel() {
        return originalLevel;
    }

    public int enchantLevel() {
        return enchantLevel;
    }

    public void setEnchantLevel(int enchantLevel) {
        this.enchantLevel = enchantLevel;
    }
}
