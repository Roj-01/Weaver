package net.weaver.core.event;

import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.SubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

/** Fixture for a mod-local subscriber that accepts both logical sides. */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public final class ModBusLogicalBothSubscriber {

    public static final AtomicInteger HITS = new AtomicInteger();

    /** Counts one hit for every dispatched {@link ScanEvent}. */
    @SubscribeEvent
    public static void onScan(ScanEvent event) {
        HITS.incrementAndGet();
    }

    /** Clears the hit counter between tests. */
    public static void reset() {
        HITS.set(0);
    }
}
