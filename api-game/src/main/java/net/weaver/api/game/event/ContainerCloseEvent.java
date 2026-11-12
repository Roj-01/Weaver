package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Typed variant of {@link net.weaver.api.event.events.ContainerCloseEvent}.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code ServerPlayer#closeContainer}. The event is not emitted when no
 * non-inventory menu is open. Observation-only and not cancelable. The payloads
 * are narrowed to the game types. The inherited accessors stay available with
 * their {@link Object} descriptors for binary compatibility.
 */
public final class ContainerCloseEvent extends net.weaver.api.event.events.ContainerCloseEvent {

    /**
     * Creates a typed container-close event.
     *
     * @param player the player closing the menu
     * @param menu   the menu being closed
     */
    public ContainerCloseEvent(Object player, Object menu) {
        super(player, menu);
    }

    @Override
    public ServerPlayer player() {
        return (ServerPlayer) super.player();
    }

    @Override
    public AbstractContainerMenu menu() {
        return (AbstractContainerMenu) super.menu();
    }
}
