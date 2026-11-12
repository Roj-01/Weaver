package net.weaver.core.hook;

import net.weaver.api.event.events.DefaultHookEventFactory;
import net.weaver.api.event.events.HookEventFactory;
import net.weaver.api.spi.DefaultGameEventFactory;
import net.weaver.api.spi.GameEventFactory;
import net.weaver.core.LifecycleHooks;

import java.util.Objects;

/**
 * Holds the event factory used by {@link GameHooks}.
 *
 * <p>The loader installs the typed {@code api-game} factory when that artifact
 * is on the game class path. When it is absent, the holder keeps the game-free
 * {@link DefaultGameEventFactory} so base events still work. The holder has no
 * compile-time game dependency; the optional typed hook factory is loaded
 * reflectively from the installed factory's class loader.
 */
public final class GameEventFactoryHolder {

    private static final String TYPED_HOOK_FACTORY =
            "net.weaver.api.game.event.TypedHookEventFactory";
    private static final GameEventFactory BASE = new DefaultGameEventFactory();
    private static final Object HOOK_LOCK = new Object();
    private static volatile GameEventFactory factory = BASE;
    private static volatile String diagnostics = "base";
    private static volatile HookEventFactory hookEvents;

    private GameEventFactoryHolder() {
    }

    /**
     * Installs the event factory. Passing {@code null} restores the base
     * factory. The cached hook-event factory is cleared in both branches.
     *
     * @param replacement the factory to use, or {@code null} for the base
     */
    public static void install(GameEventFactory replacement) {
        hookEvents = null;
        if (replacement == null) {
            factory = BASE;
            diagnostics = "base";
            return;
        }
        factory = replacement;
        diagnostics = replacement.getClass().getName();
    }

    /**
     * The active factory, never {@code null}.
     *
     * @return the installed factory or the base factory
     */
    public static GameEventFactory events() {
        return factory;
    }

    /**
     * The hook-event factory used by the newer hook entry points, resolved
     * lazily and cached.
     *
     * <p>When a non-base factory is installed, this tries to load
     * {@code net.weaver.api.game.event.TypedHookEventFactory} through the
     * installed factory's class loader. On success the typed factory is
     * cached and logged as enabled; on any failure the base
     * {@link DefaultHookEventFactory} is cached and the failure is logged
     * once. When the base factory is installed, the base hook factory is used
     * without logging.
     *
     * @return the cached hook-event factory, never {@code null}
     */
    public static HookEventFactory hookEvents() {
        HookEventFactory current = hookEvents;
        if (current != null) {
            return current;
        }
        synchronized (HOOK_LOCK) {
            current = hookEvents;
            if (current != null) {
                return current;
            }
            current = resolveHookEvents();
            hookEvents = current;
            return current;
        }
    }

    private static HookEventFactory resolveHookEvents() {
        GameEventFactory installed = factory;
        if (installed == BASE) {
            return new DefaultHookEventFactory();
        }
        try {
            ClassLoader loader = installed.getClass().getClassLoader();
            Class<?> type = Class.forName(TYPED_HOOK_FACTORY, true, loader);
            Object instance = type.getDeclaredConstructor().newInstance();
            HookEventFactory resolved = (HookEventFactory) instance;
            LifecycleHooks.logLine("[weaver] TYPED HOOK EVENTS enabled factory="
                    + resolved.getClass().getName());
            return resolved;
        } catch (Throwable failure) {
            LifecycleHooks.logLine("[weaver] TYPED HOOK EVENTS unavailable reason=" + failure);
            return new DefaultHookEventFactory();
        }
    }

    /** True when a non-base factory is installed. */
    public static boolean isTyped() {
        return factory != BASE;
    }

    /** A short description of the active factory, for diagnostics. */
    public static String description() {
        return diagnostics;
    }

    /** Restores the base factory and clears the cached hook factory. Intended for tests. */
    public static void resetForTests() {
        hookEvents = null;
        factory = BASE;
        diagnostics = "base";
    }
}
