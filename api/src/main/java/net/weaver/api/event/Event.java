package net.weaver.api.event;

import java.util.Objects;

/** Base type for every dispatcher event. */
public class Event {

    private final boolean cancelable;
    private final boolean hasResult;
    private volatile EventBus bus;
    private volatile boolean canceled;
    private volatile EventResult result = EventResult.DEFAULT;
    private volatile LogicalSide logicalSide = LogicalSide.BOTH;

    public Event() {
        this(false, false);
    }

    public Event(boolean cancelable) {
        this(cancelable, false);
    }

    public Event(boolean cancelable, boolean hasResult) {
        this.cancelable = cancelable;
        this.hasResult = hasResult;
    }

    public boolean isCancelable() {
        return cancelable;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        if (!cancelable) {
            throw new IllegalStateException(getName() + " is not cancelable");
        }
        this.canceled = canceled;
    }

    public boolean hasResult() {
        return hasResult;
    }

    public EventResult getResult() {
        return result;
    }

    public void setResult(EventResult result) {
        if (!hasResult) {
            throw new IllegalStateException(getName() + " does not expose a result");
        }
        this.result = Objects.requireNonNull(result, "result");
    }

    /**
     * The logical side that produced this event.
     *
     * <p>This is metadata; the bus does not filter listeners by it on its own.
     * A registration can opt into dispatch-time side filtering with
     * {@link EventFilter#logicalSide(LogicalSide)}, which reads this value.
     * Custom events default to {@link LogicalSide#BOTH}. Listeners that must
     * run only on one logical side should check this value when both sides can
     * exist in the same process (a client with an integrated server) or
     * register with a side filter.
     */
    public LogicalSide logicalSide() {
        return logicalSide;
    }

    /**
     * Sets the logical side metadata and returns this event for chaining.
     * {@code null} resets the value to {@link LogicalSide#BOTH}.
     */
    public Event setLogicalSide(LogicalSide logicalSide) {
        this.logicalSide = logicalSide == null ? LogicalSide.BOTH : logicalSide;
        return this;
    }

    public EventBus getBus() {
        return bus;
    }

    public void setBus(EventBus bus) {
        if (this.bus == null) {
            this.bus = bus;
        }
    }

    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return getName() + "{cancelable=" + cancelable + ", canceled=" + canceled
                + ", result=" + result + "}";
    }
}
