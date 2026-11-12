package net.weaver.api.event;

import net.weaver.api.event.events.ModLifecycleEvent;

import java.util.Objects;

/**
 * A dispatch-time predicate that decides whether one listener runs for an
 * event.
 *
 * <p>A filter is consulted after the cancellation check and before the
 * listener is invoked. Rejecting an event does not count as a listener
 * invocation in {@link EventBusStats}. Filters must be side-effect free and
 * fast; they run inside the dispatch loop.
 *
 * @see #all()
 * @see #logicalSide(LogicalSide)
 * @see #modId(String)
 * @see #and(EventFilter...)
 * @see #or(EventFilter...)
 */
@FunctionalInterface
public interface EventFilter
{
    /**
     * Filter that accepts every event. This is the singleton returned by
     * {@link #all()} and the value used when a registration passes a
     * {@code null} filter.
     */
    EventFilter ALL = event -> true;

    /**
     * Tests whether an event should reach the listener.
     *
     * @param event the event being dispatched, never {@code null}
     * @return {@code true} to run the listener
     */
    boolean accept(Event event);

    /**
     * Returns the singleton filter that accepts every event.
     *
     * @return the accept-all filter
     */
    static EventFilter all()
    {
        return ALL;
    }

    /**
     * Accepts events whose logical side matches {@code expected}.
     *
     * <p>{@link LogicalSide#BOTH} as the expected side accepts every event.
     * Otherwise an event is accepted when {@link LogicalSide#matches} reports
     * a match, so {@code BOTH} events pass every side filter, while
     * {@code SERVER} rejects {@code CLIENT} events and vice versa.
     *
     * @param expected the required logical side, not {@code null}
     * @return a side filter
     */
    static EventFilter logicalSide(LogicalSide expected)
    {
        Objects.requireNonNull(expected, "expected");
        if (expected == LogicalSide.BOTH)
        {
            return ALL;
        }
        return event -> event.logicalSide().matches(expected);
    }

    /**
     * Accepts events that belong to the given mod.
     *
     * <p>An event matches when it implements {@link ModScopedEvent} with the
     * requested id, when it is a {@link ModLifecycleEvent} for that id, or when
     * it was posted on a local bus named {@code mod:<modId>}. The bus check
     * only succeeds after the event has been posted, because the bus reference
     * is assigned at that point.
     *
     * @param modId the required mod id, not {@code null}
     * @return a mod-scoped filter
     */
    static EventFilter modId(String modId)
    {
        Objects.requireNonNull(modId, "modId");
        String busName = "mod:" + modId;
        return event ->
        {
            if (event instanceof ModScopedEvent scoped && modId.equals(scoped.modId()))
            {
                return true;
            }
            if (event instanceof ModLifecycleEvent lifecycle && modId.equals(lifecycle.modId()))
            {
                return true;
            }
            EventBus bus = event.getBus();
            return bus != null && busName.equals(bus.name());
        };
    }

    /**
     * Combines filters with logical AND, evaluating them in argument order and
     * stopping at the first rejection.
     *
     * <p>With no arguments this returns {@link #all()}. A {@code null} array or
     * any {@code null} element is rejected with {@link NullPointerException}.
     *
     * @param filters the filters to evaluate, not {@code null}
     * @return a filter that accepts only when every component accepts
     */
    static EventFilter and(EventFilter... filters)
    {
        Objects.requireNonNull(filters, "filters");
        if (filters.length == 0)
        {
            return ALL;
        }
        EventFilter[] copy = filters.clone();
        for (EventFilter filter : copy)
        {
            Objects.requireNonNull(filter, "filter");
        }
        return event ->
        {
            for (EventFilter filter : copy)
            {
                if (!filter.accept(event))
                {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * Combines filters with logical OR, evaluating them in argument order and
     * stopping at the first acceptance.
     *
     * <p>With no arguments this returns a reject-all filter. A {@code null}
     * array or any {@code null} element is rejected with
     * {@link NullPointerException}.
     *
     * @param filters the filters to evaluate, not {@code null}
     * @return a filter that accepts when any component accepts
     */
    static EventFilter or(EventFilter... filters)
    {
        Objects.requireNonNull(filters, "filters");
        EventFilter[] copy = filters.clone();
        for (EventFilter filter : copy)
        {
            Objects.requireNonNull(filter, "filter");
        }
        if (copy.length == 0)
        {
            return event -> false;
        }
        return event ->
        {
            for (EventFilter filter : copy)
            {
                if (filter.accept(event))
                {
                    return true;
                }
            }
            return false;
        };
    }
}
