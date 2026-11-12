package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A projectile is about to be launched with an explicit velocity.
 *
 * <p>Fired at method entry to {@code Projectile#shoot(DDDFF)V}, before the
 * launch velocity is applied. Observation-only and not cancelable. The
 * injected call can pass only reference parameters, so the event exposes the
 * projectile but not the three doubles and two floats. Read the projectile's
 * position and delta movement when the numeric launch data is needed.
 *
 * <p>The spec is installed on the logical server only, but {@code Projectile}
 * is a shared class: in a process that hosts an integrated client, client-side
 * invocations can also reach the injected body. The spec instruments the base
 * class body only; a subclass override that does not call {@code super}
 * bypasses the hook.
 *
 * <p>The base payload is a game object carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.
 */
public class ProjectileLaunchEvent extends Event {

    private final Object projectile;

    /**
     * Creates a projectile-launch event.
     *
     * @param projectile the projectile being launched
     */
    public ProjectileLaunchEvent(Object projectile) {
        this.projectile = Objects.requireNonNull(projectile, "projectile");
    }

    /** The projectile being launched. */
    public Object projectile() {
        return projectile;
    }
}
