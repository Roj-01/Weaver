package net.weaver.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks lifecycle injection misses and enforces the configured policy.
 *
 * <p>A lifecycle transformer that cannot find its target method must not fail
 * silently: without the injection the loader may never initialize mods. The
 * transformer records the miss here; {@link #enforce()} runs on the first game
 * tick and fails the process when the policy is {@code fatal}.</p>
 *
 * <p>The policy is read from {@value #PROPERTY}. The default is {@code fatal};
 * {@code warn} is available for diagnosis. Any other value is treated as
 * {@code fatal}.</p>
 */
public final class LifecycleInjection {

    /** The system property that selects the miss policy. */
    public static final String PROPERTY = "weaver.lifecycle.injection";

    /** How a missing lifecycle injection is handled. */
    public enum Policy {
        /** Log the miss and stop the game on the first tick. */
        FATAL,
        /** Log the miss and continue. */
        WARN
    }

    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private static volatile Consumer<String> logger = LifecycleHooks::logLine;

    private LifecycleInjection() {
    }

    /**
     * Returns the configured policy. The default is {@link Policy#FATAL}.
     *
     * @return the effective policy
     */
    public static Policy policy() {
        String value = System.getProperty(PROPERTY, "fatal");
        return value != null && "warn".equalsIgnoreCase(value.trim())
                ? Policy.WARN : Policy.FATAL;
    }

    /**
     * Records one or more missing lifecycle injection methods.
     *
     * <p>Each method is logged once per process with the stable prefix
     * {@code [weaver] FATAL LIFECYCLE INJECTION MISSING method=}. The method
     * does not throw; {@link #enforce()} applies the policy at a safe point.</p>
     *
     * @param methods the missing method descriptions, possibly empty
     */
    public static void missing(Collection<String> methods) {
        if (methods == null || methods.isEmpty()) {
            return;
        }
        List<String> sorted = methods.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        for (String method : sorted) {
            MISSING.add(method);
            if (REPORTED.add(method)) {
                logger.accept("[weaver] FATAL LIFECYCLE INJECTION MISSING method=" + method);
            }
        }
    }

    /**
     * Records one missing lifecycle injection method.
     *
     * @param method the missing method description
     */
    public static void missing(String method) {
        missing(List.of(Objects.requireNonNull(method, "method")));
    }

    /**
     * Applies the configured policy. With the default {@code fatal} policy,
     * any recorded miss throws an {@link IllegalStateException}. With
     * {@code warn}, the misses were already logged and this method returns.
     *
     * @throws IllegalStateException when the policy is fatal and a miss exists
     */
    public static void enforce() {
        if (policy() != Policy.FATAL || MISSING.isEmpty()) {
            return;
        }
        List<String> sorted = new ArrayList<>(MISSING);
        sorted.sort(String::compareTo);
        throw new IllegalStateException("Weaver lifecycle injection missing: "
                + String.join(", ", sorted));
    }

    /**
     * Returns an immutable snapshot of the recorded misses.
     *
     * @return the missing method descriptions
     */
    public static Set<String> missingMethods() {
        return Set.copyOf(MISSING);
    }

    /**
     * Clears all state and restores the production logger for an isolated test
     * or embedding run.
     */
    public static void resetForTests() {
        MISSING.clear();
        REPORTED.clear();
        logger = LifecycleHooks::logLine;
    }

    /**
     * Replaces the log sink for an isolated test.
     *
     * @param sink the test sink, or {@code null} to restore the production sink
     */
    public static void setLoggerForTests(Consumer<String> sink) {
        logger = sink == null ? LifecycleHooks::logLine : sink;
    }
}
