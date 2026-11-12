package net.weaver.api.game.event.player;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.event.Event;

/**
 * Fired after a player was granted an advancement criterion
 * ({@code PlayerAdvancements#award} returned {@code true}).
 * {@link #completed()} tells whether that criterion finished the advancement.
 */
public final class AdvancementProgressEvent extends Event {
    private final ServerPlayer player;
    private final AdvancementHolder advancement;
    private final String criterion;
    private final boolean completed;

    public AdvancementProgressEvent(ServerPlayer player, AdvancementHolder advancement,
                                    String criterion, boolean completed) {
        this.player = player;
        this.advancement = advancement;
        this.criterion = criterion;
        this.completed = completed;
    }

    public ServerPlayer player() {
        return player;
    }

    public AdvancementHolder advancement() {
        return advancement;
    }

    public String criterion() {
        return criterion;
    }

    /** Whether the advancement is now complete (earned). */
    public boolean completed() {
        return completed;
    }
}
