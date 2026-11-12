package net.weaver.api.game.event.client;

import java.util.Objects;
import net.minecraft.client.player.LocalPlayer;
import net.weaver.api.event.Event;

/**
 * Fired on the client as the local player joins, respawns, or leaves a world
 * or server. Check {@link #kind()}:
 * <ul>
 *   <li>{@code LOGGED_IN}: the join packet was handled
 *       ({@code ClientPacketListener#handleLogin}); {@link #player()} is set.</li>
 *   <li>{@code RESPAWNED}: a new player object replaced the old one, after
 *       death or a dimension change ({@code ClientPacketListener#handleRespawn});
 *       {@link #oldPlayer()} and {@link #player()} are set.</li>
 *   <li>{@code LOGGED_OUT}: the client is leaving ({@code Minecraft#disconnect}
 *       and {@code Minecraft#clearClientLevel}); {@link #player()} may be
 *       {@code null} if the connection failed before joining.</li>
 * </ul>
 */
public final class ClientPlayerNetworkEvent extends Event {
    /** What happened. */
    public enum Kind {
        LOGGED_IN,
        RESPAWNED,
        LOGGED_OUT
    }

    private final Kind kind;
    private final LocalPlayer oldPlayer;
    private final LocalPlayer player;

    public ClientPlayerNetworkEvent(Kind kind, LocalPlayer oldPlayer, LocalPlayer player) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.oldPlayer = oldPlayer;
        this.player = player;
    }

    public Kind kind() {
        return kind;
    }

    /** The replaced player for {@code RESPAWNED}, otherwise {@code null}. */
    public LocalPlayer oldPlayer() {
        return oldPlayer;
    }

    public LocalPlayer player() {
        return player;
    }
}
