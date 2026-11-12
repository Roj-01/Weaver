package net.weaver.hooksproof;

import net.weaver.api.ModContext;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Output and check bookkeeping for the Weaver 2.2 hooks proof.
 *
 * <p>Checks are idempotent and failure-sticky: the first failure for a name is
 * printed and remembered, and a later pass with the same name is ignored. The
 * final result is printed exactly once.
 */
public final class HooksProof {

    private static final Set<String> CHECKED = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final Set<String> EVENTS = ConcurrentHashMap.newKeySet();
    private static final List<String> PASSED = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean RESULT_LOGGED = new AtomicBoolean();

    private HooksProof() {
    }

    /** Records a passing check. */
    public static boolean check(String name) {
        return check(true, name);
    }

    /**
     * Records one proof check. A passing check is printed once; the first
     * failure is printed and remembered. A check that fails stays failed even
     * if a later call with the same name passes.
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
        String reason = failure == null ? "unknown" : describe(failure);
        if (FAILED.add(name)) {
            System.out.println("PROOF CHECK ok=false " + name + " reason=" + reason);
        }
    }

    /** Records a failure only when the check has not already passed. */
    public static void failIfNotChecked(String name, Throwable failure) {
        if (!CHECKED.contains(name)) {
            fail(name, failure);
        }
    }

    /** Prints an observation marker. */
    public static void event(String name) {
        EVENTS.add(name);
        System.out.println("PROOF EVENT " + name);
    }

    /** Prints an observation marker only the first time it is seen. */
    public static void eventOnce(String name) {
        if (EVENTS.add(name)) {
            System.out.println("PROOF EVENT " + name);
        }
    }

    /** The names of checks that passed, in discovery order. */
    public static List<String> passedChecks() {
        return List.copyOf(PASSED);
    }

    /** The names of checks that failed, in discovery order. */
    public static List<String> failedChecks() {
        return List.copyOf(FAILED);
    }

    /**
     * Prints the final result line through the mod context exactly once.
     *
     * @param context the context used for the log line
     * @return {@code true} when every recorded check passed
     */
    public static boolean result(ModContext context) {
        if (!RESULT_LOGGED.compareAndSet(false, true)) {
            return false;
        }
        boolean ok = FAILED.isEmpty();
        String reason = ok ? "" : " reason=" + String.join(",", FAILED);
        context.log("PROOF RESULT ok=" + ok + " side=" + context.side()
                + " checks=" + PASSED.size()
                + " check_names=" + String.join("|", PASSED) + reason);
        return ok;
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        String text = failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return text.replaceAll("\\s+", " ");
    }
}
