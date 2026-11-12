package net.weaver.api.client;

import java.util.List;

/**
 * Game-free bridge used by the built-in item tooltip mixin.
 *
 * <p>The loader installs the appender from the core client backend. In the
 * default setup the appender is
 * {@code net.weaver.core.client.CoreClientHooks.appendTooltip(Object, Object,
 * List)}, which builds a {@link TooltipContext}, collects provider lines, and
 * appends them to the supplied game list.
 *
 * <p>This type exists so the optional game-typed module can call the loader
 * hook without a compile-time dependency on the core module. The bridge is a
 * no-op until an appender is installed. It never retains the arguments.
 */
public final class ClientTooltipHook
{
    /**
     * Appends provider lines to a mutable game tooltip list.
     */
    @FunctionalInterface
    public interface Appender
    {
        /**
         * Appends zero or more lines.
         *
         * @param minecraft the client instance, possibly {@code null}
         * @param itemStack the stack being described, possibly {@code null}
         * @param lines     the game tooltip list, never {@code null}
         */
        void append(Object minecraft, Object itemStack, List<Object> lines);
    }

    private static volatile Appender appender;

    private ClientTooltipHook()
    {
    }

    /**
     * Installs the appender used by the built-in hook.
     *
     * @param newAppender the appender, or {@code null} to remove it
     */
    public static void install(Appender newAppender)
    {
        appender = newAppender;
    }

    /** Removes the installed appender. */
    public static void uninstall()
    {
        appender = null;
    }

    /**
     * Whether an appender is currently installed.
     *
     * @return {@code true} when {@link #appendTooltip(Object, Object, List)}
     *         forwards to an appender
     */
    public static boolean isInstalled()
    {
        return appender != null;
    }

    /**
     * Forwards one tooltip request to the installed appender.
     *
     * <p>No-op when no appender is installed or {@code lines} is
     * {@code null}. Exceptions from the appender propagate to the caller so the
     * mixin can isolate and report them.
     *
     * @param minecraft the client instance, possibly {@code null}
     * @param itemStack the stack being described, possibly {@code null}
     * @param lines     the game tooltip list, possibly {@code null}
     */
    public static void appendTooltip(Object minecraft, Object itemStack, List<Object> lines)
    {
        if (lines == null)
        {
            return;
        }
        final Appender current = appender;
        if (current != null)
        {
            current.append(minecraft, itemStack, lines);
        }
    }
}
