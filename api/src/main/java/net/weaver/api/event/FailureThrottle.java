package net.weaver.api.event;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate-limits repeated failure reports. A listener that throws on every tick
 * (or every entity, or every frame) would otherwise write tens of thousands of
 * log lines a second and slow the game to a crawl. The first {@value #FREE}
 * failures of a kind are reported, then one summary line with the number of
 * suppressed repeats at most every {@value #WINDOW_SECONDS} seconds.
 */
public final class FailureThrottle
{
    /** Reports of one kind that are always shown. */
    public static final int FREE = 3;
    /** Seconds between summaries once a kind is throttled. */
    public static final int WINDOW_SECONDS = 30;
    private static final int MAX_KINDS = 4096;
    private static final long WINDOW_NANOS = WINDOW_SECONDS * 1_000_000_000L;
    private static final Map<String, Kind> KINDS = new ConcurrentHashMap<>();

    private FailureThrottle()
    {
    }

    /** What to do with one occurrence. */
    public record Decision(boolean report, boolean first, long suppressed)
    {
        /** A suffix for the report line, empty unless repeats were suppressed. */
        public String suffix()
        {
            return suppressed > 0 ? " repeats_suppressed=" + suppressed : "";
        }
    }

    /**
     * Records one occurrence of a failure kind.
     *
     * @param kind identifies the failure, for example event, listener and exception class
     * @return whether to report it now
     */
    public static Decision record(String kind)
    {
        Kind state = KINDS.get(kind);
        if (state == null)
        {
            if (KINDS.size() >= MAX_KINDS)
            {
                // Bounded memory: past the limit every kind shares one bucket.
                kind = "(overflow)";
            }
            state = KINDS.computeIfAbsent(kind, key -> new Kind());
        }
        long count = state.count.incrementAndGet();
        if (count <= FREE)
        {
            return new Decision(true, count == 1, 0);
        }
        long now = System.nanoTime();
        long last = state.lastReport.get();
        if (now - last >= WINDOW_NANOS && state.lastReport.compareAndSet(last, now))
        {
            long reported = state.reported.getAndSet(count);
            return new Decision(true, false, Math.max(0, count - reported - 1));
        }
        return new Decision(false, false, 0);
    }

    private static final class Kind
    {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong lastReport = new AtomicLong(System.nanoTime());
        private final AtomicLong reported = new AtomicLong(FREE);
    }
}
