package net.weaver.api.game.event.player;

import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.event.Event;

/**
 * Fired on the server after a player's client sent new settings (language,
 * view distance, chat visibility, skin parts, main hand) during play
 * ({@code ServerGamePacketListenerImpl#handleClientInformation}).
 */
public final class ClientInformationUpdatedEvent extends Event {
    private final ServerPlayer player;
    private final ClientInformation oldInformation;
    private final ClientInformation newInformation;

    public ClientInformationUpdatedEvent(ServerPlayer player, ClientInformation oldInformation,
                                         ClientInformation newInformation) {
        this.player = player;
        this.oldInformation = oldInformation;
        this.newInformation = newInformation;
    }

    public ServerPlayer player() {
        return player;
    }

    public ClientInformation oldInformation() {
        return oldInformation;
    }

    public ClientInformation newInformation() {
        return newInformation;
    }
}
