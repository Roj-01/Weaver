package net.weaver.core.capability;

import java.lang.reflect.Method;

/**
 * Diagnostic sink for capability persistence and synchronization.
 *
 * <p>The loader's {@code LifecycleHooks.logLine} is resolved reflectively so the
 * capability package stays compilable in isolation, for example in the
 * standalone capability proof fallback. When the loader logger is unavailable
 * the message is written to {@code System.err}. Every message already starts
 * with the {@code [weaver]} prefix.</p>
 */
final class CapabilityLog
{
    private static final Method LOG_LINE = resolve();

    private CapabilityLog()
    {
    }

    static void log(String message)
    {
        Method method = LOG_LINE;
        if (method != null)
        {
            try
            {
                method.invoke(null, message);
                return;
            }
            catch (Throwable ignored)
            {
            }
        }
        System.err.println(message);
    }

    private static Method resolve()
    {
        try
        {
            Class<?> hooks = Class.forName("net.weaver.core.LifecycleHooks", true,
                    CapabilityLog.class.getClassLoader());
            return hooks.getMethod("logLine", String.class);
        }
        catch (Throwable unavailable)
        {
            return null;
        }
    }
}
