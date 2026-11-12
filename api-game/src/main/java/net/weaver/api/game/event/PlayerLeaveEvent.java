package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Typed variant of {@link net.weaver.api.event.events.PlayerLeaveEvent}.
 *
 * <p>The payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class PlayerLeaveEvent extends net.weaver.api.event.events.PlayerLeaveEvent {

    /**
     * Creates a typed player-leave event.
     *
     * @param playerList the player list losing the player
     * @param player     the leaving player
     */
    public PlayerLeaveEvent(Object playerList, Object player) {
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
