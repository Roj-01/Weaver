package net.weaver.api.game.event.player;

import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.event.Event;

/**
 * Fired on the server after a player's selected hotbar slot changed at the
 * client's request ({@code ServerGamePacketListenerImpl#handleSetCarriedItem}).
 */
public final class PlayerSwitchHotbarSlotPostEvent extends Event {
    private final ServerPlayer player;
    private final int oldSlot;
    private final int newSlot;

    public PlayerSwitchHotbarSlotPostEvent(ServerPlayer player, int oldSlot, int newSlot) {
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
