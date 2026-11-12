package net.weaver.api.event;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standalone dispatch benchmark for {@link EventBus}.
 *
 * <p>Run with the dedicated Gradle task:
 *
 * <pre>
 * .\gradlew.bat :api:benchmark
 * </pre>
 *
 * <p>The benchmark warms up every configuration, then measures one million
 * posts. It reports the current bus ({@code BENCH}), the statistics-free
 * ({@code BENCH_FAST}) path, the pre-2.1 dispatch path (per-post chain
 * allocation plus {@code Method.invoke}, {@code BENCH_LEGACY}), and a bare
 * reflective listener ({@code BENCH_REFLECTION}). The {@code BENCH_AFTER} line
 * states the speed-up over the legacy path and {@code BENCH_FAST_AFTER} states
 * the speed-up of {@code postFast} over {@code post}.
 *
 * <p>It also measures dispatch-time filters with
 * {@link EventFilter#logicalSide(LogicalSide)}: the accept path
 * ({@code BENCH_FILTER_ACCEPT}) and the reject path
 * ({@code BENCH_FILTER_REJECT}) for {@code post}, plus the matching
 * {@code BENCH_FILTER_FAST_ACCEPT} and {@code BENCH_FILTER_FAST_REJECT} lines
 * for {@code postFast}. Every line uses the same listener counts and post
 * counts as the unfiltered benchmarks so the numbers stay comparable.
 */
public final class EventBusBenchmark
{
    private static final long WARMUP_POSTS = 200_000L;
    private static final long MEASURED_POSTS = 1_000_000L;
    private static final int[] LISTENER_COUNTS = {1, 10, 100, 1000};

    private static volatile long blackhole;

    private EventBusBenchmark()
    {
    }

    /**
     * Runs the benchmark and prints one line per configuration.
     *
     * @param args ignored
     */
    public static void main(String[] args)
    {
        for (int listeners : LISTENER_COUNTS)
        {
            long current = measureBus(listeners);
            long fast = measureFast(listeners);
            long legacy = measureLegacy(listeners);
            report("BENCH", listeners, MEASURED_POSTS, current);
            report("BENCH_FAST", listeners, MEASURED_POSTS, fast);
            report("BENCH_LEGACY", listeners, MEASURED_POSTS, legacy);
            reportAfter(listeners, current, legacy);
            reportFastAfter(listeners, fast, current);
        }
        measureReflectionBaseline();
        for (int listeners : LISTENER_COUNTS)
        {
            measureFilter(listeners, true, false);
            measureFilter(listeners, false, false);
            measureFilter(listeners, true, true);
            measureFilter(listeners, false, true);
        }
    }

    private static long measureBus(int listeners)
    {
        EventBus bus = new EventBus("bench-" + listeners);
        CountingListener[] registered = register(bus, listeners);
        BenchEvent event = new BenchEvent();
        for (long post = 0; post < WARMUP_POSTS; post++)
        {
            bus.post(event);
        }
        AllocationGauge gauge = AllocationGauge.start();
        bus.resetStats();
        long started = System.nanoTime();
        for (long post = 0; post < MEASURED_POSTS; post++)
        {
            bus.post(event);
        }
        long elapsed = System.nanoTime() - started;
        long allocations = gauge.finish();
        blackhole = blackhole + calls(registered);
        reportAllocations("BENCH", listeners, allocations);
        return elapsed;
    }

    private static long measureFast(int listeners)
    {
        EventBus bus = new EventBus("bench-fast-" + listeners);
        CountingListener[] registered = register(bus, listeners);
        BenchEvent event = new BenchEvent();
        for (long post = 0; post < WARMUP_POSTS; post++)
        {
            bus.postFast(event);
        }
        AllocationGauge gauge = AllocationGauge.start();
        long started = System.nanoTime();
        for (long post = 0; post < MEASURED_POSTS; post++)
        {
            bus.postFast(event);
        }
        long elapsed = System.nanoTime() - started;
        long allocations = gauge.finish();
        blackhole = blackhole + calls(registered);
        reportAllocations("BENCH_FAST", listeners, allocations);
        return elapsed;
    }

    private static void measureFilter(int listeners, boolean accept, boolean fast)
    {
        EventBus bus = new EventBus("bench-filter-" + (fast ? "fast-" : "")
                + (accept ? "accept-" : "reject-") + listeners);
        EventFilter filter = EventFilter.logicalSide(LogicalSide.SERVER);
        CountingListener[] registered = new CountingListener[listeners];
        for (int index = 0; index < listeners; index++)
        {
            CountingListener listener = new CountingListener();
            registered[index] = listener;
            bus.addListener(BenchEvent.class, listener, filter);
        }
        BenchEvent event = new BenchEvent();
        event.setLogicalSide(accept ? LogicalSide.SERVER : LogicalSide.CLIENT);
        for (long post = 0; post < WARMUP_POSTS; post++)
        {
            post(bus, event, fast);
        }
        AllocationGauge gauge = AllocationGauge.start();
        if (!fast)
        {
            bus.resetStats();
        }
        long started = System.nanoTime();
        for (long post = 0; post < MEASURED_POSTS; post++)
        {
            post(bus, event, fast);
        }
        long elapsed = System.nanoTime() - started;
        long allocations = gauge.finish();
        blackhole = blackhole + calls(registered);
        String label = (fast ? "BENCH_FILTER_FAST_" : "BENCH_FILTER_")
                + (accept ? "ACCEPT" : "REJECT");
        reportAllocations(label, listeners, allocations);
        report(label, listeners, MEASURED_POSTS, elapsed);
    }

    private static void post(EventBus bus, Event event, boolean fast)
    {
        if (fast)
        {
            bus.postFast(event);
        }
        else
        {
            bus.post(event);
        }
    }

    private static CountingListener[] register(EventBus bus, int listeners)
    {
        CountingListener[] registered = new CountingListener[listeners];
        for (int index = 0; index < listeners; index++)
        {
            CountingListener listener = new CountingListener();
            registered[index] = listener;
            bus.addListener(BenchEvent.class, listener);
        }
        return registered;
    }

    private static long calls(CountingListener[] registered)
    {
        long calls = 0;
        for (CountingListener listener : registered)
        {
            calls += listener.calls;
        }
        return calls;
    }

    private static long measureLegacy(int listeners)
    {
        LegacyBus bus = new LegacyBus(null);
        CountingListener[] registered = new CountingListener[listeners];
        try
        {
            Method method = CountingListener.class.getDeclaredMethod("invoke", BenchEvent.class);
            method.setAccessible(true);
            for (int index = 0; index < listeners; index++)
            {
                CountingListener listener = new CountingListener();
                registered[index] = listener;
                bus.addListener(BenchEvent.class, listener, method);
            }
        }
        catch (NoSuchMethodException failure)
        {
            throw new IllegalStateException(failure);
        }
        BenchEvent event = new BenchEvent();
        for (long post = 0; post < WARMUP_POSTS; post++)
        {
            bus.post(event);
        }
        AllocationGauge gauge = AllocationGauge.start();
        long started = System.nanoTime();
        for (long post = 0; post < MEASURED_POSTS; post++)
        {
            bus.post(event);
        }
        long elapsed = System.nanoTime() - started;
        long allocations = gauge.finish();
        blackhole = blackhole + calls(registered);
        reportAllocations("BENCH_LEGACY", listeners, allocations);
        return elapsed;
    }

    private static void measureReflectionBaseline()
    {
        EventBus bus = EventBus.local("bench-reflection");
        ReflectionListener listener = new ReflectionListener();
        bus.addListener(BenchEvent.class, listener::invoke);
        BenchEvent event = new BenchEvent();
        for (long post = 0; post < WARMUP_POSTS; post++)
        {
            bus.post(event);
        }
        bus.resetStats();
        long started = System.nanoTime();
        for (long post = 0; post < MEASURED_POSTS; post++)
        {
            bus.post(event);
        }
        long elapsed = System.nanoTime() - started;
        blackhole = blackhole + listener.calls();
        report("BENCH_REFLECTION", 1, MEASURED_POSTS, elapsed);
    }

    private static void report(String label, int listeners, long posts, long elapsed)
    {
        System.out.printf(Locale.ROOT,
                "%s listeners=%d posts=%d ns_per_post=%.2f total_ms=%.2f%n",
                label, listeners, posts, (double) elapsed / posts, elapsed / 1_000_000.0);
    }

    private static void reportAfter(int listeners, long current, long legacy)
    {
        if (current <= 0)
        {
            return;
        }
        System.out.printf(Locale.ROOT,
                "BENCH_AFTER listeners=%d legacy_ns_per_post=%.2f current_ns_per_post=%.2f speedup=%.2fx%n",
                listeners, (double) legacy / MEASURED_POSTS, (double) current / MEASURED_POSTS,
                (double) legacy / current);
    }

    private static void reportFastAfter(int listeners, long fast, long current)
    {
        if (fast <= 0)
        {
            return;
        }
        System.out.printf(Locale.ROOT,
                "BENCH_FAST_AFTER listeners=%d post_ns_per_post=%.2f fast_ns_per_post=%.2f speedup=%.2fx%n",
                listeners, (double) current / MEASURED_POSTS, (double) fast / MEASURED_POSTS,
                (double) current / fast);
    }

    private static void reportAllocations(String label, int listeners, long bytes)
    {
        if (bytes < 0)
        {
            System.out.println(label + " listeners=" + listeners + " allocations=unavailable");
            return;
        }
        System.out.printf(Locale.ROOT, "%s listeners=%d bytes_per_post=%.2f%n",
                label, listeners, (double) bytes / MEASURED_POSTS);
    }

    /**
     * Measures allocation on the current thread when the JVM exposes
     * {@code getThreadAllocatedBytes}; otherwise returns {@code -1}.
     */
    private static final class AllocationGauge
    {
        private final Method method;
        private final Thread thread;
        private final long before;

        private AllocationGauge(Method method, Thread thread, long before)
        {
            this.method = method;
            this.thread = thread;
            this.before = before;
        }

        private static AllocationGauge start()
        {
            try
            {
                ThreadMXBean bean = ManagementFactory.getThreadMXBean();
                Class<?> sunBean = Class.forName("com.sun.management.ThreadMXBean");
                Method method = sunBean.getMethod("getThreadAllocatedBytes", long.class);
                Thread thread = Thread.currentThread();
                long before = (Long) method.invoke(bean, thread.threadId());
                return new AllocationGauge(method, thread, before);
            }
            catch (Throwable unavailable)
            {
                return new AllocationGauge(null, null, 0L);
            }
        }

        private long finish()
        {
            if (method == null)
            {
                return -1L;
            }
            try
            {
                ThreadMXBean bean = ManagementFactory.getThreadMXBean();
                long after = (Long) method.invoke(bean, thread.threadId());
                return after - before;
            }
            catch (Throwable unavailable)
            {
                return -1L;
            }
        }
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

    private static final class ReflectionListener
    {
        private final Method method;
        private long calls;

        private ReflectionListener()
        {
            try
            {
                method = ReflectionListener.class.getDeclaredMethod("handle", Event.class);
                method.setAccessible(true);
            }
            catch (NoSuchMethodException failure)
            {
                throw new IllegalStateException(failure);
            }
        }

        private void invoke(Event event)
        {
            try
            {
                method.invoke(this, event);
            }
            catch (IllegalAccessException | InvocationTargetException failure)
            {
                throw new IllegalStateException(failure);
            }
        }

        private void handle(Event event)
        {
            calls++;
        }

        private long calls()
        {
            return calls;
        }
    }

    /**
     * The pre-2.1 dispatch model: a per-post ancestor list, a snapshot lookup,
     * and {@code Method.invoke} for every listener. Used only as a measurement
     * baseline.
     */
    private static final class LegacyBus
    {
        private final LegacyBus parent;
        private final Map<Class<? extends Event>, List<LegacyRegistration>> registrations =
                new HashMap<>();
        private final Map<Class<? extends Event>, LegacyRegistration[]> snapshots =
                new ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicLong posts =
                new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicLong invocations =
                new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicLong totalNanos =
                new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicLong maxNanos =
                new java.util.concurrent.atomic.AtomicLong();

        private LegacyBus(LegacyBus parent)
        {
            this.parent = parent;
        }

        private void addListener(Class<? extends Event> type, Object target, Method method)
        {
            registrations.computeIfAbsent(type, ignored -> new ArrayList<>())
                    .add(new LegacyRegistration(type, target, method));
            snapshots.clear();
        }

        private boolean post(Event event)
        {
            long started = System.nanoTime();
            List<LegacyBus> chain = new ArrayList<>();
            for (LegacyBus bus = this; bus != null; bus = bus.parent)
            {
                chain.add(bus);
            }
            for (LegacyBus bus : chain)
            {
                bus.dispatch(bus.snapshotFor(event.getClass()), event);
            }
            long nanos = System.nanoTime() - started;
            posts.incrementAndGet();
            totalNanos.addAndGet(nanos);
            long current = maxNanos.get();
            while (nanos > current && !maxNanos.compareAndSet(current, nanos))
            {
                current = maxNanos.get();
            }
            return !event.isCanceled();
        }

        private LegacyRegistration[] snapshotFor(Class<? extends Event> type)
        {
            return snapshots.computeIfAbsent(type, this::buildSnapshot);
        }

        private LegacyRegistration[] buildSnapshot(Class<? extends Event> type)
        {
            List<LegacyRegistration> matched = new ArrayList<>();
            for (Class<?> current = type; current != null; current = current.getSuperclass())
            {
                List<LegacyRegistration> listeners = registrations.get(current);
                if (listeners != null)
                {
                    matched.addAll(listeners);
                }
            }
            return matched.toArray(new LegacyRegistration[0]);
        }

        private void dispatch(LegacyRegistration[] snapshot, Event event)
        {
            for (LegacyRegistration registration : snapshot)
            {
                invocations.incrementAndGet();
                try
                {
                    registration.method.invoke(registration.target, event);
                }
                catch (IllegalAccessException | InvocationTargetException ignored)
                {
                }
            }
        }
    }

    private record LegacyRegistration(Class<? extends Event> type, Object target, Method method)
    {
    }

    private static final class BenchEvent extends Event
    {
    }
}
