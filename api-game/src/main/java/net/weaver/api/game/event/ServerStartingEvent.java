package net.weaver.api.game.event;

import net.minecraft.server.MinecraftServer;

/**
 * Typed variant of {@link net.weaver.api.event.events.ServerStartingEvent}.
 *
 * <p>The server accessor is narrowed to {@link MinecraftServer}. The inherited
 * {@link Object}-typed accessor stays available for binary compatibility.
 */
public final class ServerStartingEvent extends net.weaver.api.event.events.ServerStartingEvent {

    /**
     * Creates a typed server-starting event.
     *
     * @param server the server that is about to initialize
     */
    public ServerStartingEvent(Object server) {
        super(server);
    }

    @Override
    public MinecraftServer server() {
        return (MinecraftServer) super.server();
    }
}
