package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Typed variant of {@link net.weaver.api.event.events.PlayerDeathEvent}.
 *
 * <p>The payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class PlayerDeathEvent extends net.weaver.api.event.events.PlayerDeathEvent {

    /**
     * Creates a typed player-death event.
     *
     * @param player the dying player
     * @param source the damage source that killed the player
     */
    public PlayerDeathEvent(Object player, Object source) {
        super(player, source);
    }

    @Override
    public ServerPlayer player() {
        return (ServerPlayer) super.player();
    }

    @Override
    public DamageSource source() {
        return (DamageSource) super.source();
    }
}
