package net.weaver.mixin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.logging.LoggerAdapterConsole;

/**
 * Engine log adapter that mirrors warnings, errors and fatals into the Weaver marker stream.
 *
 * <p>Mixin 0.8.x reports several hard failures only through its logger: a failing injection throws
 * inside {@code MixinApplicatorStandard}, the transformer catches it, logs {@code CRITICAL error
 * while applying mixins} and returns the <em>original</em> bytes. Without this adapter that is a
 * silent drop — exactly what the Weaver fail-closed contract forbids. The engine reads the counter
 * around each transform ({@link #errorCount()}) and turns a swallowed failure into a marker.
 *
 * <p>Injection failures are also <em>decoded</em>: Mixin prints them as a fixed-width banner split
 * over many log lines ({@code Injection validation failed: <handler> in <config>:<mixin> from mod
 * (unknown) expected N invocation(s) but M succeeded. Scanned S target(s).}), so a reader of the raw
 * log sees a wall of banner fragments and nothing actionable. {@link #observe} reassembles the
 * fragments and emits one {@code MIXIN ANCHOR ABSENT} marker per distinct failure plus a running
 * {@code MIXIN COVERAGE} total, so "the mod loaded but one hook never bound" is never invisible.
 *
 * <p>Never throws: logging must not break a class load.
 */
public final class WeaverMarkerLogger extends LoggerAdapterConsole {

    /** Errors seen since process start (monotonic). */
    private static final AtomicInteger ERRORS = new AtomicInteger();
    /** Warnings forwarded; a flood of engine warnings must not flood the marker stream. */
    private static final AtomicInteger WARN_BUDGET = new AtomicInteger(200);
    private static volatile String lastError = "none";
    /** Errors and the last error seen by the current thread: what a transform on it caused. */
    private static final ThreadLocal<int[]> THREAD_ERRORS = ThreadLocal.withInitial(() -> new int[1]);
    private static final ThreadLocal<String> THREAD_LAST_ERROR = ThreadLocal.withInitial(() -> "none");

    /** Distinct injection failures already reported, keyed mod|config|mixin|handler. */
    private static final Set<String> REPORTED = Collections.synchronizedSet(new LinkedHashSet<String>());
    /** mod id -> configs seen arming (from Mixin's own "Mixing ... from <origin>" line). */
    private static final Map<String, Integer> CONFIGS =
            Collections.synchronizedMap(new LinkedHashMap<String, Integer>());
    /** mod id -> distinct injection failures. */
    private static final Map<String, Integer> ABSENT =
            Collections.synchronizedMap(new LinkedHashMap<String, Integer>());
    /** mod id -> mixins that threw while being applied. */
    private static final Map<String, Integer> APPLY =
            Collections.synchronizedMap(new LinkedHashMap<String, Integer>());
    /** Tail of the in-flight banner: Mixin wraps one sentence over several fixed-width lines. */
    private static final StringBuilder BANNER = new StringBuilder();
    private static final int BANNER_LIMIT = 4000;

    private static final Pattern ARMING = Pattern.compile("Mixing \\S+ from (\\S+) into \\S+");
    private static final Pattern HANDLER = Pattern.compile(
            "Injection validation failed: (.+?) in (\\S+?\\.json):(\\S+) from mod");
    private static final Pattern APPLY_FAIL = Pattern.compile(
            "Mixin apply for mod (\\S+) failed (\\S+?\\.json):(\\S+) from mod");
    private static final Pattern EXPECTED = Pattern.compile(
            "expected (\\d+) invocation\\(s\\) but (\\d+) succeeded");
    private static final Pattern SCANNED = Pattern.compile("Scanned (\\d+) target\\(s\\)");
    private static final Pattern TARGET = Pattern.compile("target=\"(L[^\"]+)\"");
    private static final Pattern NOT_FOUND = Pattern.compile(
            "(?:method|field) (\\S+) (?:was not found|is not a member)");

    private static volatile boolean shutdownHookInstalled;

    private final String id;

    public WeaverMarkerLogger(String id) {
        super(id);
        this.id = id;
        installShutdownSummary();
    }

    /** Errors logged by the engine on the current thread (a transform runs on one thread). */
    /** Errors logged by the engine on every thread since start. */
    public static int totalErrorCount() {
        return ERRORS.get();
    }

    public static int errorCount() {
        return THREAD_ERRORS.get()[0];
    }

    public static String lastError() {
        return THREAD_LAST_ERROR.get();
    }

    /** Records that a mod armed one more config (the coverage line needs a real total). */
    public static void noteConfig(String mod) {
        if (mod == null || mod.isEmpty()) {
            return;
        }
        synchronized (CONFIGS) {
            Integer previous = CONFIGS.get(mod);
            CONFIGS.put(mod, previous == null ? 1 : previous + 1);
        }
    }

    /**
     * Records a handler shortfall found by the engine's own audit — the case Mixin never reports,
     * because a tolerant ({@code require = 0}) injection that matched nothing is silent.
     */
    public static void recordAbsent(String mod, String target, int expected, int applied) {
        try {
            String owner = mod == null || mod.isEmpty() ? "(unknown)" : mod;
            if (!REPORTED.add("audit|" + owner + "|" + target + "|" + expected + "|" + applied)) {
                return;
            }
            net.weaver.mixin.core.Marker.emit("MIXIN ANCHOR ABSENT"
                    + " mod=" + owner
                    + " target=" + target
                    + " expected=" + expected
                    + " applied=" + applied
                    + " missing=" + (expected - applied)
                    + " reason=handler-not-bound"
                    + " action=reported");
            synchronized (ABSENT) {
                Integer previous = ABSENT.get(owner);
                ABSENT.put(owner, previous == null ? 1 : previous + 1);
                Integer configs = CONFIGS.get(owner);
                net.weaver.mixin.core.Marker.emit("MIXIN COVERAGE mod=" + owner
                        + " configs=" + (configs == null ? "?" : configs)
                        + " anchor-absent=" + ABSENT.get(owner)
                        + " apply-failed=" + (APPLY.get(owner) == null ? 0 : APPLY.get(owner)));
            }
        } catch (Throwable ignored) {
            // diagnostics must never break weaving
        }
    }

    public static void recordAbsentHandlers(String mod, String target, int expected,
                                            java.util.List<String> missing) {
        try {
            String owner = mod == null || mod.isEmpty() ? "(unknown)" : mod;
            StringBuilder names = new StringBuilder();
            int shown = 0;
            for (String handler : missing) {
                if (shown == 4) {
                    names.append(",+").append(missing.size() - 4);
                    break;
                }
                if (shown > 0) {
                    names.append(',');
                }
                names.append(handler);
                shown++;
            }
            if (!REPORTED.add("audit|" + owner + "|" + target + "|" + names)) {
                return;
            }
            net.weaver.mixin.core.Marker.emit("MIXIN ANCHOR ABSENT"
                    + " mod=" + owner
                    + " target=" + target
                    + " expected=" + expected
                    + " applied=" + (expected - missing.size())
                    + " missing=" + missing.size()
                    + " handlers=" + names
                    + " reason=handler-not-bound"
                    + " action=reported");
            synchronized (ABSENT) {
                Integer previous = ABSENT.get(owner);
                ABSENT.put(owner, previous == null ? 1 : previous + 1);
                Integer configs = CONFIGS.get(owner);
                net.weaver.mixin.core.Marker.emit("MIXIN COVERAGE mod=" + owner
                        + " configs=" + (configs == null ? "?" : configs)
                        + " anchor-absent=" + ABSENT.get(owner)
                        + " apply-failed=" + (APPLY.get(owner) == null ? 0 : APPLY.get(owner)));
            }
        } catch (Throwable ignored) {
            // diagnostics must never break weaving
        }
    }

    /** Distinct injection failures decoded so far. */
    public static int anchorAbsentCount() {
        int total = 0;
        synchronized (ABSENT) {
            for (Integer value : ABSENT.values()) {
                total += value == null ? 0 : value;
            }
        }
        return total;
    }

    /** Emits the per-mod coverage total. Safe to call any time, with or without failures. */
    public static void reportCoverage() {
        try {
            synchronized (ABSENT) {
                if (ABSENT.isEmpty() && APPLY.isEmpty()) {
                    net.weaver.mixin.core.Marker.emit("MIXIN COVERAGE anchor-absent=0 apply-failed=0");
                    return;
                }
                Set<String> mods = new LinkedHashSet<String>(ABSENT.keySet());
                mods.addAll(APPLY.keySet());
                for (String mod : mods) {
                    Integer configs = CONFIGS.get(mod);
                    Integer absent = ABSENT.get(mod);
                    Integer failed = APPLY.get(mod);
                    net.weaver.mixin.core.Marker.emit("MIXIN COVERAGE mod=" + mod
                            + " configs=" + (configs == null ? "?" : configs)
                            + " anchor-absent=" + (absent == null ? 0 : absent)
                            + " apply-failed=" + (failed == null ? 0 : failed));
                }
            }
        } catch (Throwable ignored) {
            // diagnostics must never break weaving or shutdown
        }
    }

    private static void installShutdownSummary() {
        if (shutdownHookInstalled) {
            return;
        }
        shutdownHookInstalled = true;
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(WeaverMarkerLogger::reportCoverage,
                    "weaver-mixin-coverage"));
        } catch (Throwable ignored) {
            // a late class load or restricted runtime must not fail the engine
        }
    }

    @Override
    public void log(Level level, String message, Object... params) {
        observe(level, message, params, null);
        super.log(level, message, params);
    }

    @Override
    public void log(Level level, String message, Throwable throwable) {
        observe(level, message, new Object[0], throwable);
        super.log(level, message, throwable);
    }

    private void observe(Level level, String message, Object[] params, Throwable throwable) {
        try {
            if (level == null) {
                return;
            }
            String text = format(message, params);
            if (throwable != null) {
                text = text + " cause=" + rootCause(throwable);
            }
            if (level == Level.ERROR || level == Level.FATAL) {
                ERRORS.incrementAndGet();
                lastError = trim(text);
                THREAD_ERRORS.get()[0]++;
                THREAD_LAST_ERROR.set(lastError);
                net.weaver.mixin.core.Marker.emit("MIXIN ENGINE " + level.name()
                        + " logger=" + this.id + " message=" + trim(text));
                decodeBanner(text);
            } else if (level == Level.WARN && WARN_BUDGET.getAndDecrement() > 0) {
                net.weaver.mixin.core.Marker.emit("MIXIN ENGINE WARN logger=" + this.id
                        + " message=" + trim(text));
            } else if (level == Level.INFO) {
                countArming(text);
            } else if (level == Level.DEBUG || level == Level.TRACE) {
                // A tolerant (require=0) injection failure is only reported at DEBUG: Mixin logs the
                // same sentence there instead of throwing the banner. Decoding runs on every level so
                // "armed but never bound" cannot hide behind a log-level difference.
                decodeBanner(text);
            }
        } catch (Throwable ignored) {
            // logging must never break weaving
        }
    }

    /** Counts configs per mod from Mixin's arming line: {@code Mixing <mixin> from <origin>}. */
    private static void countArming(String text) {
        Matcher matcher = ARMING.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String mod = modOf(matcher.group(1));
        if (mod == null) {
            return;
        }
        synchronized (CONFIGS) {
            Integer previous = CONFIGS.get(mod);
            CONFIGS.put(mod, previous == null ? 1 : previous + 1);
        }
    }

    /**
     * Reassembles Mixin's wrapped failure banner and emits one marker per distinct injection that
     * found no target. Mixin writes the sentence in fixed-width fragments, so parsing runs over a
     * rolling concatenation rather than single lines.
     */
    private static void decodeBanner(String raw) {
        String flat = flatten(raw);
        if (flat.isEmpty()) {
            return;
        }
        synchronized (BANNER) {
            BANNER.append(' ').append(flat);
            if (BANNER.length() > BANNER_LIMIT) {
                BANNER.delete(0, BANNER.length() - BANNER_LIMIT);
            }
            String joined = BANNER.toString();

            Matcher handlerMatch = HANDLER.matcher(joined);
            if (!handlerMatch.find()) {
                decodeApplyFailure(joined);
                return;
            }
            String handler = handlerMatch.group(1).trim();
            String config = handlerMatch.group(2).trim();
            String mixin = shortName(handlerMatch.group(3).trim());
            String mod = modOf(config);
            if (mod == null) {
                mod = "(unknown)";
            }
            if (!REPORTED.add(mod + "|" + config + "|" + mixin + "|" + handler)) {
                return;
            }
            Matcher expectedMatch = EXPECTED.matcher(joined);
            Matcher scannedMatch = SCANNED.matcher(joined);
            String expected = expectedMatch.find(handlerMatch.end()) ? expectedMatch.group(1) : "?";
            String succeeded = expectedMatch.reset().find(handlerMatch.end())
                    ? expectedMatch.group(2) : "?";
            String scanned = scannedMatch.find(handlerMatch.end()) ? scannedMatch.group(1) : "?";
            String target = "unresolved";
            Matcher targetMatch = TARGET.matcher(joined);
            if (targetMatch.find(handlerMatch.end())) {
                target = targetMatch.group(1);
            } else {
                Matcher notFound = NOT_FOUND.matcher(joined);
                if (notFound.find(handlerMatch.end())) {
                    target = notFound.group(1);
                }
            }
            String reason = "anchor-not-found";
            if (!"?".equals(expected) && "0".equals(succeeded)) {
                reason = "anchor-removed-by-host";
            }
            net.weaver.mixin.core.Marker.emit("MIXIN ANCHOR ABSENT"
                    + " mod=" + mod
                    + " config=" + config
                    + " mixin=" + mixin
                    + " handler=" + handler
                    + " expected=" + expected
                    + " succeeded=" + succeeded
                    + " scanned=" + scanned
                    + " target=" + target
                    + " reason=" + reason
                    + " action=reported");
            synchronized (ABSENT) {
                Integer previous = ABSENT.get(mod);
                ABSENT.put(mod, previous == null ? 1 : previous + 1);
                Integer configs = CONFIGS.get(mod);
                net.weaver.mixin.core.Marker.emit("MIXIN COVERAGE mod=" + mod
                        + " configs=" + (configs == null ? "?" : configs)
                        + " anchor-absent=" + ABSENT.get(mod));
            }
            // Consume the banner so a later, unrelated sentence is not glued onto it.
            BANNER.setLength(0);
        }
    }

    /**
     * Second banner shape: {@code Mixin apply for mod <id> failed <config>:<mixin>} — the mixin blew
     * up while being applied (a missing target, a bad accessor). Reported separately from a missing
     * anchor because the remedy differs.
     */
    private static void decodeApplyFailure(String joined) {
        Matcher applyMatch = APPLY_FAIL.matcher(joined);
        if (!applyMatch.find()) {
            return;
        }
        String config = applyMatch.group(2).trim();
        String mixin = shortName(applyMatch.group(3).trim());
        String mod = modOf(config);
        if (mod == null) {
            mod = applyMatch.group(1).trim();
        }
        if (!REPORTED.add("apply|" + mod + "|" + config + "|" + mixin)) {
            return;
        }
        net.weaver.mixin.core.Marker.emit("MIXIN APPLY FAILED"
                + " mod=" + mod
                + " config=" + config
                + " mixin=" + mixin
                + " reason=apply-exception"
                + " detail=see-engine-fatal"
                + " action=reported");
        synchronized (APPLY) {
            Integer previous = APPLY.get(mod);
            APPLY.put(mod, previous == null ? 1 : previous + 1);
        }
        BANNER.setLength(0);
    }

    /** Strips Mixin's banner decoration and fixed-width padding. */
    private static String flatten(String raw) {
        String text = raw.replace("/*", " ").replace("*/", " ");
        StringBuilder out = new StringBuilder(text.length());
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                space = true;
                continue;
            }
            if (space && out.length() > 0) {
                out.append(' ');
            }
            space = false;
            out.append(c);
        }
        return out.toString().trim();
    }

    /** {@code weaver/<mod>/<config>.json} -> {@code <mod>}; anything else -> {@code null}. */
    private static String modOf(String origin) {
        if (origin == null) {
            return null;
        }
        String path = origin.trim();
        int slash = path.indexOf('/');
        if (slash <= 0 || !"weaver".equals(path.substring(0, slash))) {
            return null;
        }
        int next = path.indexOf('/', slash + 1);
        return next < 0 ? null : path.substring(slash + 1, next);
    }

    private static String shortName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }

    private static String format(String message, Object[] params) {
        if (message == null) {
            return "null";
        }
        if (params == null || params.length == 0) {
            return message;
        }
        StringBuilder out = new StringBuilder(message.length() + 32);
        int next = 0;
        int index = 0;
        while (index < message.length()) {
            int mark = message.indexOf("{}", index);
            if (mark < 0) {
                out.append(message, index, message.length());
                break;
            }
            out.append(message, index, mark);
            if (next < params.length) {
                out.append(params[next++]);
            } else {
                out.append("{}");
            }
            index = mark + 2;
        }
        return out.toString();
    }

    private static String rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** One-line, marker-safe, bounded. */
    private static String trim(String text) {
        String oneLine = text.replace('\r', ' ').replace('\n', ' ').replace('"', '\'');
        return oneLine.length() <= 400 ? oneLine : oneLine.substring(0, 397) + "...";
    }
}
