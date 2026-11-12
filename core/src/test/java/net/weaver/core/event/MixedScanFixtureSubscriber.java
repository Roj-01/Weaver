package net.weaver.core.event;

import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.SubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

@EventBusSubscriber
public final class MixedScanFixtureSubscriber {

    public static final AtomicInteger STATIC_HITS = new AtomicInteger();
    public static final AtomicInteger INSTANCE_HITS = new AtomicInteger();
    public static final AtomicInteger INSTANCES = new AtomicInteger();
    public static volatile MixedScanFixtureSubscriber last;

    public MixedScanFixtureSubscriber() {
        INSTANCES.incrementAndGet();
        last = this;
    }

    @SubscribeEvent
    public static void onStaticScan(ScanEvent event) {
        STATIC_HITS.incrementAndGet();
    }

    @SubscribeEvent
    public void onInstanceScan(ScanEvent event) {
        INSTANCE_HITS.incrementAndGet();
    }

    public static void reset() {
        STATIC_HITS.set(0);
        INSTANCE_HITS.set(0);
        INSTANCES.set(0);
        last = null;
    }
}
