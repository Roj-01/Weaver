package net.weaver.api.game.event.player;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.event.Event;

/**
 * Fired when the server reads the name it shows for a player in the tab list
 * ({@code ServerPlayer#getTabListDisplayName}). A {@code null} name means the
 * client shows the regular display name. The server sends this name when the
 * player joins and on player-info updates.
 */
public final class PlayerTabListNameFormatEvent extends Event {
    private final ServerPlayer player;
    private Component displayName;

    public PlayerTabListNameFormatEvent(ServerPlayer player, Component displayName) {
        this.player = player;
        this.displayName = displayName;
    }

    public ServerPlayer player() {
        return player;
    }

    /** The tab-list name, or {@code null} for the regular display name. */
    public Component displayName() {
        return displayName;
    }

    public void setDisplayName(Component displayName) {
        this.displayName = displayName;
    }
}
