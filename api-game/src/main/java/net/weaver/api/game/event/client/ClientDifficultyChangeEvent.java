package net.weaver.api.game.event.client;

import net.minecraft.world.Difficulty;
import net.weaver.api.event.Event;

/**
 * Fired on the client when the client's copy of the world difficulty is set
 * ({@code ClientLevel.ClientLevelData#setDifficulty}), on joining and when the
 * server changes it.
 */
public final class ClientDifficultyChangeEvent extends Event {
    private final Difficulty oldDifficulty;
    private final Difficulty newDifficulty;

    public ClientDifficultyChangeEvent(Difficulty oldDifficulty, Difficulty newDifficulty) {
        this.oldDifficulty = oldDifficulty;
        this.newDifficulty = newDifficulty;
    }

    public Difficulty oldDifficulty() {
        return oldDifficulty;
    }

    public Difficulty newDifficulty() {
        return newDifficulty;
    }
}
