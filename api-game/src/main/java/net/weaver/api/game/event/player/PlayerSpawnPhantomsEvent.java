package net.weaver.api.game.event.player;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.event.Event;

/**
 * Fired when the phantom spawner considers a player
 * ({@code PhantomSpawner#tick}), roughly every one to two minutes at night or
 * in dimensions without skylight. {@link Decision#DENY} spawns no phantoms for
 * this player; {@link Decision#ALLOW} skips the difficulty and
 * time-since-rest rolls (in the overworld the player must still be under open
 * sky, and the spawn spot must be free). {@link #setPhantomCount(int)}
 * overrides the group size.
 */
public final class PlayerSpawnPhantomsEvent extends Event {
    /** Whether phantoms may spawn. */
    public enum Decision {
        DEFAULT,
        ALLOW,
        DENY
    }

    private final ServerPlayer player;
    private Decision decision = Decision.DEFAULT;
    private int phantomCount = -1;

    public PlayerSpawnPhantomsEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer player() {
        return player;
    }

    public Decision decision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = Objects.requireNonNull(decision, "decision");
    }

    /** The group size, or {@code -1} for vanilla's random size. */
    public int phantomCount() {
        return phantomCount;
    }

    public void setPhantomCount(int phantomCount) {
        this.phantomCount = phantomCount < 0 ? -1 : phantomCount;
    }
}
