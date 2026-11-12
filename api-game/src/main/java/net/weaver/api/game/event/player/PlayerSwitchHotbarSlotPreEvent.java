package net.weaver.api.game.event.player;

import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.event.Event;

/**
 * Fired on the server when a player's client selects another hotbar slot
 * ({@code ServerGamePacketListenerImpl#handleSetCarriedItem}). Cancelable: a
 * canceled event keeps the old slot, and the server tells the client to
 * select it again.
 */
public final class PlayerSwitchHotbarSlotPreEvent extends Event {
    private final ServerPlayer player;
    private final int oldSlot;
    private final int newSlot;

    public PlayerSwitchHotbarSlotPreEvent(ServerPlayer player, int oldSlot, int newSlot) {
        super(true);
        this.player = player;
        this.oldSlot = oldSlot;
        this.newSlot = newSlot;
    }

    public ServerPlayer player() {
        return player;
    }

    public int oldSlot() {
        return oldSlot;
    }

    public int newSlot() {
        return newSlot;
    }
}
