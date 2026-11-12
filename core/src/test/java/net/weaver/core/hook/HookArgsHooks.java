package net.weaver.core.hook;

/** Static targets used by the five-argument bytecode hook test. */
public final class HookArgsHooks
{
    public static Object[] LAST;

    private HookArgsHooks()
    {
    }

    public static void record(Object a, Object b, Object c, Object d, Object e)
    {
        LAST = new Object[]{a, b, c, d, e};
    }
}
