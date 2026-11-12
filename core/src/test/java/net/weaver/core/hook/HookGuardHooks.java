package net.weaver.core.hook;

import java.util.concurrent.atomic.AtomicInteger;

/** Static hook targets used by the transformer guardrail tests. */
public final class HookGuardHooks
{
    /** Controls the boolean returned by {@link #allow()}. */
    public static volatile boolean allowed;

    /** Number of times the static fixture body ran. */
    public static final AtomicInteger STATIC_BODIES = new AtomicInteger();

    /** Number of allow checks performed by instrumented methods. */
    public static final AtomicInteger ALLOW_CHECKS = new AtomicInteger();

    private HookGuardHooks()
    {
    }

    /** Returns the boolean toggle and records the check. */
    public static boolean allow()
    {
        ALLOW_CHECKS.incrementAndGet();
        return allowed;
    }

    /** Records that the static fixture body ran. */
    public static void recordStaticBody()
    {
        STATIC_BODIES.incrementAndGet();
    }

    /** Restores the fixture state. */
    public static void reset()
    {
        allowed = false;
        STATIC_BODIES.set(0);
        ALLOW_CHECKS.set(0);
    }
}
