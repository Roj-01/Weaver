package net.weaver.core.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Static targets used by the bytecode transformer tests. */
public final class HookTestHooks
{
    /** The shared exception instance the throw test asserts against. */
    public static final IllegalStateException ERROR = new IllegalStateException("boom");

    /** Controls the boolean returned by {@link #allow(Object)}. */
    public static volatile boolean allowed = true;

    /** The value returned by {@link #replacement(Object)}. */
    public static volatile Object replacement;

    /** Number of normal-return observations. */
    public static final AtomicInteger EXITS = new AtomicInteger();

    /** Number of throw observations. */
    public static final AtomicInteger THROWS = new AtomicInteger();

    /** Ordered record of entry-call names used by the priority test. */
    public static final List<String> ORDER = new ArrayList<>();

    private HookTestHooks()
    {
    }

    /** Returns the current boolean toggle. */
    public static boolean allow(Object self)
    {
        return allowed;
    }

    /** Returns the configured replacement value. */
    public static Object replacement(Object self)
    {
        return replacement;
    }

    /** Records a normal-return observation. */
    public static void recordExit(Object self)
    {
        EXITS.incrementAndGet();
    }

    /** Records a throw observation. */
    public static void recordThrow(Object self)
    {
        THROWS.incrementAndGet();
    }

    /** Records the early entry call in {@link #ORDER}. */
    public static void recordEarly(Object self)
    {
        ORDER.add("early");
    }

    /** Records the late entry call in {@link #ORDER}. */
    public static void recordLate(Object self)
    {
        ORDER.add("late");
    }

    /** Restores every mutable fixture value. */
    public static void reset()
    {
        allowed = true;
        replacement = null;
        EXITS.set(0);
        THROWS.set(0);
        ORDER.clear();
    }
}
