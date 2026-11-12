package net.weaver.api.game.event;

import net.minecraft.world.entity.projectile.Projectile;

/**
 * Typed variant of {@link net.weaver.api.event.events.ProjectileLaunchEvent}.
 *
 * <p>Fired at method entry to {@code Projectile#shoot(DDDFF)V}, before the
 * launch velocity is applied. The injected call can pass only reference
 * parameters, so the event exposes the projectile but not the numeric launch
 * coordinates. Observation-only and not cancelable. The payload is narrowed to
 * the game type. The inherited accessor stays available with its {@link Object}
 * descriptor for binary compatibility.
 */
public final class ProjectileLaunchEvent
        extends net.weaver.api.event.events.ProjectileLaunchEvent {

    /**
     * Creates a typed projectile-launch event.
     *
     * @param projectile the projectile being launched
     */
    public ProjectileLaunchEvent(Object projectile) {
        super(projectile);
    }

    @Override
    public Projectile projectile() {
        return (Projectile) super.projectile();
    }
}
