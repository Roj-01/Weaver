package net.example.mod;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Observable state for the core spike. */
public final class ProofHooks {

    private static final Set<String> PHASES = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger HITS = new AtomicInteger();

    private ProofHooks() {
    }

    public static void phase(String name) {
        PHASES.add(name);
        System.out.println("PROOF PHASE " + name);
    }

    public static boolean hasPhase(String name) {
        return PHASES.contains(name);
    }

    public static String phases() {
        return String.join(",", new TreeSet<>(PHASES));
    }

    public static void hit() {
        System.out.println("PROOF MIXIN hit=" + HITS.incrementAndGet());
    }

    public static int hits() {
        return HITS.get();
    }
}
