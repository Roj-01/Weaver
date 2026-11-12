package net.weaver.api.game.loot;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

/**
 * One change to generated loot, read from a data pack file under
 * {@code data/<namespace>/weaver/loot_modifiers/}. Weaver checks the file's
 * {@code tables} and {@code conditions} before calling {@link #apply}.
 *
 * <p>Mods add their own kinds with {@link LootModifiers#registerType}.</p>
 */
@FunctionalInterface
public interface LootModifier {
    /**
     * Changes {@code loot} in place: add, remove, or replace stacks.
     *
     * @param loot    the stacks the table generated, after earlier modifiers
     * @param context the loot context the table was rolled with
     */
    void apply(List<ItemStack> loot, LootContext context);
}
