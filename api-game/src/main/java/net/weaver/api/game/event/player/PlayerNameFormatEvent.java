package net.weaver.api.game.event.player;

import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired when the game builds a player's display name
 * ({@code Player#getDisplayName}), before the team prefix, suffix, and color
 * are applied. Set {@link #setDisplayName(Component)} to show a different
 * name in chat, death messages, and other places that use the display name.
 * Vanilla does not cache the display name, so this fires on every call.
 */
public final class PlayerNameFormatEvent extends Event {
    private final Player player;
    private final Component username;
    private Component displayName;

    public PlayerNameFormatEvent(Player player, Component username) {
        this.player = player;
        this.username = username;
        this.displayName = username;
    }

    public Player player() {
        return player;
    }

    /** The account name, unchanged. */
    public Component username() {
        return username;
    }

    public Component displayName() {
        return displayName;
    }

    public void setDisplayName(Component displayName) {
        this.displayName = Objects.requireNonNull(displayName, "displayName");
    }
}
