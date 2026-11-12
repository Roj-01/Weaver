package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

/**
 * Typed variant of {@link net.weaver.api.event.events.ContainerOpenEvent}.
 *
 * <p>The payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class ContainerOpenEvent extends net.weaver.api.event.events.ContainerOpenEvent {

    /**
     * Creates a typed container-open event.
     *
     * @param player       the player opening the menu
     * @param menuProvider the provider that builds the menu
     */
    public ContainerOpenEvent(Object player, Object menuProvider) {
        super(player, menuProvider);
    }

    @Override
    public ServerPlayer player() {
        return (ServerPlayer) super.player();
    }

    @Override
    public MenuProvider menuProvider() {
        return (MenuProvider) super.menuProvider();
    }
}
