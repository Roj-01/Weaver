package net.weaver.api.game.event.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.weaver.api.event.Event;

/**
 * Fired when a player is about to change game mode
 * ({@code ServerPlayer#setGameMode}). Cancelable: a canceled event keeps the
 * current mode. The new mode can be changed.
 */
public final class PlayerChangeGameModeEvent extends Event {
    private final ServerPlayer player;
    private final GameType currentMode;
    private GameType newMode;

    public PlayerChangeGameModeEvent(ServerPlayer player, GameType currentMode, GameType newMode) {
        super(true);
        this.player = player;
        this.currentMode = currentMode;
        this.newMode = newMode;
    }

    public ServerPlayer player() {
        return player;
    }

    public GameType currentMode() {
        return currentMode;
    }

    public GameType newMode() {
        return newMode;
    }

    public void setNewMode(GameType newMode) {
        this.newMode = java.util.Objects.requireNonNull(newMode, "newMode");
    }
}
