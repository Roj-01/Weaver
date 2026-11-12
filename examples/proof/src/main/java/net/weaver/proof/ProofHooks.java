package net.weaver.proof;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Observable state for the real-game proof. */
public final class ProofHooks {

    private static final Set<String> PHASES = ConcurrentHashMap.newKeySet();
    private static final Set<String> EVENTS = ConcurrentHashMap.newKeySet();
    private static final Set<String> CHECKED = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final List<String> PASSED = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<String, AtomicInteger> HITS = new ConcurrentHashMap<>();

    private ProofHooks() {
    }

    public static void phase(String name) {
        PHASES.add(name);
        System.out.println("PROOF PHASE " + name);
    }

    public static void mixinHit(String name) {
        int count = HITS.computeIfAbsent(name, key -> new AtomicInteger()).incrementAndGet();
        System.out.println("PROOF MIXIN " + name + " hit=" + count);
    }

    public static void event(String name) {
        int count = HITS.computeIfAbsent(name, key -> new AtomicInteger()).incrementAndGet();
        EVENTS.add(name);
        System.out.println("PROOF EVENT " + name + " hit=" + count);
    }

    public static void eventOnce(String name) {
        if (EVENTS.add(name)) {
            HITS.computeIfAbsent(name, key -> new AtomicInteger()).incrementAndGet();
            System.out.println("PROOF EVENT " + name);
        }
    }

    /**
     * Records one new-style proof check. A passing check is printed once; the
     * first failure is printed and remembered. A check that fails stays failed
     * even if a later call with the same name passes.
     *
     * @param condition whether the check passed
     * @param name      the check name shown in the log
     * @return {@code condition}, unless the name already failed
     */
    public static boolean check(boolean condition, String name) {
        if (!condition) {
            if (FAILED.add(name)) {
                System.out.println("PROOF CHECK ok=false " + name);
            }
            return false;
        }
        if (FAILED.contains(name)) {
            return false;
        }
        if (CHECKED.add(name)) {
            PASSED.add(name);
            System.out.println("PROOF CHECK ok=true " + name);
        }
        return true;
    }

    /** Records a failed check with a reason. */
    public static void fail(String name, Throwable failure) {
        String reason = failure == null ? "unknown" : failure.toString();
        if (FAILED.add(name)) {
            System.out.println("PROOF CHECK ok=false " + name + " reason=" + reason);
        }
    }

    /** The names of checks that failed, in discovery order. */
    public static List<String> failedChecks() {
        return List.copyOf(FAILED);
    }

    /** The names of checks that passed, in discovery order. */
    public static List<String> passedChecks() {
        return List.copyOf(PASSED);
    }

    public static int hits(String name) {
        AtomicInteger count = HITS.get(name);
        return count == null ? 0 : count.get();
    }

    public static boolean hasPhase(String name) {
        return PHASES.contains(name);
    }

    public static boolean hasEvent(String name) {
        return EVENTS.contains(name);
    }

    public static boolean hasEventContaining(String fragment) {
        return EVENTS.stream().anyMatch(event -> event.contains(fragment));
    }

    public static String phases() {
        return String.join(",", new TreeSet<>(PHASES));
    }

    public static String events() {
        return String.join(",", new TreeSet<>(EVENTS));
    }
}
