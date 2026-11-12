package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Typed variant of {@link net.weaver.api.event.events.PlayerJoinEvent}.
 *
 * <p>The payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class PlayerJoinEvent extends net.weaver.api.event.events.PlayerJoinEvent {

    /**
     * Creates a typed player-join event.
     *
     * @param playerList the player list receiving the player
     * @param player     the joining player
     */
    public PlayerJoinEvent(Object playerList, Object player) {
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
