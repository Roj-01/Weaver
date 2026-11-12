package net.weaver.api.event;

/** A single event callback. */
@FunctionalInterface
public interface EventListener<E extends Event> {

    void invoke(E event);
}
