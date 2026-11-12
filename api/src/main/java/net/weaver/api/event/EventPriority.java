package net.weaver.api.event;

/** Listener execution order. {@link #HIGHEST} runs first, {@link #MONITOR} last. */
public enum EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST,
    MONITOR
}
