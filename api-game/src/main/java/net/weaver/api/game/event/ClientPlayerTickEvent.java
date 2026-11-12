package net.weaver.api.game.event;

import net.minecraft.client.player.LocalPlayer;

/**
 * Typed variant of {@link net.weaver.api.event.events.ClientPlayerTickEvent}.
 *
 * <p>Fired at method entry to and normal exit from {@code LocalPlayer#tick}.
 * The event is not cancelable. The payload is narrowed to the game type; the
 * phase is inherited from the base event. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class ClientPlayerTickEvent
        extends net.weaver.api.event.events.ClientPlayerTickEvent {

    /**
     * Creates a typed client-player-tick event.
     *
     * @param player the ticking local player
     * @param phase  the tick phase
     */
    public ClientPlayerTickEvent(Object player, Phase phase) {
        super(player, phase);
    }

    @Override
    public LocalPlayer player() {
        return (LocalPlayer) super.player();
    }
}
