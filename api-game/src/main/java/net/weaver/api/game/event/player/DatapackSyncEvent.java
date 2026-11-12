package net.weaver.api.game.event.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.weaver.api.event.Event;

/**
 * Fired on the server when players need datapack-driven data: once for each
 * player after they join ({@code PlayerList#placeNewPlayer}, {@link #player()}
 * set), and once for everyone after {@code /reload}
 * ({@code PlayerList#reloadResources}, {@link #player()} is {@code null}).
 * Send mod data loaded from datapacks here.
 */
public final class DatapackSyncEvent extends Event {
    private final PlayerList playerList;
    private final ServerPlayer player;

    public DatapackSyncEvent(PlayerList playerList, ServerPlayer player) {
        this.playerList = playerList;
        this.player = player;
    }

    public PlayerList playerList() {
        return playerList;
    }

    /** The joining player, or {@code null} for a reload that affects every player. */
    public ServerPlayer player() {
        return player;
    }
}
