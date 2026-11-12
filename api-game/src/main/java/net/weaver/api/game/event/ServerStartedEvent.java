package net.weaver.api.game.event;

import net.minecraft.server.MinecraftServer;

/**
 * Typed variant of {@link net.weaver.api.event.events.ServerStartedEvent}.
 *
 * <p>The server accessor is narrowed to {@link MinecraftServer}. The inherited
 * {@link Object}-typed accessor stays available for binary compatibility.
 */
public final class ServerStartedEvent extends net.weaver.api.event.events.ServerStartedEvent {

    /**
     * Creates a typed server-started event.
     *
     * @param server the server that finished initialization
     */
    public ServerStartedEvent(Object server) {
        super(server);
    }

    @Override
    public MinecraftServer server() {
        return (MinecraftServer) super.server();
    }
}
