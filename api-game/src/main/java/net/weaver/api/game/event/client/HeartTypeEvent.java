package net.weaver.api.game.event.client;

import java.util.Objects;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.Event;

/**
 * Fired on the client when the HUD picks which hearts to draw for the player
 * ({@code Hud.HeartType#forPlayer}). The heart type is a package-private
 * vanilla enum, so it is exchanged by constant name: {@code NORMAL},
 * {@code POISONED}, {@code WITHERED}, {@code ABSORBING}, {@code FROZEN}.
 * {@link #setType(String)} draws another kind; an unknown name keeps
 * vanilla's.
 */
public final class HeartTypeEvent extends Event {
    private final Player player;
    private final String originalType;
    private String type;

    public HeartTypeEvent(Player player, String type) {
        this.player = player;
        this.originalType = type;
        this.type = type;
    }

    public Player player() {
        return player;
    }

    public String originalType() {
        return originalType;
    }

    public String type() {
        return type;
    }

    public void setType(String type) {
        this.type = Objects.requireNonNull(type, "type");
    }
}
