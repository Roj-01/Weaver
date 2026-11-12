package net.weaver.core.event;

import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.SubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

@EventBusSubscriber
public final class InstanceScanFixtureSubscriber {

    public static final AtomicInteger HITS = new AtomicInteger();
    public static final AtomicInteger INSTANCES = new AtomicInteger();
    public static volatile InstanceScanFixtureSubscriber last;

    public InstanceScanFixtureSubscriber() {
        INSTANCES.incrementAndGet();
        last = this;
    }

    @SubscribeEvent
    public void onScan(ScanEvent event) {
        HITS.incrementAndGet();
    }

    public static void reset() {
        HITS.set(0);
        INSTANCES.set(0);
        last = null;
    }
}
