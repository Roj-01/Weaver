package net.weaver.core.event;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.events.ModLifecycleEvent;
import net.weaver.api.registry.RegistryBackend;
import net.weaver.api.registry.RegistryManager;
import net.weaver.core.LifecycleHooks;
import net.weaver.core.ModJar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleHooksTest {

    @TempDir
    Path tempDir;

    private ModJar mod;
    private ModJar secondMod;

    @BeforeEach
    void setUp() {
        LifecycleHooks.reset();
        LifecycleOrderFixtureMod.reset();
        LifecycleOrderFixtureSubscriber.reset();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (LifecycleOrderFixtureMod.last != null) {
            EventBus.GAME.unregister(LifecycleOrderFixtureMod.last);
        }
        if (LifecycleOrderFixtureSubscriber.last != null) {
            EventBus.GAME.unregister(LifecycleOrderFixtureSubscriber.last);
        }
        if (mod != null) {
            mod.close();
            mod = null;
        }
        if (secondMod != null) {
            secondMod.close();
            secondMod = null;
        }
        LifecycleHooks.reset();
        LifecycleOrderFixtureMod.reset();
        LifecycleOrderFixtureSubscriber.reset();
    }

    @Test
    void scansSubscribersAfterModInitializationAndSkipsEntrypoint() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("ordering-test",
                LifecycleOrderFixtureMod.class.getName(),
                LifecycleOrderFixtureMod.class,
                LifecycleOrderFixtureSubscriber.class));
        LifecycleHooks.install(List.of(mod), loader(), "server", false,
                tempDir.resolve("config"), tempDir.resolve("data"), log::add);

        LifecycleHooks.onInitialize();

        assertEquals(1, LifecycleOrderFixtureMod.INIT_CALLS.get(), () -> "log: " + log);
        assertTrue(LifecycleOrderFixtureMod.initialized);
        assertEquals(1, LifecycleHooks.modCount());
        assertEquals(1, LifecycleHooks.mods().size());
        assertEquals(1, LifecycleOrderFixtureSubscriber.CONSTRUCTED_AFTER_INIT.get(),
                () -> "subscriber constructed before mod init: " + log);

        EventBus.GAME.post(new ScanEvent());

        assertEquals(1, LifecycleOrderFixtureMod.HITS.get(),
                () -> "entrypoint registered more than once: " + log);
        assertEquals(1, LifecycleOrderFixtureSubscriber.HITS.get(), () -> "log: " + log);
        assertTrue(log.stream().anyMatch(line -> line.contains("MOD INIT id=ordering-test")),
                () -> "missing init log: " + log);
    }

    @Test
    void lifecycleEventsCarryThePhaseLogicalSide() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("side-test",
                LifecycleOrderFixtureMod.class.getName(), LifecycleOrderFixtureMod.class));
        List<ModLifecycleEvent> events = new ArrayList<>();
        ListenerHandle handle = EventBus.MOD.addListener(ModLifecycleEvent.class, events::add);
        try {
            LifecycleHooks.install(List.of(mod), loader(), "client", true,
                    tempDir.resolve("config"), tempDir.resolve("data"), log::add);
            RegistryManager.install(new FakeRegistryBackend(Set.of("minecraft:block")), log::add);

            LifecycleHooks.onInitialize();
            LifecycleHooks.onRegister();
            LifecycleHooks.onCommonSetup();
            LifecycleHooks.onClientSetup();
            LifecycleHooks.onServerSetup();
            LifecycleHooks.onClientTick();
            LifecycleHooks.onServerTick();
        } finally {
            handle.unregister();
        }

        assertEquals(LogicalSide.BOTH,
                logicalSideOf(events, ModLifecycleEvent.Phase.INITIALIZE), () -> "log: " + log);
        assertEquals(LogicalSide.BOTH,
                logicalSideOf(events, ModLifecycleEvent.Phase.REGISTER), () -> "log: " + log);
        assertEquals(LogicalSide.BOTH,
                logicalSideOf(events, ModLifecycleEvent.Phase.COMMON_SETUP), () -> "log: " + log);
        assertEquals(LogicalSide.CLIENT,
                logicalSideOf(events, ModLifecycleEvent.Phase.CLIENT_SETUP), () -> "log: " + log);
        assertEquals(LogicalSide.SERVER,
                logicalSideOf(events, ModLifecycleEvent.Phase.SERVER_SETUP), () -> "log: " + log);
        assertEquals(LogicalSide.BOTH,
                logicalSideOf(events, ModLifecycleEvent.Phase.LOAD_COMPLETE), () -> "log: " + log);
        assertEquals(LogicalSide.CLIENT,
                eventOf(events, ModLifecycleEvent.Phase.CLIENT_SETUP).context().logicalSide());
        assertEquals(LogicalSide.SERVER,
                eventOf(events, ModLifecycleEvent.Phase.SERVER_SETUP).context().logicalSide());

        LifecycleHooks.LoadedMod loaded = LifecycleHooks.mods().get(0);
        for (ModLifecycleEvent event : events) {
            assertSame(loaded.localBus(), event.getBus(),
                    () -> "event did not carry the local bus: " + event.phase());
        }
    }

    @Test
    void localBusKeepsLifecycleEventsScopedToOneMod() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("mod-a",
                LocalBusFixtureMod.class.getName(), LocalBusFixtureMod.class));
        secondMod = ModJar.read(TestJars.create("mod-b",
                LocalBusFixtureMod.class.getName(), LocalBusFixtureMod.class));
        LifecycleHooks.install(List.of(mod, secondMod), loader(), "client", true,
                tempDir.resolve("config"), tempDir.resolve("data"), log::add);
        LifecycleHooks.onInitialize();

        LifecycleHooks.LoadedMod a = loadedMod("mod-a");
        LifecycleHooks.LoadedMod b = loadedMod("mod-b");
        List<ModLifecycleEvent> localA = new ArrayList<>();
        List<ModLifecycleEvent> localB = new ArrayList<>();
        List<ModLifecycleEvent> modEvents = new ArrayList<>();
        List<ModLifecycleEvent> gameEvents = new ArrayList<>();
        ListenerHandle localHandleA = a.localBus().addListener(ModLifecycleEvent.class, localA::add);
        ListenerHandle localHandleB = b.localBus().addListener(ModLifecycleEvent.class, localB::add);
        ListenerHandle modHandle = EventBus.MOD.addListener(ModLifecycleEvent.class, modEvents::add);
        ListenerHandle gameHandle = EventBus.GAME.addListener(ModLifecycleEvent.class, gameEvents::add);
        try {
            LifecycleHooks.onRegister();
        } finally {
            localHandleA.unregister();
            localHandleB.unregister();
            modHandle.unregister();
            gameHandle.unregister();
        }

        assertEquals(List.of("mod-a"), modIds(localA), () -> "log: " + log);
        assertEquals(List.of("mod-b"), modIds(localB), () -> "log: " + log);
        assertEquals(List.of("mod-a", "mod-b"), modIds(modEvents), () -> "log: " + log);
        assertEquals(List.of("mod-a", "mod-b"), modIds(gameEvents), () -> "log: " + log);
        assertSame(a.localBus(), localA.get(0).getBus());
        assertSame(b.localBus(), localB.get(0).getBus());
        assertEquals("mod:mod-a", a.localBus().name());
        assertEquals("mod:mod-b", b.localBus().name());
        assertEquals(EventBus.MOD, a.localBus().parent());
        assertNotSame(a.localBus(), b.localBus());
    }

    @Test
    void sideContextsShareTheModLocalBus() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("context-bus",
                LocalBusFixtureMod.class.getName(), LocalBusFixtureMod.class));
        LifecycleHooks.install(List.of(mod), loader(), "client", true,
                tempDir.resolve("config"), tempDir.resolve("data"), log::add);
        LifecycleHooks.onInitialize();

        LifecycleHooks.LoadedMod loaded = loadedMod("context-bus");
        assertSame(loaded.localBus(), loaded.context().modEvents());
        assertSame(loaded.localBus(), loaded.context(LogicalSide.CLIENT).modEvents());
        assertSame(loaded.localBus(), loaded.context(LogicalSide.SERVER).modEvents());
        assertSame(EventBus.MOD, loaded.context().globalModEvents());
        assertSame(EventBus.MOD, loaded.context(LogicalSide.CLIENT).globalModEvents());
        assertSame(EventBus.MOD, loaded.context(LogicalSide.SERVER).globalModEvents());
        assertNotSame(loaded.context(), loaded.context(LogicalSide.CLIENT));
        assertNotSame(loaded.localBus(), EventBus.MOD);
    }

    @Test
    void exposedContextsAreSideSpecific() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("context-test",
                LifecycleOrderFixtureMod.class.getName(), LifecycleOrderFixtureMod.class));
        LifecycleHooks.install(List.of(mod), loader(), "client", true,
                tempDir.resolve("config"), tempDir.resolve("data"), log::add);

        LifecycleHooks.onInitialize();

        LifecycleHooks.LoadedMod loaded = LifecycleHooks.mods().get(0);
        assertEquals(LogicalSide.BOTH, loaded.context().logicalSide());
        assertEquals(LogicalSide.CLIENT, loaded.context(LogicalSide.CLIENT).logicalSide());
        assertEquals(LogicalSide.SERVER, loaded.context(LogicalSide.SERVER).logicalSide());
        assertEquals(loaded.id(), loaded.context(LogicalSide.CLIENT).id());
        assertEquals(loaded.context().configDir(),
                loaded.context(LogicalSide.CLIENT).configDir());
        assertTrue(loaded.context(LogicalSide.CLIENT).isLogicalClient());
        assertFalse(loaded.context(LogicalSide.CLIENT).isLogicalServer());
        assertTrue(loaded.context(LogicalSide.SERVER).isLogicalServer());
        assertFalse(loaded.context(LogicalSide.SERVER).isLogicalClient());
    }

    @Test
    void endRegistrationRunsAfterSuccessfulFreeze() {
        List<String> log = new ArrayList<>();
        installNoMods(log);
        FakeRegistryBackend backend = new FakeRegistryBackend(Set.of("minecraft:block"));
        RegistryManager.install(backend, log::add);

        LifecycleHooks.onRegister();

        assertFalse(RegistryManager.isRegistrationPhase());
        assertEquals(1, backend.frozen.get());
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);
    }

    @Test
    void endRegistrationRunsWhenFreezeThrows() {
        List<String> log = new ArrayList<>();
        installNoMods(log);
        RegistryManager.install(new FakeRegistryBackend(Set.of("")), log::add);

        LifecycleHooks.onRegister();

        assertFalse(RegistryManager.isRegistrationPhase());
        assertTrue(log.stream().anyMatch(line -> line.contains("REGISTRY FREEZE FAILED")),
                () -> "missing freeze failure log: " + log);
    }

    private void installNoMods(List<String> log) {
        LifecycleHooks.install(List.of(), loader(), "server", false,
                tempDir.resolve("config"), tempDir.resolve("data"), log::add);
    }

    private static LogicalSide logicalSideOf(List<ModLifecycleEvent> events,
                                             ModLifecycleEvent.Phase phase) {
        return eventOf(events, phase).logicalSide();
    }

    private static ModLifecycleEvent eventOf(List<ModLifecycleEvent> events,
                                             ModLifecycleEvent.Phase phase) {
        return events.stream()
                .filter(event -> event.phase() == phase)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing lifecycle event: " + phase));
    }

    private static List<String> modIds(List<ModLifecycleEvent> events) {
        return events.stream().map(ModLifecycleEvent::modId).toList();
    }

    private static LifecycleHooks.LoadedMod loadedMod(String id) {
        return LifecycleHooks.mods().stream()
                .filter(loaded -> loaded.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing loaded mod: " + id));
    }

    private ClassLoader loader() {
        return getClass().getClassLoader();
    }

    private static final class FakeRegistryBackend implements RegistryBackend {

        private final Set<String> ids;
        private final AtomicInteger frozen = new AtomicInteger();

        private FakeRegistryBackend(Set<String> ids) {
            this.ids = ids;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public Set<String> registryIds() {
            return ids;
        }

        @Override
        public Object nativeRegistry(String registryId) {
            return new Object();
        }

        @Override
        public Object register(Object nativeRegistry, String namespace, String path, Object value) {
            return value;
        }

        @Override
        public Object get(Object nativeRegistry, String namespace, String path) {
            return null;
        }

        @Override
        public boolean containsKey(Object nativeRegistry, String namespace, String path) {
            return false;
        }

        @Override
        public List<String> keys(Object nativeRegistry) {
            return List.of();
        }

        @Override
        public List<Map.Entry<String, Object>> entries(Object nativeRegistry) {
            return List.of();
        }

        @Override
        public Object holder(Object nativeRegistry, Object value) {
            return value;
        }

        @Override
        public Object identifier(String namespace, String path) {
            return namespace + ":" + path;
        }

        @Override
        public void markFrozen(Object nativeRegistry) {
            frozen.incrementAndGet();
        }
    }
}
