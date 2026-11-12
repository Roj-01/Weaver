package net.weaver.core.hook;

/** Fixture used by the action and throw bytecode hook tests. */
public class HookBehaviorFixture
{
    /** Body counter shared by the cancel tests. */
    public int calls;

    /** Set by the constructor body so the constructor-cancel test can observe it. */
    public int constructed;

    /** No-argument constructor used by the constructor action test. */
    public HookBehaviorFixture()
    {
        constructed = 1;
    }

    /** Void method whose body increments {@link #calls}. */
    public void cancelVoid()
    {
        calls++;
    }

    /** Boolean method whose body increments {@link #calls} and returns true. */
    public boolean cancelBoolean()
    {
        calls++;
        return true;
    }

    /** Reference-returning method used by the return-value test. */
    public String replace()
    {
        calls++;
        return "original";
    }

    /** Method that always throws the shared exception instance. */
    public void throwing()
    {
        calls++;
        throw HookTestHooks.ERROR;
    }

    /** Method that records its body after the entry hooks run. */
    public void ordered()
    {
        HookTestHooks.ORDER.add("body");
    }
}
