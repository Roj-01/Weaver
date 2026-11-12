package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Typed variant of {@link net.weaver.api.event.events.PlayerLogoutEvent}.
 *
 * <p>The payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class PlayerLogoutEvent extends net.weaver.api.event.events.PlayerLogoutEvent {

    /**
     * Creates a typed player-logout event.
     *
     * @param playerList the player list processing the logout
     * @param player     the logging-out player
     */
    public PlayerLogoutEvent(Object playerList, Object player) {
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
