package net.weaver.core.event;

import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.SubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

@EventBusSubscriber(modId = "other-mod")
public final class MismatchedModIdScanFixtureSubscriber {

    public static final AtomicInteger HITS = new AtomicInteger();

    @SubscribeEvent
    public static void onScan(ScanEvent event) {
        HITS.incrementAndGet();
    }

    public static void reset() {
        HITS.set(0);
    }
}
