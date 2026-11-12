package net.weaver.compatsuite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared check state and marker output for the compatibility suite. */
public final class CompatSuiteState {

    private static final Map<String, String> PASSED = new LinkedHashMap<>();
    private static final Map<String, String> FAILED = new LinkedHashMap<>();
    private static boolean resultPrinted;

    private CompatSuiteState() {
    }

    public static synchronized boolean check(boolean condition, String name) {
        if (!condition) {
            if (FAILED.putIfAbsent(name, "false") == null) {
                System.out.println("COMPAT SUITE CHECK ok=false " + name);
            }
            return false;
        }
        if (FAILED.containsKey(name)) {
            return false;
        }
        if (PASSED.putIfAbsent(name, name) == null) {
            System.out.println("COMPAT SUITE CHECK ok=true " + name);
        }
        return true;
    }

    public static synchronized int passedCount() {
        return PASSED.size();
    }

    public static synchronized int failedCount() {
        return FAILED.size();
    }

    public static synchronized List<String> passedNames() {
        return List.copyOf(PASSED.keySet());
    }

    public static synchronized List<String> failedNames() {
        return List.copyOf(FAILED.keySet());
    }

    /**
     * Prints the final result marker once.
     *
     * @return {@code true} when this call printed the marker
     */
    public static synchronized boolean printResultOnce(boolean ok, String side, String detail) {
        if (resultPrinted) {
            return false;
        }
        resultPrinted = true;
        StringBuilder line = new StringBuilder("COMPAT SUITE RESULT ok=")
                .append(ok)
                .append(" side=")
                .append(side)
                .append(" checks=")
                .append(PASSED.size());
        if (detail != null && !detail.isBlank()) {
            line.append(' ').append(detail);
        }
        System.out.println(line);
        return true;
    }
}
