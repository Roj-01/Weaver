package net.weaver.core.event;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.LogicalSide;
import net.weaver.core.ModJar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoSubscriberScannerTest {

    private ModJar mod;
    private EventBus localBus;

    @AfterEach
    void tearDown() throws Exception {
        EventBus.GAME.unregister(ScanFixtureSubscriber.class);
        EventBus.GAME.unregister(ServerSideScanFixtureSubscriber.class);
        EventBus.GAME.unregister(ClientSideScanFixtureSubscriber.class);
        EventBus.GAME.unregister(BothSideScanFixtureSubscriber.class);
        EventBus.GAME.unregister(BothSideLogicalServerSubscriber.class);
        EventBus.GAME.unregister(BothSideLogicalClientSubscriber.class);
        EventBus.GAME.unregister(MatchingModIdScanFixtureSubscriber.class);
        EventBus.GAME.unregister(MismatchedModIdScanFixtureSubscriber.class);
        EventBus.GAME.unregister(AfterBrokenScanFixtureSubscriber.class);
        if (InstanceScanFixtureSubscriber.last != null) {
            EventBus.GAME.unregister(InstanceScanFixtureSubscriber.last);
        }
        if (MixedScanFixtureSubscriber.last != null) {
            EventBus.GAME.unregister(MixedScanFixtureSubscriber.last);
        }
        if (ModBusScanFixtureSubscriber.last != null) {
            EventBus.MOD.unregister(ModBusScanFixtureSubscriber.last);
        }
        EventBus.MOD.unregister(GlobalModBusScanFixtureSubscriber.class);
        if (localBus != null) {
            if (ModBusScanFixtureSubscriber.last != null) {
                localBus.unregister(ModBusScanFixtureSubscriber.last);
            }
            localBus.unregister(GlobalModBusScanFixtureSubscriber.class);
            localBus.unregister(ModBusLogicalServerSubscriber.class);
            localBus.unregister(ModBusLogicalBothSubscriber.class);
            localBus = null;
        }
        ScanFixtureSubscriber.HITS.set(0);
        InstanceScanFixtureSubscriber.reset();
        MixedScanFixtureSubscriber.reset();
        ModBusScanFixtureSubscriber.reset();
        ServerSideScanFixtureSubscriber.reset();
        ClientSideScanFixtureSubscriber.reset();
        BothSideScanFixtureSubscriber.reset();
        BothSideLogicalServerSubscriber.reset();
        BothSideLogicalClientSubscriber.reset();
        MatchingModIdScanFixtureSubscriber.reset();
        MismatchedModIdScanFixtureSubscriber.reset();
        AfterBrokenScanFixtureSubscriber.reset();
        GlobalModBusScanFixtureSubscriber.reset();
        ModBusLogicalServerSubscriber.reset();
        ModBusLogicalBothSubscriber.reset();
        if (mod != null) {
            mod.close();
            mod = null;
        }
    }

    @Test
    void scansAnnotatedStaticSubscriberFromModJar() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                ScanFixtureSubscriber.class.getName(), ScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "server", loader(), log::add);

        assertEquals(1, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);
        assertEquals(0, ScanFixtureSubscriber.HITS.get());

        EventBus.GAME.post(new ScanEvent());

        assertEquals(1, ScanFixtureSubscriber.HITS.get());
    }

    @Test
    void instantiatesSubscriberWithInstanceMethods() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                InstanceScanFixtureSubscriber.class.getName(),
                InstanceScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "server", loader(), log::add);

        assertEquals(1, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);
        assertEquals(1, InstanceScanFixtureSubscriber.INSTANCES.get());

        EventBus.GAME.post(new ScanEvent());

        assertEquals(1, InstanceScanFixtureSubscriber.HITS.get());
    }

    @Test
    void registersMixedStaticAndInstanceSubscriber() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                MixedScanFixtureSubscriber.class.getName(), MixedScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "server", loader(), log::add);

        assertEquals(1, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);
        assertEquals(1, MixedScanFixtureSubscriber.INSTANCES.get());

        EventBus.GAME.post(new ScanEvent());

        assertEquals(1, MixedScanFixtureSubscriber.STATIC_HITS.get());
        assertEquals(1, MixedScanFixtureSubscriber.INSTANCE_HITS.get());
    }

    @Test
    void registersModBusSubscriberOnModBusOnly() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                ModBusScanFixtureSubscriber.class.getName(), ModBusScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "server", loader(), log::add);

        assertEquals(1, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);
        assertEquals(1, ModBusScanFixtureSubscriber.INSTANCES.get());

        EventBus.GAME.post(new ScanEvent());
        assertEquals(0, ModBusScanFixtureSubscriber.HITS.get());

        EventBus.MOD.post(new ScanEvent());
        assertEquals(1, ModBusScanFixtureSubscriber.HITS.get());
    }

    @Test
    void registersModBusSubscriberOnProvidedLocalBus() throws Exception {
        List<String> log = new ArrayList<>();
        localBus = EventBus.local("scanner-test");
        mod = ModJar.read(TestJars.create("scanner-test",
                ModBusScanFixtureSubscriber.class.getName(), ModBusScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "server", true, true, loader(), log::add,
                null, localBus);

        assertEquals(1, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);
        assertEquals(1, ModBusScanFixtureSubscriber.INSTANCES.get());

        EventBus.GAME.post(new ScanEvent());
        assertEquals(0, ModBusScanFixtureSubscriber.HITS.get());

        EventBus.MOD.post(new ScanEvent());
        assertEquals(0, ModBusScanFixtureSubscriber.HITS.get());

        localBus.post(new ScanEvent());
        assertEquals(1, ModBusScanFixtureSubscriber.HITS.get());
    }

    @Test
    void registersGlobalModSubscriberOnAggregateModBus() throws Exception {
        List<String> log = new ArrayList<>();
        localBus = EventBus.local("scanner-test");
        mod = ModJar.read(TestJars.create("scanner-test",
                GlobalModBusScanFixtureSubscriber.class.getName(),
                GlobalModBusScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "server", true, true, loader(), log::add,
                null, localBus);

        assertEquals(1, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);

        EventBus.GAME.post(new ScanEvent());
        assertEquals(0, GlobalModBusScanFixtureSubscriber.HITS.get());

        EventBus.MOD.post(new ScanEvent());
        assertEquals(1, GlobalModBusScanFixtureSubscriber.HITS.get());
    }

    @Test
    void registersGameSubscriberOnGameBus() throws Exception {
        List<String> log = new ArrayList<>();
        localBus = EventBus.local("scanner-test");
        mod = ModJar.read(TestJars.create("scanner-test",
                ScanFixtureSubscriber.class.getName(), ScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "server", true, true, loader(), log::add,
                null, localBus);

        assertEquals(1, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);

        EventBus.GAME.post(new ScanEvent());
        assertEquals(1, ScanFixtureSubscriber.HITS.get());
    }

    @Test
    void appliesStrictLogicalSideFilterOnLocalBus() throws Exception {
        List<String> log = new ArrayList<>();
        localBus = EventBus.local("scanner-test");
        mod = ModJar.read(TestJars.create("scanner-test",
                ModBusLogicalServerSubscriber.class.getName(),
                ModBusLogicalServerSubscriber.class,
                ModBusLogicalBothSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "client", true, true, loader(), log::add,
                null, localBus);

        assertEquals(2, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);

        localBus.post(new ScanEvent().setLogicalSide(LogicalSide.SERVER));
        assertEquals(1, ModBusLogicalServerSubscriber.HITS.get());
        assertEquals(1, ModBusLogicalBothSubscriber.HITS.get());

        localBus.post(new ScanEvent().setLogicalSide(LogicalSide.CLIENT));
        assertEquals(1, ModBusLogicalServerSubscriber.HITS.get());
        assertEquals(2, ModBusLogicalBothSubscriber.HITS.get());

        localBus.post(new ScanEvent().setLogicalSide(LogicalSide.BOTH));
        assertEquals(2, ModBusLogicalServerSubscriber.HITS.get(),
                "BOTH events match every side filter");
        assertEquals(3, ModBusLogicalBothSubscriber.HITS.get());
    }

    @Test
    void skipsServerOnlySubscriberOnClient() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                ServerSideScanFixtureSubscriber.class.getName(),
                ServerSideScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "client", loader(), log::add);

        assertEquals(0, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);

        EventBus.GAME.post(new ScanEvent());
        assertEquals(0, ServerSideScanFixtureSubscriber.HITS.get());
    }

    @Test
    void skipsClientOnlySubscriberOnServer() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                ClientSideScanFixtureSubscriber.class.getName(),
                ClientSideScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "server", loader(), log::add);

        assertEquals(0, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);

        EventBus.GAME.post(new ScanEvent());
        assertEquals(0, ClientSideScanFixtureSubscriber.HITS.get());
    }

    @Test
    void registersBothSideSubscriberOnClientAndServer() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                BothSideScanFixtureSubscriber.class.getName(), BothSideScanFixtureSubscriber.class));

        assertEquals(1, AutoSubscriberScanner.scan(mod, "client", loader(), log::add));
        EventBus.GAME.post(new ScanEvent());
        assertEquals(1, BothSideScanFixtureSubscriber.HITS.get());

        EventBus.GAME.unregister(BothSideScanFixtureSubscriber.class);
        BothSideScanFixtureSubscriber.HITS.set(0);

        assertEquals(1, AutoSubscriberScanner.scan(mod, "server", loader(), log::add));
        EventBus.GAME.post(new ScanEvent());
        assertEquals(1, BothSideScanFixtureSubscriber.HITS.get());
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);
    }

    @Test
    void loadsLogicalServerSubscriberWhenBothSidesAreAvailable() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                BothSideLogicalServerSubscriber.class.getName(),
                BothSideLogicalServerSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "client", true, true, loader(), log::add, null);

        assertEquals(1, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);

        EventBus.GAME.post(new ScanEvent());
        assertEquals(1, BothSideLogicalServerSubscriber.HITS.get());
    }

    @Test
    void skipsLogicalServerSubscriberWhenServerIsUnavailable() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                BothSideLogicalServerSubscriber.class.getName(),
                BothSideLogicalServerSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "client", true, false, loader(), log::add, null);

        assertEquals(0, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);

        EventBus.GAME.post(new ScanEvent());
        assertEquals(0, BothSideLogicalServerSubscriber.HITS.get());
    }

    @Test
    void loadsLogicalClientSubscriberWhenClientIsAvailable() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                BothSideLogicalClientSubscriber.class.getName(),
                BothSideLogicalClientSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "client", true, true, loader(), log::add, null);

        assertEquals(1, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);

        EventBus.GAME.post(new ScanEvent());
        assertEquals(1, BothSideLogicalClientSubscriber.HITS.get());
    }

    @Test
    void skipsLogicalClientSubscriberOnDedicatedServer() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                BothSideLogicalClientSubscriber.class.getName(),
                BothSideLogicalClientSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "server", false, true, loader(), log::add, null);

        assertEquals(0, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);

        EventBus.GAME.post(new ScanEvent());
        assertEquals(0, BothSideLogicalClientSubscriber.HITS.get());
    }

    @Test
    void newOverloadStillSkipsPhysicalServerSubscriberOnClientProcess() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                ServerSideScanFixtureSubscriber.class.getName(),
                ServerSideScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "client", true, true, loader(), log::add, null);

        assertEquals(0, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);
    }

    @Test
    void legacyOverloadsUsePhysicalSideForLogicalAvailability() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                BothSideLogicalServerSubscriber.class.getName(),
                BothSideLogicalServerSubscriber.class,
                BothSideLogicalClientSubscriber.class));

        assertEquals(1, AutoSubscriberScanner.scan(mod, "client", loader(), log::add,
                null));
        assertEquals(1, AutoSubscriberScanner.scan(mod, "server", loader(), log::add,
                null));

        EventBus.GAME.post(new ScanEvent());
        assertEquals(1, BothSideLogicalServerSubscriber.HITS.get());
        assertEquals(1, BothSideLogicalClientSubscriber.HITS.get());
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);
    }

    @Test
    void filtersSubscribersByModId() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                MatchingModIdScanFixtureSubscriber.class.getName(),
                MatchingModIdScanFixtureSubscriber.class,
                MismatchedModIdScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "server", loader(), log::add);

        assertEquals(1, count);
        assertTrue(log.isEmpty(), () -> "unexpected log: " + log);

        EventBus.GAME.post(new ScanEvent());

        assertEquals(1, MatchingModIdScanFixtureSubscriber.HITS.get());
        assertEquals(0, MismatchedModIdScanFixtureSubscriber.HITS.get());
    }

    @Test
    void skipsEntrypointClassWhenProvided() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                ScanFixtureSubscriber.class.getName(), ScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "server", loader(), log::add,
                ScanFixtureSubscriber.class);

        assertEquals(0, count);
        long skips = log.stream()
                .filter(line -> line.equals("[weaver] SUBSCRIBER ENTRYPOINT SKIPPED class="
                        + ScanFixtureSubscriber.class.getName()
                        + " reason=already-registered-on-game-bus"))
                .count();
        assertEquals(1, skips, () -> "missing or duplicated entrypoint skip log: " + log);

        EventBus.GAME.post(new ScanEvent());
        assertEquals(0, ScanFixtureSubscriber.HITS.get());
    }

    @Test
    void brokenSubscriberDoesNotStopScan() throws Exception {
        List<String> log = new ArrayList<>();
        mod = ModJar.read(TestJars.create("scanner-test",
                BrokenScanFixtureSubscriber.class.getName(),
                BrokenScanFixtureSubscriber.class,
                AfterBrokenScanFixtureSubscriber.class));

        int count = AutoSubscriberScanner.scan(mod, "server", loader(), log::add);

        assertEquals(1, count);
        assertTrue(log.stream().anyMatch(line -> line.contains("SUBSCRIBER SCAN FAILED")
                        && line.contains("BrokenScanFixtureSubscriber")),
                () -> "missing failure log: " + log);

        EventBus.GAME.post(new ScanEvent());
        assertEquals(1, AfterBrokenScanFixtureSubscriber.HITS.get());
    }

    private ClassLoader loader() {
        return getClass().getClassLoader();
    }
}
