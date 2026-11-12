package net.weaver.core.hook;

/** Fixture used by the transformer guardrail tests. */
public class HookGuardFixture
{
    /** Constructor body marker. */
    public int constructed;

    /** Instance body counter. */
    public int calls;

    /** Creates the fixture and marks the constructor body as executed. */
    public HookGuardFixture()
    {
        constructed = 1;
    }

    /** Static target that a non-void action must not instrument. */
    public static void statically()
    {
        HookGuardHooks.recordStaticBody();
    }

    /** Instance target used as the positive control for a cancel action. */
    public void instance()
    {
        calls++;
    }
}
