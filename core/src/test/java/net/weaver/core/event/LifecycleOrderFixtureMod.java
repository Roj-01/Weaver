package net.weaver.core.event;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;
import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.SubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

@EventBusSubscriber
public final class LifecycleOrderFixtureMod implements WeaverMod {

    public static final AtomicInteger INIT_CALLS = new AtomicInteger();
    public static final AtomicInteger HITS = new AtomicInteger();
    public static volatile boolean initialized;
    public static volatile LifecycleOrderFixtureMod last;

    public LifecycleOrderFixtureMod() {
        last = this;
    }

    @Override
    public void onInitialize(ModContext context) {
        INIT_CALLS.incrementAndGet();
        initialized = true;
    }

    @SubscribeEvent
    public void onScan(ScanEvent event) {
        HITS.incrementAndGet();
    }

    public static void reset() {
        INIT_CALLS.set(0);
        HITS.set(0);
        initialized = false;
        last = null;
    }
}
