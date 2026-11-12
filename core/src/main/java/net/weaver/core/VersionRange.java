package net.weaver.core;

/**
 * The loader's name for {@link net.weaver.api.version.VersionRange}, which holds
 * the matcher so the API (and the join check) use the same rules.
 */
public final class VersionRange {
    private VersionRange() {
    }

    /** See {@link net.weaver.api.version.VersionRange#matches(String, String)}. */
    public static boolean matches(String version, String range) {
        return net.weaver.api.version.VersionRange.matches(version, range);
    }

    /** See {@link net.weaver.api.version.VersionRange#isValid(String)}. */
    public static boolean isValid(String range) {
        return net.weaver.api.version.VersionRange.isValid(range);
    }

    /** See {@link net.weaver.api.version.VersionRange#problem(String)}. */
    public static String problem(String range) {
        return net.weaver.api.version.VersionRange.problem(range);
    }

    /** See {@link net.weaver.api.version.VersionRange#compare(String, String)}. */
    public static int compare(String left, String right) {
        return net.weaver.api.version.VersionRange.compare(left, right);
    }
}
