package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/** The server is saving or finished saving all chunks. */
public class ServerSaveEvent extends Event {

    public enum Phase {
        START,
        FINISHED
    }

    private final Object server;
    private final Phase phase;

    public ServerSaveEvent(Object server, Phase phase) {
        this.server = Objects.requireNonNull(server, "server");
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public Object server() {
        return server;
    }

    public Phase phase() {
        return phase;
    }
}
