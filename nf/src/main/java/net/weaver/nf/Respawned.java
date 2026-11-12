package net.weaver.nf;

import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.event.EventBus;
import net.weaver.api.game.event.player.PlayerRespawnedEvent;

/**
 * A player respawned, with the player that plays on and whether the player came
 * back from the end, as NeoForge's {@code PlayerEvent.PlayerRespawnEvent}. Backed
 * by Weaver's {@code PlayerRespawnedEvent}.
 */
public final class Respawned {
    private Respawned() {
    }

    public static void listen(BiConsumer<ServerPlayer, Boolean> listener) {
        EventBus.GAME.addListener(PlayerRespawnedEvent.class, event -> listener.accept(event.player(), event.endConquered()));
    }

    /** Kept for code written against the copies; Weaver fires the event itself. */
    public static void fire(ServerPlayer player, boolean endConquered) {
    }
}
