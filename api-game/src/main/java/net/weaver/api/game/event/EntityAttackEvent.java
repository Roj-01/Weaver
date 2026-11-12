package net.weaver.api.game.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Typed variant of {@link net.weaver.api.event.events.EntityAttackEvent}.
 *
 * <p>Fired at method entry to {@code Player#attack}, before the attack is
 * resolved. The hook creates the cancelable form; canceling makes
 * {@code Player#attack} return immediately and skips the attack body for that
 * call. The payloads are narrowed to the game types. The inherited accessors
 * stay available with their {@link Object} descriptors for binary
 * compatibility.
 */
public final class EntityAttackEvent extends net.weaver.api.event.events.EntityAttackEvent {

    /**
     * Creates an observation-only typed entity-attack event.
     *
     * @param player the attacking player
     * @param target the entity being attacked
     */
    public EntityAttackEvent(Object player, Object target) {
        this(player, target, false);
    }

    /**
     * Creates a typed entity-attack event with an explicit cancelable flag.
     *
     * @param player     the attacking player
     * @param target     the entity being attacked
     * @param cancelable whether listeners may cancel the attack for this call
     */
    public EntityAttackEvent(Object player, Object target, boolean cancelable) {
        super(player, target, cancelable);
    }

    @Override
    public Player player() {
        return (Player) super.player();
    }

    @Override
    public Entity target() {
        return (Entity) super.target();
    }
}
