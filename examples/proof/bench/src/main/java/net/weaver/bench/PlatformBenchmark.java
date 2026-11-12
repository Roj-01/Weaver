package net.weaver.bench;

import net.weaver.api.event.Event;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.EventListener;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.events.LevelTickEvent;
import net.weaver.api.event.events.PlayerTickEvent;
import net.weaver.api.event.events.ServerTickEvent;
import net.weaver.api.event.events.TickEvent;
import net.weaver.api.registry.RegistryBackend;
import net.weaver.api.registry.RegistryKey;
import net.weaver.api.registry.RegistryManager;
import net.weaver.api.registry.RegistryView;
import net.weaver.core.ModJar;
import net.weaver.core.event.AutoSubscriberScanner;
import net.weaver.core.hook.GameHooks;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PlatformBenchmark
{
    private static final int REGISTRY_ENTRIES = 256;
    private static final int WARMUP_OPS = 200_000;
    private static final int MEASURED_OPS = 1_000_000;
    private static final int SCAN_WARMUPS = 3;
    private static final int SCAN_MEASURED = 20;
    private static final int MODJAR_WARMUPS = 3;
    private static final int MODJAR_MEASURED = 20;

    private static final long REGISTRY_BUDGET_NS = 2_000L;
    private static final long HOOK_BUDGET_NS = 5_000L;
    private static final long SCAN_BUDGET_NS = 2_000_000_000L;
    private static final long SCAN_COLD_BUDGET_NS = 10_000_000_000L;

    private static final String REGISTRY_ID = "weaver_bench:main";
    private static final String PRESENT_ID = "weaver_bench:entry_007";
    private static final String MISSING_ID = "weaver_bench:missing";

    private static volatile long blackhole;
    private static boolean budgetOk = true;

    private PlatformBenchmark()
    {
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length != 1)
        {
            throw new IllegalArgumentException("usage: PlatformBenchmark <fixture.jar>");
        }
        printEnvironment();
        benchmarkRegistry();
        benchmarkHooks();
        benchmarkModJar(Path.of(args[0]));
        benchmarkScanning(Path.of(args[0]));
        System.out.println("PLATFORM_BENCH BUDGET ok=" + budgetOk);
        if (!budgetOk)
        {
            System.exit(1);
        }
    }

    private static void printEnvironment()
    {
        System.out.printf(Locale.ROOT,
                "PLATFORM_BENCH machine java=%s vm=%s os=%s arch=%s cpus=%d max_heap_mb=%d%n",
                System.getProperty("java.version"), System.getProperty("java.vm.name"),
                System.getProperty("os.name"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / (1024L * 1024L));
    }

    private static void benchmarkRegistry()
    {
        FakeRegistryBackend backend = new FakeRegistryBackend(REGISTRY_ENTRIES);
        RegistryManager.install(backend, message ->
        {
        });
        try
        {
            RegistryView<Object> view = RegistryManager.registry(RegistryKey.parse(REGISTRY_ID));
            RegistryKey<Object> present = RegistryKey.parse(PRESENT_ID);
            RegistryKey<Object> missing = RegistryKey.parse(MISSING_ID);
            if (view.get(present) == null || view.get(missing) != null)
            {
                throw new IllegalStateException("fake registry did not produce the expected entries");
            }
            measureKeyParse();
            measureRegistryGet(view, present);
            measureRegistryGetString(view);
            measureRegistryContains(view, present);
            measureRegistryKeySet(view);
            measureRegistryValues(view);
            measureRegistrySize(view);
            measureRegistryMiss(view, missing);
        }
        finally
        {
            RegistryManager.reset();
        }
    }

    private static void measureKeyParse()
    {
        long sink = 0;
        for (int op = 0; op < WARMUP_OPS; op++)
        {
            sink += RegistryKey.parse(PRESENT_ID).path().length();
        }
        long started = System.nanoTime();
        for (int op = 0; op < MEASURED_OPS; op++)
        {
            sink += RegistryKey.parse(PRESENT_ID).path().length();
        }
        long elapsed = System.nanoTime() - started;
        blackhole += sink;
        reportOps("registry_key_parse", MEASURED_OPS, elapsed);
        checkBudget("registry_key_parse", (double) elapsed / MEASURED_OPS, REGISTRY_BUDGET_NS);
    }

    private static void measureRegistryGet(RegistryView<Object> view, RegistryKey<Object> key)
    {
        long sink = 0;
        for (int op = 0; op < WARMUP_OPS; op++)
        {
            if (view.get(key) != null)
            {
                sink++;
            }
        }
        long started = System.nanoTime();
        for (int op = 0; op < MEASURED_OPS; op++)
        {
            if (view.get(key) != null)
            {
                sink++;
            }
        }
        long elapsed = System.nanoTime() - started;
        blackhole += sink;
        reportOps("registry_get", MEASURED_OPS, elapsed);
        checkBudget("registry_get", (double) elapsed / MEASURED_OPS, REGISTRY_BUDGET_NS);
    }

    private static void measureRegistryGetString(RegistryView<Object> view)
    {
        long sink = 0;
        for (int op = 0; op < WARMUP_OPS; op++)
        {
            if (view.get(PRESENT_ID) != null)
            {
                sink++;
            }
        }
        long started = System.nanoTime();
        for (int op = 0; op < MEASURED_OPS; op++)
        {
            if (view.get(PRESENT_ID) != null)
            {
                sink++;
            }
        }
        long elapsed = System.nanoTime() - started;
        blackhole += sink;
        reportOps("registry_get_string", MEASURED_OPS, elapsed);
        checkBudget("registry_get_string", (double) elapsed / MEASURED_OPS, REGISTRY_BUDGET_NS);
    }

    private static void measureRegistryContains(RegistryView<Object> view, RegistryKey<Object> key)
    {
        long sink = 0;
        for (int op = 0; op < WARMUP_OPS; op++)
        {
            if (view.containsKey(key))
            {
                sink++;
            }
        }
        long started = System.nanoTime();
        for (int op = 0; op < MEASURED_OPS; op++)
        {
            if (view.containsKey(key))
            {
                sink++;
            }
        }
        long elapsed = System.nanoTime() - started;
        blackhole += sink;
        reportOps("registry_contains", MEASURED_OPS, elapsed);
        checkBudget("registry_contains", (double) elapsed / MEASURED_OPS, REGISTRY_BUDGET_NS);
    }

    private static void measureRegistryKeySet(RegistryView<Object> view)
    {
        long sink = 0;
        for (int op = 0; op < WARMUP_OPS; op++)
        {
            sink += view.keySet().size();
        }
        long started = System.nanoTime();
        for (int op = 0; op < MEASURED_OPS; op++)
        {
            sink += view.keySet().size();
        }
        long elapsed = System.nanoTime() - started;
        blackhole += sink;
        reportOps("registry_keySet", MEASURED_OPS, elapsed);
        checkBudget("registry_keySet", (double) elapsed / MEASURED_OPS, REGISTRY_BUDGET_NS);
    }

    private static void measureRegistryValues(RegistryView<Object> view)
    {
        long sink = 0;
        for (int op = 0; op < WARMUP_OPS; op++)
        {
            sink += view.values().size();
        }
        long started = System.nanoTime();
        for (int op = 0; op < MEASURED_OPS; op++)
        {
            sink += view.values().size();
        }
        long elapsed = System.nanoTime() - started;
        blackhole += sink;
        reportOps("registry_values", MEASURED_OPS, elapsed);
        checkBudget("registry_values", (double) elapsed / MEASURED_OPS, REGISTRY_BUDGET_NS);
    }

    private static void measureRegistrySize(RegistryView<Object> view)
    {
        long sink = 0;
        for (int op = 0; op < WARMUP_OPS; op++)
        {
            sink += view.size();
        }
        long started = System.nanoTime();
        for (int op = 0; op < MEASURED_OPS; op++)
        {
            sink += view.size();
        }
        long elapsed = System.nanoTime() - started;
        blackhole += sink;
        reportOps("registry_size", MEASURED_OPS, elapsed);
        checkBudget("registry_size", (double) elapsed / MEASURED_OPS, REGISTRY_BUDGET_NS);
    }

    private static void measureRegistryMiss(RegistryView<Object> view, RegistryKey<Object> missing)
    {
        long sink = 0;
        for (int op = 0; op < WARMUP_OPS; op++)
        {
            if (view.get(missing) == null)
            {
                sink++;
            }
        }
        long started = System.nanoTime();
        for (int op = 0; op < MEASURED_OPS; op++)
        {
            if (view.get(missing) == null)
            {
                sink++;
            }
        }
        long elapsed = System.nanoTime() - started;
        blackhole += sink;
        reportOps("registry_get_missing", MEASURED_OPS, elapsed);
        checkBudget("registry_get_missing", (double) elapsed / MEASURED_OPS, REGISTRY_BUDGET_NS);
    }

    private static void benchmarkHooks()
    {
        CountingListener tickListener = new CountingListener();
        CountingListener levelListener = new CountingListener();
        CountingListener playerListener = new CountingListener();
        ListenerHandle tickHandle = EventBus.GAME.addListener(TickEvent.class, tickListener);
        ListenerHandle levelHandle = EventBus.GAME.addListener(LevelTickEvent.class, levelListener);
        ListenerHandle playerHandle = EventBus.GAME.addListener(PlayerTickEvent.class, playerListener);
        try
        {
            measureDirectTickDispatch();
            measureGameHookTick(tickListener);
            measureGameHookLevel(levelListener);
            measureGameHookPlayer(playerListener);
        }
        finally
        {
            EventBus.GAME.unregister(tickHandle);
            EventBus.GAME.unregister(levelHandle);
            EventBus.GAME.unregister(playerHandle);
        }
    }

    private static void measureDirectTickDispatch()
    {
        ServerTickEvent.Pre event = new ServerTickEvent.Pre(1L);
        for (int op = 0; op < WARMUP_OPS; op++)
        {
            EventBus.GAME.post(event);
        }
        EventBus.GAME.resetStats();
        long started = System.nanoTime();
        for (int op = 0; op < MEASURED_OPS; op++)
        {
            EventBus.GAME.post(event);
        }
        long elapsed = System.nanoTime() - started;
        reportPosts("hook_dispatch", MEASURED_OPS, elapsed);
        checkBudget("hook_dispatch", (double) elapsed / MEASURED_OPS, HOOK_BUDGET_NS);
    }

    private static void measureGameHookTick(CountingListener listener)
    {
        for (int op = 0; op < WARMUP_OPS; op++)
        {
            GameHooks.serverTickEnter();
        }
        EventBus.GAME.resetStats();
        long started = System.nanoTime();
        for (int op = 0; op < MEASURED_OPS; op++)
        {
            GameHooks.serverTickEnter();
        }
        long elapsed = System.nanoTime() - started;
        blackhole += listener.calls;
        reportPosts("hook_gamelifecycle", MEASURED_OPS, elapsed);
        checkBudget("hook_gamelifecycle", (double) elapsed / MEASURED_OPS, HOOK_BUDGET_NS);
    }

    private static void measureGameHookLevel(CountingListener listener)
    {
        Object level = new Object();
        for (int op = 0; op < WARMUP_OPS; op++)
        {
            GameHooks.serverLevelTickEnter(level);
        }
        EventBus.GAME.resetStats();
        long started = System.nanoTime();
        for (int op = 0; op < MEASURED_OPS; op++)
        {
            GameHooks.serverLevelTickEnter(level);
        }
        long elapsed = System.nanoTime() - started;
        blackhole += listener.calls;
        reportPosts("hook_level_dispatch", MEASURED_OPS, elapsed);
        checkBudget("hook_level_dispatch", (double) elapsed / MEASURED_OPS, HOOK_BUDGET_NS);
    }

    private static void measureGameHookPlayer(CountingListener listener)
    {
        Object player = new Object();
        for (int op = 0; op < WARMUP_OPS; op++)
        {
            GameHooks.playerTickEnter(player);
        }
        EventBus.GAME.resetStats();
        long started = System.nanoTime();
        for (int op = 0; op < MEASURED_OPS; op++)
        {
            GameHooks.playerTickEnter(player);
        }
        long elapsed = System.nanoTime() - started;
        blackhole += listener.calls;
        reportPosts("hook_player_dispatch", MEASURED_OPS, elapsed);
        checkBudget("hook_player_dispatch", (double) elapsed / MEASURED_OPS, HOOK_BUDGET_NS);
    }

    private static void benchmarkModJar(Path fixture) throws Exception
    {
        long sink = 0;
        for (int read = 0; read < MODJAR_WARMUPS; read++)
        {
            try (ModJar mod = ModJar.read(fixture))
            {
                sink += mod.classEntries().size();
            }
        }
        long started = System.nanoTime();
        for (int read = 0; read < MODJAR_MEASURED; read++)
        {
            try (ModJar mod = ModJar.read(fixture))
            {
                sink += mod.classEntries().size();
            }
        }
        long elapsed = System.nanoTime() - started;
        blackhole += sink;
        double perRead = (double) elapsed / MODJAR_MEASURED;
        reportScans("modjar_read", MODJAR_MEASURED, elapsed);
        checkBudget("modjar_read", perRead, SCAN_BUDGET_NS);
    }

    private static void benchmarkScanning(Path fixture) throws Exception
    {
        try (ModJar mod = ModJar.read(fixture);
             URLClassLoader loader = new URLClassLoader(new URL[]{fixture.toUri().toURL()},
                     PlatformBenchmark.class.getClassLoader()))
        {
            long coldStarted = System.nanoTime();
            int expected = scanOnce(mod, loader, 0);
            long coldElapsed = System.nanoTime() - coldStarted;
            if (expected <= 0)
            {
                throw new IllegalStateException("fixture jar produced no subscriber registrations");
            }
            reportScans("scan_cold", 1, coldElapsed);
            checkBudget("scan_cold", coldElapsed, SCAN_COLD_BUDGET_NS);

            for (int scan = 0; scan < SCAN_WARMUPS; scan++)
            {
                int count = scanOnce(mod, loader, scan + 1);
                if (count != expected)
                {
                    throw new IllegalStateException("scan count changed: " + count + " != " + expected);
                }
            }
            long started = System.nanoTime();
            for (int scan = 0; scan < SCAN_MEASURED; scan++)
            {
                int count = scanOnce(mod, loader, scan + SCAN_WARMUPS + 1);
                if (count != expected)
                {
                    throw new IllegalStateException("scan count changed: " + count + " != " + expected);
                }
            }
            long elapsed = System.nanoTime() - started;
            double perScan = (double) elapsed / SCAN_MEASURED;
            reportScans("scan", SCAN_MEASURED, elapsed);
            checkBudget("scan", perScan, SCAN_BUDGET_NS);
            System.out.println("PLATFORM_BENCH scan_subscribers=" + expected);
        }
    }

    private static int scanOnce(ModJar mod, ClassLoader loader, int index)
    {
        EventBus localBus = EventBus.local("bench-scan-" + index);
        return AutoSubscriberScanner.scan(mod, "both", true, true, loader, message ->
        {
        }, null, localBus);
    }

    private static void reportOps(String name, int operations, long elapsed)
    {
        System.out.printf(Locale.ROOT,
                "PLATFORM_BENCH %s ops=%d ns_per_op=%.2f total_ms=%.2f%n",
                name, operations, (double) elapsed / operations, elapsed / 1_000_000.0);
    }

    private static void reportPosts(String name, int posts, long elapsed)
    {
        System.out.printf(Locale.ROOT,
                "PLATFORM_BENCH %s posts=%d ns_per_post=%.2f total_ms=%.2f%n",
                name, posts, (double) elapsed / posts, elapsed / 1_000_000.0);
    }

    private static void reportScans(String name, int scans, long elapsed)
    {
        System.out.printf(Locale.ROOT,
                "PLATFORM_BENCH %s scans=%d ns_per_scan=%.2f total_ms=%.2f%n",
                name, scans, (double) elapsed / scans, elapsed / 1_000_000.0);
    }

    private static void checkBudget(String name, double actualNanos, long budgetNanos)
    {
        boolean ok = actualNanos <= budgetNanos;
        if (!ok)
        {
            budgetOk = false;
        }
        System.out.printf(Locale.ROOT,
                "PLATFORM_BENCH BUDGET name=%s actual_ns=%.2f budget_ns=%d ok=%s%n",
                name, actualNanos, budgetNanos, ok);
    }

    private static final class CountingListener implements EventListener<Event>
    {
        private long calls;

        @Override
        public void invoke(Event event)
        {
            calls++;
        }
    }

    private static final class FakeRegistryBackend implements RegistryBackend
    {
        private final Map<String, Object> valuesById = new LinkedHashMap<>();
        private final Object nativeRegistry = new Object();

        private FakeRegistryBackend(int entries)
        {
            for (int index = 0; index < entries; index++)
            {
                valuesById.put("weaver_bench:entry_" + String.format(Locale.ROOT, "%03d", index),
                        "value-" + index);
            }
        }

        @Override
        public boolean isAvailable()
        {
            return true;
        }

        @Override
        public Set<String> registryIds()
        {
            return Set.of(REGISTRY_ID);
        }

        @Override
        public Object nativeRegistry(String registryId)
        {
            return REGISTRY_ID.equals(registryId) ? nativeRegistry : null;
        }

        @Override
        public Object register(Object nativeRegistry, String namespace, String path, Object value)
        {
            valuesById.put(namespace + ":" + path, value);
            return value;
        }

        @Override
        public Object get(Object nativeRegistry, String namespace, String path)
        {
            return valuesById.get(namespace + ":" + path);
        }

        @Override
        public boolean containsKey(Object nativeRegistry, String namespace, String path)
        {
            return valuesById.containsKey(namespace + ":" + path);
        }

        @Override
        public List<String> keys(Object nativeRegistry)
        {
            return List.copyOf(valuesById.keySet());
        }

        @Override
        public List<Map.Entry<String, Object>> entries(Object nativeRegistry)
        {
            return List.copyOf(valuesById.entrySet());
        }

        @Override
        public Object holder(Object nativeRegistry, Object value)
        {
            return value;
        }

        @Override
        public Object identifier(String namespace, String path)
        {
            return namespace + ":" + path;
        }
    }
}
