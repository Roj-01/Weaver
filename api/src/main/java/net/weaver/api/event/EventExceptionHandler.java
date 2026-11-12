package net.weaver.api.event;

/** Receives failures thrown by one listener without aborting the remaining dispatch. */
@FunctionalInterface
public interface EventExceptionHandler {

    void handle(Event event, EventListener<?> listener, Throwable failure);
}
