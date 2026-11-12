package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A loot table is about to generate items on the logical server.
 * Observation-only; the base payloads are game objects carried as {@link Object}.
 */
public class LootTableEvent extends Event {

    private final Object lootTable;
    private final Object params;

    /**
     * Creates a loot-table event.
     *
     * @param lootTable the loot table
     * @param params    the loot parameters
     */
    public LootTableEvent(Object lootTable, Object params) {
        this.lootTable = Objects.requireNonNull(lootTable, "lootTable");
        this.params = Objects.requireNonNull(params, "params");
    }

    /** The loot table. */
    public Object lootTable() {
        return lootTable;
    }

    /** The loot parameters. */
    public Object params() {
        return params;
    }
}
