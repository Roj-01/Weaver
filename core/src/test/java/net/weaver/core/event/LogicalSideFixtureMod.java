package net.weaver.core.event;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;

import java.util.concurrent.atomic.AtomicInteger;

public final class LogicalSideFixtureMod implements WeaverMod {

    public static final AtomicInteger CLIENT_TICKS = new AtomicInteger();
    public static final AtomicInteger SERVER_TICKS = new AtomicInteger();
    public static final AtomicInteger JOINS = new AtomicInteger();
    public static volatile ModContext clientContext;
    public static volatile ModContext serverContext;
    public static volatile ModContext joinContext;

    @Override
    public void onClientTick(ModContext context, long tick) {
        CLIENT_TICKS.incrementAndGet();
        clientContext = context;
    }

    @Override
    public void onServerTick(ModContext context, long tick) {
        SERVER_TICKS.incrementAndGet();
        serverContext = context;
    }

    @Override
    public void onPlayerJoin(ModContext context, Object player) {
        JOINS.incrementAndGet();
        joinContext = context;
    }

    public static void reset() {
        CLIENT_TICKS.set(0);
        SERVER_TICKS.set(0);
        JOINS.set(0);
        clientContext = null;
        serverContext = null;
        joinContext = null;
    }
}
