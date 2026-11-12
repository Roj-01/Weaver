package net.weaver.core.event;

import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.SubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

/** Fixture registered on {@code EventBus.MOD} through {@code Bus.GLOBAL_MOD}. */
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GLOBAL_MOD)
public final class GlobalModBusScanFixtureSubscriber {

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
