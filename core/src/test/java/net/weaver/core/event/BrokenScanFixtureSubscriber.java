package net.weaver.core.event;

import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.SubscribeEvent;

@EventBusSubscriber
public final class BrokenScanFixtureSubscriber {

    public BrokenScanFixtureSubscriber(int ignored) {
    }

    @SubscribeEvent
    public void onScan(ScanEvent event) {
    }
}
