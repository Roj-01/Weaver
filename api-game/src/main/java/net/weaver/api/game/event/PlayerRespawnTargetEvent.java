package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;

/**
 * Typed variant of
 * {@link net.weaver.api.event.events.PlayerRespawnTargetEvent}.
 *
 * <p>Fired on the server thread at method entry to
 * {@code ServerPlayer#findRespawnPositionAndUseSpawnBlock}. This is the
 * result-based RETURN_VALUE pilot: setting a non-null target replaces the
 * vanilla lookup result and skips the method body, while leaving the target
 * {@code null} (or clearing it) lets vanilla run. The event is not cancelable.
 * The payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class PlayerRespawnTargetEvent
        extends net.weaver.api.event.events.PlayerRespawnTargetEvent {

    /**
     * Creates a typed respawn-target event.
     *
     * @param player       the respawning player
     * @param postTeleport the post-teleport transition supplied to the lookup,
     *                     possibly {@code null}
     */
    public PlayerRespawnTargetEvent(Object player, Object postTeleport) {
        super(player, postTeleport);
    }

    @Override
    public ServerPlayer player() {
        return (ServerPlayer) super.player();
    }

    /**
     * The post-teleport transition supplied to the vanilla lookup.
     *
     * @return the transition, or {@code null} when the caller supplied none
     */
    @Override
    public TeleportTransition postTeleport() {
        return (TeleportTransition) super.postTeleport();
    }

    /**
     * The replacement respawn target chosen by a listener.
     *
     * @return the replacement transition, or {@code null} when the vanilla
     *         lookup should run
     */
    @Override
    public TeleportTransition respawnTarget() {
        return (TeleportTransition) super.respawnTarget();
    }
}
