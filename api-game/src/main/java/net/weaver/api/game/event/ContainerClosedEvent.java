package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Typed variant of {@link net.weaver.api.event.events.ContainerClosedEvent}.
 *
 * <p>Fired at method entry to {@code ServerPlayer#doCloseContainer}, before the
 * container menu is removed. This is the completion path, distinct from
 * {@link ContainerCloseEvent}. Observation-only and not cancelable. The
 * payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class ContainerClosedEvent
        extends net.weaver.api.event.events.ContainerClosedEvent {

    /**
     * Creates a typed container-closed event.
     *
     * @param player        the player whose container menu is closing
     * @param containerMenu the container menu being closed
     */
    public ContainerClosedEvent(Object player, Object containerMenu) {
        super(player, containerMenu);
    }

    @Override
    public ServerPlayer player() {
        return (ServerPlayer) super.player();
    }

    @Override
    public AbstractContainerMenu containerMenu() {
        return (AbstractContainerMenu) super.containerMenu();
    }
}
