package net.weaver.api.game.event;

import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Typed variant of {@link net.weaver.api.event.events.LootTableEvent}.
 *
 * <p>The payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class LootTableEvent extends net.weaver.api.event.events.LootTableEvent {

    /**
     * Creates a typed loot-table event.
     *
     * @param lootTable the loot table
     * @param params    the loot parameters
     */
    public LootTableEvent(Object lootTable, Object params) {
        super(lootTable, params);
    }

    @Override
    public LootTable lootTable() {
        return (LootTable) super.lootTable();
    }

    @Override
    public LootParams params() {
        return (LootParams) super.params();
    }
}
