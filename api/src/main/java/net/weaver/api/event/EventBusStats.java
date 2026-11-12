package net.weaver.api.event;

/**
 * An immutable statistics snapshot for one bus.
 *
 * <p>Only {@link EventBus#post(Event)} traffic is recorded. Events dispatched
 * with {@link EventBus#postFast(Event)} run with identical dispatch, filter,
 * cancellation, and exception semantics but are intentionally invisible to
 * every counter here. Use {@code post} when {@code stats()} must observe the
 * traffic.
 */
public final class EventBusStats
{
    private final long posts;
    private final long listenerInvocations;
    private final long canceledPosts;
    private final long exceptions;
    private final long totalNanos;
    private final long maxNanos;

    EventBusStats(long posts, long listenerInvocations, long canceledPosts, long exceptions,
                  long totalNanos, long maxNanos)
    {
        this.posts = posts;
        this.listenerInvocations = listenerInvocations;
        this.canceledPosts = canceledPosts;
        this.exceptions = exceptions;
        this.totalNanos = totalNanos;
        this.maxNanos = maxNanos;
    }

    public long posts()
    {
        return posts;
    }

    public long listenerInvocations()
    {
        return listenerInvocations;
    }

    public long canceledPosts()
    {
        return canceledPosts;
    }

    public long exceptions()
    {
        return exceptions;
    }

    public long totalNanos()
    {
        return totalNanos;
    }

    public long maxNanos()
    {
        return maxNanos;
    }

    @Override
    public String toString()
    {
        return "EventBusStats{posts=" + posts + ", listenerInvocations=" + listenerInvocations
                + ", canceledPosts=" + canceledPosts + ", exceptions=" + exceptions
                + ", totalNanos=" + totalNanos + ", maxNanos=" + maxNanos + '}';
    }
}
