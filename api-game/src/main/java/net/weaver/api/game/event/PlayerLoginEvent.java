package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Typed variant of {@link net.weaver.api.event.events.PlayerLoginEvent}.
 *
 * <p>The payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class PlayerLoginEvent extends net.weaver.api.event.events.PlayerLoginEvent {

    /**
     * Creates a typed player-login event.
     *
     * @param playerList the player list accepting the login
     * @param player     the logging-in player
     */
    public PlayerLoginEvent(Object playerList, Object player) {
        super(playerList, player);
    }

    @Override
    public PlayerList playerList() {
        return (PlayerList) super.playerList();
    }

    @Override
    public ServerPlayer player() {
        return (ServerPlayer) super.player();
    }
}
