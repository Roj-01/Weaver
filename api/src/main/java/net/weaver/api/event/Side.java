package net.weaver.api.event;

/** The physical or logical side an event belongs to. */
public enum Side {
    CLIENT,
    SERVER,
    BOTH;

    public boolean isClient() {
        return this == CLIENT || this == BOTH;
    }

    public boolean isServer() {
        return this == SERVER || this == BOTH;
    }

    public boolean matches(Side other) {
        return this == BOTH || other == BOTH || this == other;
    }
}
