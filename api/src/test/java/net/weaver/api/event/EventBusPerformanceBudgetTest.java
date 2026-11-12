package net.weaver.api.event;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coarse performance regression guards for {@link EventBus}.
 *
 * <p>These are not micro-benchmarks. They run a short warm-up and a small
 * measured loop with ceilings that are one to two orders of magnitude above
 * the numbers reported by {@link EventBusBenchmark}, so they stay green on
 * slow or shared CI machines while still catching structural regressions such
 * as a per-post allocation, a lost snapshot cache, or an accidental
 * {@code Method.invoke} on the hot path.
 */
class EventBusPerformanceBudgetTest
{
    private static final int WARMUP_POSTS = 50_000;
    private static final int MEASURED_POSTS = 200_000;

    private static volatile long blackhole;

    @Test
    void postWithOneListenerStaysUnderBudget()
    {
        EventBus bus = new EventBus("budget-post-1");
        CountingListener listener = new CountingListener();
        bus.addListener(BenchEvent.class, listener);

        double nanos = measurePost(bus, new BenchEvent());

        assertTrue(nanos < 10_000.0,
                () -> "post with one listener took " + nanos + " ns/post");
        assertTrue(listener.calls == WARMUP_POSTS + MEASURED_POSTS, "listener must run for every post");
    }

    @Test
    void postFastWithOneListenerStaysUnderBudget()
    {
        EventBus bus = new EventBus("budget-fast-1");
        CountingListener listener = new CountingListener();
        bus.addListener(BenchEvent.class, listener);

        double nanos = measurePostFast(bus, new BenchEvent());

        assertTrue(nanos < 10_000.0,
                () -> "postFast with one listener took " + nanos + " ns/post");
        assertTrue(listener.calls == WARMUP_POSTS + MEASURED_POSTS, "listener must run for every post");
    }

    @Test
    void postWithHundredListenersStaysUnderBudget()
    {
        EventBus bus = new EventBus("budget-post-100");
        CountingListener[] listeners = register(bus, 100, null);

        double nanos = measurePost(bus, new BenchEvent());

        assertTrue(nanos < 100_000.0,
                () -> "post with 100 listeners took " + nanos + " ns/post");
        assertTrue(calls(listeners) == 100L * (WARMUP_POSTS + MEASURED_POSTS), "every listener must run");
    }

    @Test
    void filterAcceptWithHundredListenersStaysUnderBudget()
    {
        EventBus bus = new EventBus("budget-filter-accept");
        EventFilter filter = EventFilter.logicalSide(LogicalSide.SERVER);
        CountingListener[] listeners = register(bus, 100, filter);
        BenchEvent event = new BenchEvent();
        event.setLogicalSide(LogicalSide.SERVER);

        double nanos = measurePost(bus, event);

        assertTrue(nanos < 100_000.0,
                () -> "accepted filter with 100 listeners took " + nanos + " ns/post");
        assertTrue(calls(listeners) == 100L * (WARMUP_POSTS + MEASURED_POSTS), "every listener must run");
    }

    @Test
    void filterRejectWithHundredListenersStaysUnderBudget()
    {
        EventBus bus = new EventBus("budget-filter-reject");
        EventFilter filter = EventFilter.logicalSide(LogicalSide.SERVER);
        CountingListener[] listeners = register(bus, 100, filter);
        BenchEvent event = new BenchEvent();
        event.setLogicalSide(LogicalSide.CLIENT);

        double nanos = measurePost(bus, event);

        assertTrue(nanos < 50_000.0,
                () -> "rejected filter with 100 listeners took " + nanos + " ns/post");
        assertTrue(calls(listeners) == 0L, "rejected listeners must not run");
    }

    @Test
    void postFastDoesNotAllocatePerPostInSteadyState()
    {
        ThreadAllocationGauge gauge = ThreadAllocationGauge.start();
        Assumptions.assumeTrue(gauge.available(),
                "com.sun.management.ThreadMXBean#getThreadAllocatedBytes is unavailable");

        EventBus bus = new EventBus("budget-fast-alloc");
        CountingListener listener = new CountingListener();
        bus.addListener(BenchEvent.class, listener);
        BenchEvent event = new BenchEvent();
        for (int post = 0; post < WARMUP_POSTS; post++)
        {
            bus.postFast(event);
        }

        long before = gauge.bytes();
        for (int post = 0; post < MEASURED_POSTS; post++)
        {
            bus.postFast(event);
        }
        long allocated = gauge.bytes() - before;

        double perPost = (double) allocated / MEASURED_POSTS;
        assertTrue(perPost <= 64.0,
                () -> "postFast allocated " + perPost + " bytes/post in steady state");
        blackhole = blackhole + listener.calls;
    }

    private static CountingListener[] register(EventBus bus, int count, EventFilter filter)
    {
        CountingListener[] listeners = new CountingListener[count];
        for (int index = 0; index < count; index++)
        {
            CountingListener listener = new CountingListener();
            listeners[index] = listener;
            if (filter == null)
            {
                bus.addListener(BenchEvent.class, listener);
            }
            else
            {
                bus.addListener(BenchEvent.class, listener, filter);
            }
        }
        return listeners;
    }

    private static double measurePost(EventBus bus, BenchEvent event)
    {
        for (int post = 0; post < WARMUP_POSTS; post++)
        {
            bus.post(event);
        }
        bus.resetStats();
        long started = System.nanoTime();
        for (int post = 0; post < MEASURED_POSTS; post++)
        {
            bus.post(event);
        }
        long elapsed = System.nanoTime() - started;
        return (double) elapsed / MEASURED_POSTS;
    }

    private static double measurePostFast(EventBus bus, BenchEvent event)
    {
        for (int post = 0; post < WARMUP_POSTS; post++)
        {
            bus.postFast(event);
        }
        long started = System.nanoTime();
        for (int post = 0; post < MEASURED_POSTS; post++)
        {
            bus.postFast(event);
        }
        long elapsed = System.nanoTime() - started;
        return (double) elapsed / MEASURED_POSTS;
    }

    private static long calls(CountingListener[] listeners)
    {
        long calls = 0;
        for (CountingListener listener : listeners)
        {
            calls += listener.calls;
        }
        return calls;
    }

    private static final class CountingListener implements EventListener<BenchEvent>
    {
        private long calls;

        @Override
        public void invoke(BenchEvent event)
        {
            calls++;
        }
    }

    private static final class BenchEvent extends Event
    {
    }

    /**
     * Reads the current thread's allocated bytes when the JVM exposes
     * {@code com.sun.management.ThreadMXBean#getThreadAllocatedBytes}.
     */
    private static final class ThreadAllocationGauge
    {
        private final Method method;
        private final long threadId;

        private ThreadAllocationGauge(Method method, long threadId)
        {
            this.method = method;
            this.threadId = threadId;
        }

        private static ThreadAllocationGauge start()
        {
            try
            {
                ThreadMXBean bean = ManagementFactory.getThreadMXBean();
                Class<?> sunBean = Class.forName("com.sun.management.ThreadMXBean");
                Method method = sunBean.getMethod("getThreadAllocatedBytes", long.class);
                return new ThreadAllocationGauge(method, Thread.currentThread().threadId());
            }
            catch (Throwable unavailable)
            {
                return new ThreadAllocationGauge(null, -1L);
            }
        }

        private boolean available()
        {
            return method != null;
        }

        private long bytes()
        {
            if (method == null)
            {
                return -1L;
            }
            try
            {
                ThreadMXBean bean = ManagementFactory.getThreadMXBean();
                return (Long) method.invoke(bean, threadId);
            }
            catch (Throwable unavailable)
            {
                return -1L;
            }
        }
    }
}
