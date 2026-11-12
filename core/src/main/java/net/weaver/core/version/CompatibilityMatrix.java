package net.weaver.core.version;

import net.weaver.api.WeaverApi;

import java.util.List;

/**
 * The supported API, game, and hook-target combinations.
 *
 * <p>The matrix is the single source of truth for boot-time compatibility
 * decisions. It is intentionally small: this build implements the baseline
 * {@code 2.1} API on Minecraft {@code 26.3}, plus the additive-only {@code 2.2}
 * feature level on the same game and hook target. Adding a second Minecraft
 * version requires new hook specifications and Mixin refmap work documented in
 * {@code docs/platform-ops.md}; it is not just a new matrix row.
 *
 * <p>{@link #level(String, String, String)} matches API entries by
 * {@code major.minor} (so {@code 2.1.0} selects the {@code 2.1} row) and game
 * and hook-target entries by exact id or numeric version equality (so
 * {@code 26.3.0} matches {@code 26.3}). Unknown combinations are
 * {@link Level#UNSUPPORTED}.
 */
public final class CompatibilityMatrix {

    /**
     * The support level of a matrix entry or lookup.
     */
    public enum Level {
        /** The combination is accepted and fully supported. */
        SUPPORTED,
        /** The combination is usable but provisional; log a warning. */
        EXPERIMENTAL,
        /** The combination is not supported. */
        UNSUPPORTED
    }

    /**
     * One accepted API/game/hook-target combination.
     *
     * @param apiVersion  the API {@code major.minor} row
     * @param gameVersion the exact game version id, or a numeric equivalent
     * @param hookTarget  the exact hook target id, or a numeric equivalent
     * @param level       the support level
     * @param note        a short human-readable rationale used by
     *                    {@link #describe()}
     */
    public record Entry(String apiVersion, String gameVersion, String hookTarget, Level level,
                        String note) {

        /**
         * Validates the entry.
         *
         * @throws IllegalArgumentException when a textual field is {@code null}
         *                                  or blank
         * @throws NullPointerException     when {@code level} or {@code note}
         *                                  is {@code null}
         */
        public Entry {
            apiVersion = requireText(apiVersion, "apiVersion");
            gameVersion = requireText(gameVersion, "gameVersion");
            hookTarget = requireText(hookTarget, "hookTarget");
            if (level == null) {
                throw new NullPointerException("level must not be null");
            }
            if (note == null) {
                throw new NullPointerException("note must not be null");
            }
        }

        private static String requireText(String value, String label) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(label + " must not be null or blank");
            }
            return value.trim();
        }
    }

    /**
     * The accepted combinations, in documentation order.
     */
    public static final List<Entry> ENTRIES = List.of(
            new Entry("2.1", "26.3", "26.3", Level.SUPPORTED,
                    "Accepted baseline: Weaver 2.1 mods on Minecraft 26.3 with the 26.3 hook set."),
            new Entry("2.2", "26.3", "26.3", Level.SUPPORTED,
                    "Feature level 2.2 is additive only over 2.1: no removals, no signature "
                            + "changes, and no dispatch or event behavior changes.")
    );

    private CompatibilityMatrix() {
    }

    /**
     * Looks up the support level for an API/game/hook-target combination.
     *
     * <p>API rows match by {@code major.minor}; game and hook-target values
     * match by exact id or numeric version equality. When several entries
     * match, the highest API version wins and earlier entries win ties. A
     * {@code null} or unparseable API version, or an unknown combination,
     * returns {@link Level#UNSUPPORTED}.
     *
     * @param apiVersion  the Weaver API version, for example {@code "2.1.0"}
     * @param gameVersion the Minecraft version id, for example {@code "26.3"}
     * @param hookTarget  the hook target id, for example {@code "26.3"}
     * @return the support level; never {@code null}
     */
    public static Level level(String apiVersion, String gameVersion, String hookTarget) {
        Entry best = bestEntry(apiVersion, gameVersion, hookTarget);
        return best == null ? Level.UNSUPPORTED : best.level();
    }

    /**
     * Tests whether an API/game/hook-target combination is accepted.
     *
     * <p>{@link Level#SUPPORTED} and {@link Level#EXPERIMENTAL} are accepted;
     * {@link Level#UNSUPPORTED} is not.
     *
     * @param apiVersion  the Weaver API version
     * @param gameVersion the Minecraft version id
     * @param hookTarget  the hook target id
     * @return {@code true} when {@link #level(String, String, String)} is not
     *         {@link Level#UNSUPPORTED}
     */
    public static boolean supports(String apiVersion, String gameVersion, String hookTarget) {
        return level(apiVersion, gameVersion, hookTarget) != Level.UNSUPPORTED;
    }

    /**
     * Looks up the best support level declared for an API version, ignoring
     * the game version.
     *
     * @param apiVersion the Weaver API version
     * @return the strongest matching level, or {@link Level#UNSUPPORTED}
     */
    public static Level levelForApi(String apiVersion) {
        SemVer api = parseOrNull(apiVersion);
        if (api == null) {
            return Level.UNSUPPORTED;
        }
        Level best = Level.UNSUPPORTED;
        for (Entry entry : ENTRIES) {
            if (sameMajorMinor(api, entry.apiVersion())) {
                best = stronger(best, entry.level());
            }
        }
        return best;
    }

    /**
     * Looks up the best support level declared for a game version and hook
     * target, ignoring the API version.
     *
     * @param gameVersion the Minecraft version id
     * @param hookTarget  the hook target id
     * @return the strongest matching level, or {@link Level#UNSUPPORTED}
     */
    public static Level levelForGame(String gameVersion, String hookTarget) {
        Level best = Level.UNSUPPORTED;
        for (Entry entry : ENTRIES) {
            if (matchesVersion(entry.gameVersion(), gameVersion)
                    && matchesVersion(entry.hookTarget(), hookTarget)) {
                best = stronger(best, entry.level());
            }
        }
        return best;
    }

    /**
     * Returns the support level of the running build: {@link WeaverApi#VERSION}
     * with {@link GameVersion#CURRENT}.
     *
     * @return the current support level; never {@code null}
     */
    public static Level currentLevel() {
        return level(WeaverApi.VERSION, GameVersion.CURRENT.id(), GameVersion.CURRENT.hookTarget());
    }

    /**
     * Renders a stable, line-oriented description of the matrix for logs and
     * documentation.
     *
     * @return one header line followed by one line per entry
     */
    public static String describe() {
        StringBuilder out = new StringBuilder("Weaver compatibility matrix: ")
                .append(ENTRIES.size()).append(" entries");
        for (Entry entry : ENTRIES) {
            out.append('\n')
                    .append("  api ").append(entry.apiVersion())
                    .append(" x game ").append(entry.gameVersion())
                    .append(" (hooks ").append(entry.hookTarget()).append("): ")
                    .append(entry.level())
                    .append(" - ").append(entry.note());
        }
        return out.toString();
    }

    private static Entry bestEntry(String apiVersion, String gameVersion, String hookTarget) {
        SemVer api = parseOrNull(apiVersion);
        if (api == null) {
            return null;
        }
        Entry best = null;
        SemVer bestApi = null;
        for (Entry entry : ENTRIES) {
            if (!sameMajorMinor(api, entry.apiVersion())) {
                continue;
            }
            if (!matchesVersion(entry.gameVersion(), gameVersion)) {
                continue;
            }
            if (!matchesVersion(entry.hookTarget(), hookTarget)) {
                continue;
            }
            SemVer candidate = parseOrNull(entry.apiVersion());
            if (best == null || candidate.compareTo(bestApi) > 0) {
                best = entry;
                bestApi = candidate;
            }
        }
        return best;
    }

    private static boolean sameMajorMinor(SemVer api, String row) {
        SemVer entry = parseOrNull(row);
        return entry != null && api.major() == entry.major() && api.minor() == entry.minor();
    }

    private static boolean matchesVersion(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.trim().equals(actual.trim())) {
            return true;
        }
        SemVer left = parseOrNull(expected);
        SemVer right = parseOrNull(actual);
        return left != null && right != null && left.equals(right);
    }

    private static SemVer parseOrNull(String version) {
        try {
            return SemVer.parse(version);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static Level stronger(Level left, Level right) {
        return right.ordinal() < left.ordinal() ? right : left;
    }
}
