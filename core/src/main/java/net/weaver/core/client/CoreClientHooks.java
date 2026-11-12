package net.weaver.core.client;

import net.weaver.api.client.ClientTooltipHook;
import net.weaver.api.client.HudRenderContext;
import net.weaver.api.client.TooltipContext;
import net.weaver.core.LifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Integration entry points for injected client hooks.
 *
 * <p>The built-in HUD mixin calls the client facade directly; a manual hook or
 * proof can call {@link #renderHud(Object)} or
 * {@link #renderHud(Object, float)} from the HUD render callback and
 * {@link #appendTooltip(Object, Object, List)} from the item tooltip callback.
 * Both methods only forward to an already installed backend: they never create
 * or install one, so they remain no-ops before the client backend is installed.
 */
public final class CoreClientHooks
{
    private static final AtomicBoolean TOOLTIP_FAILURE_REPORTED = new AtomicBoolean();

    private CoreClientHooks()
    {
    }

    /**
     * Installs the game-free tooltip bridge used by the built-in mixin.
     */
    static void installBridge()
    {
        ClientTooltipHook.install(CoreClientHooks::appendTooltip);
    }

    /**
     * Removes the game-free tooltip bridge.
     */
    static void uninstallBridge()
    {
        ClientTooltipHook.uninstall();
    }

    /**
     * Forwards one HUD render pass to the installed client backend.
     *
     * <p>No-op when no backend is installed or {@code graphics} is
     * {@code null}. The partial tick is {@code 0}; use
     * {@link #renderHud(Object, float)} when an injected hook can supply one.
     *
     * @param graphics the game graphics or GUI rendering object
     */
    public static void renderHud(Object graphics)
    {
        renderHud(graphics, 0.0F);
    }

    /**
     * Forwards one HUD render pass with the frame's partial tick.
     *
     * <p>No-op when no backend is installed or {@code graphics} is
     * {@code null}. The built-in client HUD mixin calls this overload from
     * {@code Hud.extractRenderState}.
     *
     * @param graphics    the game graphics or GUI rendering object
     * @param partialTick the partial tick for the current frame
     */
    public static void renderHud(Object graphics, float partialTick)
    {
        CoreClientBackend backend = CoreClientBackend.installed();
        if (backend == null || graphics == null)
        {
            return;
        }
        try
        {
            ClientGameAdapter adapter = backend.adapter();
            HudRenderContext context = new HudRenderContext(graphics, adapter.minecraft(),
                    Math.max(0, adapter.guiWidth(graphics)),
                    Math.max(0, adapter.guiHeight(graphics)), partialTick);
            backend.renderHud(context);
        }
        catch (RuntimeException failure)
        {
            log("[weaver] CLIENT HUD HOOK FAILED reason=" + failure);
        }
    }

    /**
     * Appends tooltip lines from the installed client backend to a game list.
     *
     * <p>No-op when no backend is installed or {@code lines} is {@code null}.
     * Each collected plain-text line is converted to a game component through
     * the adapter and appended to {@code lines}. A line whose conversion fails
     * is skipped. An immutable game list is tolerated: the first append failure
     * is reported once and the remaining lines are skipped.
     *
     * @param minecraft the client instance, possibly {@code null}
     * @param itemStack the stack being described, possibly {@code null}
     * @param lines     the mutable game tooltip line list, possibly {@code null}
     */
    public static void appendTooltip(Object minecraft, Object itemStack, List<Object> lines)
    {
        CoreClientBackend backend = CoreClientBackend.installed();
        if (backend == null || lines == null)
        {
            return;
        }
        try
        {
            ClientGameAdapter adapter = backend.adapter();
            List<Object> existing = new ArrayList<>(lines.size());
            for (Object line : lines)
            {
                if (line != null)
                {
                    existing.add(line);
                }
            }
            Object screen = null;
            try
            {
                screen = adapter.currentScreen();
            }
            catch (RuntimeException failure)
            {
                log("[weaver] CLIENT TOOLTIP SCREEN FAILED reason=" + failure);
            }
            TooltipContext context = new TooltipContext(minecraft, screen, itemStack, existing);
            for (String line : backend.tooltipLines(context))
            {
                try
                {
                    Object component = adapter.literal(line);
                    if (component != null)
                    {
                        adapter.appendComponent(lines, component);
                    }
                }
                catch (RuntimeException failure)
                {
                    log("[weaver] CLIENT TOOLTIP LINE FAILED reason=" + failure);
                }
            }
        }
        catch (Throwable failure)
        {
            if (TOOLTIP_FAILURE_REPORTED.compareAndSet(false, true))
            {
                log("[weaver] CLIENT TOOLTIP HOOK FAILED reason=" + failure);
            }
        }
    }

    private static void log(String message)
    {
        try
        {
            LifecycleHooks.logLine(message);
        }
        catch (Throwable ignored)
        {
        }
    }
}
