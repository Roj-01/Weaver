package net.weaver.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Problems the loader worked around for each mod: a mixin config that was
 * rejected, a mixin that was dropped, a jar that could not be read. Each was
 * logged when it happened; this list lets the game show them in one place (the
 * loader prints a summary once the game is running and adds it to crash
 * reports), so a player knows which mod is not fully working.
 */
public final class WeaverProblems {
    private static final int MAX_PER_MOD = 20;
    private static final Map<String, List<String>> PROBLEMS = new LinkedHashMap<>();

    private WeaverProblems() {
    }

    /** Records a problem of a mod (or of the loader, with id {@code weaver}). */
    public static void record(String modId, String problem) {
        if (problem == null) {
            return;
        }
        String id = modId == null || modId.isBlank() ? "(unknown)" : modId;
        synchronized (PROBLEMS) {
            List<String> list = PROBLEMS.computeIfAbsent(id, key -> new ArrayList<>());
            if (list.size() < MAX_PER_MOD && !list.contains(problem)) {
                list.add(problem);
            }
        }
    }

    /** Every recorded problem, by mod id (a snapshot). */
    public static Map<String, List<String>> all() {
        synchronized (PROBLEMS) {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            PROBLEMS.forEach((id, list) -> copy.put(id, List.copyOf(list)));
            return Collections.unmodifiableMap(copy);
        }
    }

    public static boolean isEmpty() {
        synchronized (PROBLEMS) {
            return PROBLEMS.isEmpty();
        }
    }

    /** A multi-line summary, empty when nothing was recorded. */
    public static String summary() {
        StringBuilder out = new StringBuilder();
        all().forEach((id, list) -> {
            for (String problem : list) {
                out.append("  ").append(id).append(": ").append(problem).append('\n');
            }
        });
        return out.toString();
    }
}
