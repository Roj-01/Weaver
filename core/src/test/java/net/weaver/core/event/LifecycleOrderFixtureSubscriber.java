package net.weaver.core.event;

import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.SubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

@EventBusSubscriber
public final class LifecycleOrderFixtureSubscriber {

    public static final AtomicInteger HITS = new AtomicInteger();
    public static final AtomicInteger CONSTRUCTED_AFTER_INIT = new AtomicInteger();
    public static volatile LifecycleOrderFixtureSubscriber last;

    public LifecycleOrderFixtureSubscriber() {
        last = this;
        if (LifecycleOrderFixtureMod.initialized) {
            CONSTRUCTED_AFTER_INIT.incrementAndGet();
        }
    }

    @SubscribeEvent
    public void onScan(ScanEvent event) {
        HITS.incrementAndGet();
    }

    public static void reset() {
        HITS.set(0);
        CONSTRUCTED_AFTER_INIT.set(0);
        last = null;
    }
}
