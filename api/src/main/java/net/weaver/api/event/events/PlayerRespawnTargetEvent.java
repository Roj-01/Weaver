package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server player's respawn position lookup can be replaced by listeners.
 *
 * <p>Fired on the server thread at method entry to
 * {@code ServerPlayer#findRespawnPositionAndUseSpawnBlock}. This is the
 * result-based RETURN_VALUE pilot: setting a non-null target with
 * {@link #setRespawnTarget(Object)} replaces the vanilla lookup result and skips
 * the method body, while leaving the target {@code null} (or clearing it) lets
 * vanilla run. The event exposes a tri-state result but is <strong>not
 * cancelable</strong>. The base payloads are game objects carried as
 * {@link Object}; the typed {@code api-game} event exposes game types.
 */
public class PlayerRespawnTargetEvent extends Event {

    private final Object player;
    private final Object postTeleport;
    private volatile Object respawnTarget;

    /**
     * Creates a respawn-target event.
     *
     * @param player       the respawning player
     * @param postTeleport the post-teleport transition supplied to the lookup,
     *                     possibly {@code null}
     */
    public PlayerRespawnTargetEvent(Object player, Object postTeleport) {
        super(false, true);
        this.player = Objects.requireNonNull(player, "player");
        this.postTeleport = postTeleport;
    }

    /** The respawning player. */
    public Object player() {
        return player;
    }

    /**
     * The post-teleport transition supplied to the vanilla lookup.
     *
     * @return the transition, or {@code null} when the caller supplied none
     */
    public Object postTeleport() {
        return postTeleport;
    }

    /**
     * The replacement respawn target chosen by a listener.
     *
     * @return the replacement transition, or {@code null} when the vanilla
     *         lookup should run
     */
    public Object respawnTarget() {
        return respawnTarget;
    }

    /**
     * Replaces the vanilla respawn-position lookup result.
     *
     * <p>Setting a non-null target makes the hook skip the vanilla method body
     * and return the target directly. Setting {@code null} clears any previous
     * replacement and lets the vanilla lookup run.
     *
     * @param target the replacement transition, or {@code null} to let vanilla
     *               run
     */
    public void setRespawnTarget(Object target) {
        this.respawnTarget = target;
    }
}
