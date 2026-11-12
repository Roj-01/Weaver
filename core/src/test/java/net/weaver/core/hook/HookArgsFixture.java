package net.weaver.core.hook;

/** Fixture used by the five-argument bytecode hook test. */
public class HookArgsFixture
{
    public Object[] seen;

    public HookArgsFixture()
    {
    }

    public void five(Object a, Object b, Object c, Object d, Object e)
    {
        seen = new Object[]{a, b, c, d, e};
    }
}
