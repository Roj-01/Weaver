package net.weaver.core.hook;

/** Fixture used by the bytecode hook test. Its methods must stay tiny and deterministic. */
public class HookFixture
{
    public HookFixture()
    {
    }

    public void voidMethod()
    {
    }

    public int intMethod()
    {
        return 42;
    }

    public long longMethod()
    {
        return 1234567890123L;
    }
}
