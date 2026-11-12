package net.weaver.api.game.event;

import net.minecraft.server.MinecraftServer;

/**
 * Typed variant of {@link net.weaver.api.event.events.ServerStoppingEvent}.
 *
 * <p>The server accessor is narrowed to {@link MinecraftServer}. The inherited
 * {@link Object}-typed accessor stays available for binary compatibility.
 */
public final class ServerStoppingEvent extends net.weaver.api.event.events.ServerStoppingEvent {

    /**
     * Creates a typed server-stopping event.
     *
     * @param server the server that is shutting down
     */
    public ServerStoppingEvent(Object server) {
        super(server);
    }

    @Override
    public MinecraftServer server() {
        return (MinecraftServer) super.server();
    }
}
