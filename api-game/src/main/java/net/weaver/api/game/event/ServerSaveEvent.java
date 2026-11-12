package net.weaver.api.game.event;

import net.minecraft.server.MinecraftServer;

/**
 * Typed variant of {@link net.weaver.api.event.events.ServerSaveEvent}.
 *
 * <p>The server accessor is narrowed to {@link MinecraftServer}. The inherited
 * {@link Object}-typed accessor stays available for binary compatibility.
 */
public final class ServerSaveEvent extends net.weaver.api.event.events.ServerSaveEvent {

    /**
     * Creates a typed server-save event.
     *
     * @param server the server saving all chunks
     * @param phase  the save phase
     */
    public ServerSaveEvent(Object server, Phase phase) {
        super(server, phase);
    }

    @Override
    public MinecraftServer server() {
        return (MinecraftServer) super.server();
    }
}
