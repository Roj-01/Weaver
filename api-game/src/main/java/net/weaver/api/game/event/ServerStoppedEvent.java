package net.weaver.api.game.event;

import net.minecraft.server.MinecraftServer;

/**
 * Typed variant of {@link net.weaver.api.event.events.ServerStoppedEvent}.
 *
 * <p>The server accessor is narrowed to {@link MinecraftServer}. The inherited
 * {@link Object}-typed accessor stays available for binary compatibility.
 */
public final class ServerStoppedEvent extends net.weaver.api.event.events.ServerStoppedEvent {

    /**
     * Creates a typed server-stopped event.
     *
     * @param server the server that stopped
     */
    public ServerStoppedEvent(Object server) {
        super(server);
    }

    @Override
    public MinecraftServer server() {
        return (MinecraftServer) super.server();
    }
}
