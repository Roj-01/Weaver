package net.weaver.api.game.event.client;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;
import net.weaver.api.event.Event;

/**
 * Fired on the client when the server reports that a player in the tab list,
 * the local player included, changed game mode ({@code PlayerInfo#setGameMode}).
 */
public final class ClientGameModeChangeEvent extends Event {
    private final PlayerInfo info;
    private final GameType oldGameMode;
    private final GameType newGameMode;

    public ClientGameModeChangeEvent(PlayerInfo info, GameType oldGameMode, GameType newGameMode) {
        this.info = info;
        this.oldGameMode = oldGameMode;
        this.newGameMode = newGameMode;
    }

    /** The player's tab-list entry; {@code info().getProfile()} identifies them. */
    public PlayerInfo info() {
        return info;
    }

    public GameType oldGameMode() {
        return oldGameMode;
    }

    public GameType newGameMode() {
        return newGameMode;
    }
}
