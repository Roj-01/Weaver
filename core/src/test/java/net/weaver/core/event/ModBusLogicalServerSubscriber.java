package net.weaver.core.event;

import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.SubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

/** Fixture for a mod-local subscriber restricted to the logical server side. */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, logicalSide = LogicalSide.SERVER)
public final class ModBusLogicalServerSubscriber {

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
