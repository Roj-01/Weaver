package net.weaver.api.game.event.support;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.weaver.api.event.FailureThrottle;

/**
 * Keeps one failing game object from crashing the whole game. Vanilla turns an
 * exception thrown while an entity or block entity ticks, while a feature or
 * structure generates, or while the client draws an entity or a screen, into a
 * crash report that stops the game (or the server for every player). With the
 * shield (on by default, {@code -Dweaver.crashShield=false} turns it off) Weaver
 * reports the failure, with the class most likely responsible, and:
 *
 * <ul>
 *   <li>suspends that entity or block entity: it stays in the world and in the
 *       save but no longer ticks (until the world is loaded again);</li>
 *   <li>disconnects a player whose own tick fails, instead of stopping the server;</li>
 *   <li>skips a failing feature or structure piece in that chunk;</li>
 *   <li>skips drawing a failing entity or block entity for the frame, and closes
 *       a screen that fails.</li>
 * </ul>
 *
 * <p>Errors that mean the JVM itself is in trouble (out of memory, internal
 * errors) are never caught.
 */
public final class CrashShield {
    /** Whether the shield is active. */
    public static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("weaver.crashShield", "true"));

    private static final Set<Object> SUSPENDED = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));

    private CrashShield() {
    }

    /** True for failures the shield must let through. */
    public static boolean fatal(Throwable failure) {
        return failure instanceof VirtualMachineError && !(failure instanceof StackOverflowError);
    }

    public static boolean suspended(Object subject) {
        return !SUSPENDED.isEmpty() && SUSPENDED.contains(subject);
    }

    /** Stops ticking {@code subject} and reports why. */
    public static void suspend(Object subject, String kind, String description, Throwable failure) {
        SUSPENDED.add(subject);
        report(kind, description, "suspended", failure);
    }

    /** Reports a prevented crash, throttled; the first report of a kind carries the stack trace. */
    public static void report(String kind, String description, String action, Throwable failure) {
        FailureThrottle.Decision decision = FailureThrottle.record(
                "shield|" + kind + "|" + description + "|" + failure.getClass().getName());
        if (!decision.report()) {
            return;
        }
        net.weaver.api.WeaverProblems.record("crash-shield", kind + " " + description + " " + action
                + " (suspect " + suspect(failure) + ")");
        System.out.println("[weaver] CRASH PREVENTED kind=" + kind + " subject=" + description
                + " action=" + action + " suspect=" + suspect(failure) + " reason=" + failure
                + decision.suffix());
        if (decision.first()) {
            failure.printStackTrace(System.out);
        }
    }

    /** The registry id of a placed feature's configured feature, for reports. */
    public static String featureName(net.minecraft.world.level.levelgen.placement.PlacedFeature feature) {
        return feature.feature().unwrapKey().map(key -> key.identifier().toString()).orElse("unregistered");
    }

    /** Rethrows any throwable without declaring it. */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException rethrow(Throwable failure) throws T {
        throw (T) failure;
    }

    /**
     * The first stack frame that is neither the JDK, the game, nor Weaver: the
     * class of the mod most likely at fault, or {@code minecraft} when none.
     */
    public static String suspect(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            for (StackTraceElement frame : current.getStackTrace()) {
                String name = frame.getClassName();
                if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
                        || name.startsWith("sun.") || name.startsWith("net.minecraft.")
                        || name.startsWith("com.mojang.") || name.startsWith("net.weaver.api.")
                        || name.startsWith("net.weaver.core.") || name.startsWith("net.weaver.mixin.")
                        || name.startsWith("org.spongepowered.") || name.startsWith("com.llamalad7.")
                        || name.startsWith("it.unimi.") || name.startsWith("com.google.")) {
                    continue;
                }
                return name + "." + frame.getMethodName();
            }
        }
        return "minecraft";
    }
}
