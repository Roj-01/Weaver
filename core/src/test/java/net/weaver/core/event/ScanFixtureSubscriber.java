package net.weaver.core.event;

import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.SubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

/** Test fixture discovered by {@link AutoSubscriberScannerTest}. */
@EventBusSubscriber
public final class ScanFixtureSubscriber {

    public static final AtomicInteger HITS = new AtomicInteger();

    private ScanFixtureSubscriber() {
    }

    @SubscribeEvent
    public static void onScanEvent(ScanEvent event) {
        HITS.incrementAndGet();
    }
}
