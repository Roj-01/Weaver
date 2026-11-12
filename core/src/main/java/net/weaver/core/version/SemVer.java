package net.weaver.core.version;

import java.util.Objects;

/**
 * A semantic version with {@code major}, {@code minor}, and {@code patch}
 * components.
 *
 * <p>Parsing is intentionally tolerant for platform ids: a leading {@code v}
 * is ignored, and pre-release ({@code -suffix}) and build ({@code +build})
 * suffixes are stripped. Missing trailing components default to zero, so
 * {@code "2.1"} parses as {@code 2.1.0}. Everything else, including blank or
 * non-numeric input, is rejected with an
 * {@link IllegalArgumentException}.
 *
 * @param major the major version component, never negative
 * @param minor the minor version component, never negative
 * @param patch the patch version component, never negative
 */
public record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException when a component is negative
     */
    public SemVer {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("version components must not be negative: "
                    + major + "." + minor + "." + patch);
        }
    }

    /**
     * Parses a version string.
     *
     * <p>The parser accepts one to three numeric components, an optional
     * leading {@code v}/{@code V}, and optional {@code -suffix} or
     * {@code +build} suffixes, which are stripped. Missing trailing components
     * default to zero.
     *
     * @param version the version string to parse, for example
     *                {@code "v2.1.0-rc.1+build.5"}
     * @return the parsed version
     * @throws IllegalArgumentException when the input is {@code null}, blank,
     *                                  has more than three components, or
     *                                  contains a non-numeric component
     */
    public static SemVer parse(String version) {
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        String value = version.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (value.charAt(0) == 'v' || value.charAt(0) == 'V') {
            value = value.substring(1);
        }
        int dash = value.indexOf('-');
        int plus = value.indexOf('+');
        int cut = dash < 0 ? plus : (plus < 0 ? dash : Math.min(dash, plus));
        if (cut >= 0) {
            value = value.substring(0, cut);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("not a version: '" + version + "'");
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length > 3) {
            throw new IllegalArgumentException("not a version: '" + version
                    + "' (expected at most three numeric components)");
        }
        int[] numbers = new int[3];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || !isDigits(part)) {
                throw new IllegalArgumentException("not a version: '" + version
                        + "' (component '" + part + "' is not a number)");
            }
            try {
                numbers[i] = Integer.parseInt(part);
            } catch (NumberFormatException overflow) {
                throw new IllegalArgumentException("not a version: '" + version
                        + "' (component '" + part + "' is out of range)", overflow);
            }
        }
        return new SemVer(numbers[0], numbers[1], numbers[2]);
    }

    /**
     * Compares this version with another version by major, then minor, then
     * patch.
     *
     * @param other the version to compare against
     * @return a negative value, zero, or a positive value when this version is
     *         less than, equal to, or greater than {@code other}
     */
    @Override
    public int compareTo(SemVer other) {
        Objects.requireNonNull(other, "other");
        int result = Integer.compare(major, other.major);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(minor, other.minor);
        if (result != 0) {
            return result;
        }
        return Integer.compare(patch, other.patch);
    }

    /**
     * Tests whether this version is greater than or equal to another version.
     *
     * @param other the version to compare against
     * @return {@code true} when this version is at least {@code other}
     */
    public boolean atLeast(SemVer other) {
        Objects.requireNonNull(other, "other");
        return compareTo(other) >= 0;
    }

    /**
     * Tests whether this version and another version share a major component.
     *
     * @param other the version to compare against
     * @return {@code true} when both versions have the same major component
     */
    public boolean sameMajor(SemVer other) {
        Objects.requireNonNull(other, "other");
        return major == other.major;
    }

    /**
     * Returns the canonical {@code major.minor.patch} representation.
     *
     * @return the canonical version string
     */
    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    private static boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
