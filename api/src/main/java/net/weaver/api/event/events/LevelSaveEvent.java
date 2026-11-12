package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/** A server level is saving or finished saving. */
public class LevelSaveEvent extends Event {

    public enum Phase {
        START,
        FINISHED
    }

    private final Object level;
    private final Phase phase;

    public LevelSaveEvent(Object level, Phase phase) {
        this.level = Objects.requireNonNull(level, "level");
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public Object level() {
        return level;
    }

    public Phase phase() {
        return phase;
    }
}
