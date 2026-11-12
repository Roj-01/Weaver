package net.weaver.compatsuite;

import net.weaver.api.event.Event;

/** A synthetic event posted on the mod's own local bus. */
public final class CompatPingEvent extends Event {

    private final String payload;

    public CompatPingEvent(String payload) {
        this.payload = payload;
    }

    public String payload() {
        return payload;
    }
}
