package net.weaver.api.game.event;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Typed variant of {@link net.weaver.api.event.events.EntityInteractEvent}.
 *
 * <p>Fired at method entry to {@code Player#interactOn}, before the
 * interaction is resolved. Observation-only and not cancelable. The payloads
 * are narrowed to the game types. The inherited accessors stay available with
 * their {@link Object} descriptors for binary compatibility.
 */
public final class EntityInteractEvent extends net.weaver.api.event.events.EntityInteractEvent {

    /**
     * Creates a typed entity-interaction event.
     *
     * @param player the interacting player
     * @param entity the entity being interacted with
     * @param hand   the hand used for the interaction
     * @param hitVec the interaction position
     */
    public EntityInteractEvent(Object player, Object entity, Object hand, Object hitVec) {
        super(player, entity, hand, hitVec);
    }

    @Override
    public Player player() {
        return (Player) super.player();
    }

    @Override
    public Entity entity() {
        return (Entity) super.entity();
    }

    @Override
    public InteractionHand hand() {
        return (InteractionHand) super.hand();
    }

    @Override
    public Vec3 hitVec() {
        return (Vec3) super.hitVec();
    }
}
