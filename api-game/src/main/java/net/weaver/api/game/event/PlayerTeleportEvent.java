package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;

/**
 * Typed variant of {@link net.weaver.api.event.events.PlayerTeleportEvent}.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code ServerPlayer#teleport}. The teleport may be same-dimension or
 * cross-dimension, so listeners compare the source and destination levels from
 * the transition. Observation-only and not cancelable. The payloads are
 * narrowed to the game types. The inherited accessors stay available with their
 * {@link Object} descriptors for binary compatibility.
 */
public final class PlayerTeleportEvent extends net.weaver.api.event.events.PlayerTeleportEvent {

    /**
     * Creates a typed player-teleport event.
     *
     * @param player     the teleporting player
     * @param transition the transition describing the teleport
     */
    public PlayerTeleportEvent(Object player, Object transition) {
        super(player, transition);
    }

    @Override
    public ServerPlayer player() {
        return (ServerPlayer) super.player();
    }

    @Override
    public TeleportTransition transition() {
        return (TeleportTransition) super.transition();
    }
}
