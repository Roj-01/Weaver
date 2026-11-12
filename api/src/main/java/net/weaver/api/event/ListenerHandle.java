package net.weaver.api.event;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** A removable registration returned by {@link EventBus}. */
public final class ListenerHandle {

    private final EventBus bus;
    private final Class<? extends Event> eventType;
    private final Object owner;
    private final Runnable remover;
    private final AtomicBoolean active = new AtomicBoolean(true);

    public ListenerHandle(EventBus bus, Class<? extends Event> eventType, Object owner, Runnable remover) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.remover = Objects.requireNonNull(remover, "remover");
    }

    public EventBus bus() {
        return bus;
    }

    public Class<? extends Event> eventType() {
        return eventType;
    }

    public Object owner() {
        return owner;
    }

    public boolean isActive() {
        return active.get();
    }

    public void unregister() {
        if (active.compareAndSet(true, false)) {
            remover.run();
        }
    }

    @Override
    public String toString() {
        return "ListenerHandle{" + eventType.getName() + ", owner=" + owner
                + ", active=" + active.get() + "}";
    }
}
