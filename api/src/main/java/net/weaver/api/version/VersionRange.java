package net.weaver.api.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small version-range matcher for mod metadata: dependencies, the game version,
 * and the versions of a mod that may connect to each other ({@code compatibleVersions}).
 *
 * <p>Supported range forms:</p>
 * <ul>
 *   <li>blank or {@code *}: any version;</li>
 *   <li>a bare version: exact match ({@code 1.2.0});</li>
 *   <li>comparison operators {@code >=}, {@code <=}, {@code >}, {@code <}, {@code =}, with
 *       optional whitespace between the operator and the version ({@code ">= 1.2.0"});</li>
 *   <li>tilde: {@code ~1.2.3} means {@code >=1.2.3 <1.3}, {@code ~1.2} means
 *       {@code >=1.2 <1.3}, {@code ~1} means {@code >=1 <2};</li>
 *   <li>caret: pins the first non-zero component, so {@code ^1.2.3} means
 *       {@code >=1.2.3 <2}, {@code ^0.2.3} means {@code >=0.2.3 <0.3} and
 *       {@code ^0.0.3} means {@code >=0.0.3 <0.0.4};</li>
 *   <li>comma- or whitespace-separated constraints, all of which must hold
 *       ({@code ">=1.0 <2.0"});</li>
 *   <li>Maven ranges: {@code [1.0,2.0)}, {@code [1.0,)}, {@code (,2.0]}, {@code [1.0}]
 *       and comma-separated unions of them.</li>
 * </ul>
 *
 * <p>An unparseable range never matches; {@link #problem(String)} reports why so the
 * loader can reject the declaring mod with a clear diagnostic.</p>
 */
public final class VersionRange {

    private static final Pattern VERSION = Pattern.compile("[vV]?[0-9][0-9A-Za-z.+_-]*");
    private static final Pattern CONSTRAINT = Pattern.compile(
            "\\G[\\s,]*(>=|<=|>|<|=|~|\\^)?\\s*([^\\s,]+)");
    private static final Pattern MAVEN_SET = Pattern.compile(
            "\\G\\s*,?\\s*([\\[(])([^\\])]*)([\\])])\\s*");

    private VersionRange() {
    }

    /**
     * Reports whether a version satisfies a range. An invalid range never matches.
     *
     * @param version the candidate version
     * @param range   the range expression
     * @return {@code true} when the version is inside the range
     */
    public static boolean matches(String version, String range) {
        Parsed parsed = parse(range);
        if (parsed.problem != null) {
            return false;
        }
        for (List<Bound> conjunction : parsed.alternatives) {
            boolean all = true;
            for (Bound bound : conjunction) {
                if (!bound.test(version)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reports whether a range expression can be parsed.
     *
     * @param range the range expression
     * @return {@code true} when the range is valid
     */
    public static boolean isValid(String range) {
        return parse(range).problem == null;
    }

    /**
     * Explains why a range expression cannot be parsed.
     *
     * @param range the range expression
     * @return a short reason, or {@code null} when the range is valid
     */
    public static String problem(String range) {
        return parse(range).problem;
    }

    private static Parsed parse(String range) {
        if (range == null || range.isBlank() || "*".equals(range.trim())) {
            return new Parsed(List.of(List.of()), null);
        }
        String text = range.trim();
        if (text.startsWith("[") || text.startsWith("(")) {
            return parseMaven(text);
        }
        List<Bound> bounds = new ArrayList<>();
        Matcher matcher = CONSTRAINT.matcher(text);
        int end = 0;
        while (matcher.find()) {
            end = matcher.end();
            String operator = matcher.group(1) == null ? "" : matcher.group(1);
            String value = matcher.group(2);
            if (!VERSION.matcher(value).matches()) {
                return Parsed.invalid("invalid version '" + value + "' in range '" + text + "'");
            }
            switch (operator) {
                case ">=" -> bounds.add(new Bound(value, true, true));
                case ">" -> bounds.add(new Bound(value, true, false));
                case "<=" -> bounds.add(new Bound(value, false, true));
                case "<" -> bounds.add(new Bound(value, false, false));
                case "~" -> {
                    bounds.add(new Bound(value, true, true));
                    bounds.add(new Bound(tildeUpper(value), false, false));
                }
                case "^" -> {
                    bounds.add(new Bound(value, true, true));
                    bounds.add(new Bound(caretUpper(value), false, false));
                }
                default -> {
                    bounds.add(new Bound(value, true, true));
                    bounds.add(new Bound(value, false, true));
                }
            }
        }
        if (text.substring(end).replaceAll("[\\s,]", "").length() > 0 || bounds.isEmpty()) {
            return Parsed.invalid("unparseable range '" + text + "'");
        }
        return new Parsed(List.of(List.copyOf(bounds)), null);
    }

    private static Parsed parseMaven(String text) {
        List<List<Bound>> alternatives = new ArrayList<>();
        Matcher matcher = MAVEN_SET.matcher(text);
        int end = 0;
        while (end < text.length() && matcher.find()) {
            end = matcher.end();
            boolean lowerInclusive = "[".equals(matcher.group(1));
            boolean upperInclusive = "]".equals(matcher.group(3));
            String body = matcher.group(2);
            int comma = body.indexOf(',');
            List<Bound> bounds = new ArrayList<>();
            if (comma < 0) {
                String exact = body.trim();
                if (!lowerInclusive || !upperInclusive || !VERSION.matcher(exact).matches()) {
                    return Parsed.invalid("invalid Maven range '" + text + "'");
                }
                bounds.add(new Bound(exact, true, true));
                bounds.add(new Bound(exact, false, true));
            } else {
                String lower = body.substring(0, comma).trim();
                String upper = body.substring(comma + 1).trim();
                if (lower.isEmpty() && upper.isEmpty()
                        || !lower.isEmpty() && !VERSION.matcher(lower).matches()
                        || !upper.isEmpty() && !VERSION.matcher(upper).matches()
                        || upper.indexOf(',') >= 0) {
                    return Parsed.invalid("invalid Maven range '" + text + "'");
                }
                if (!lower.isEmpty()) {
                    bounds.add(new Bound(lower, true, lowerInclusive));
                }
                if (!upper.isEmpty()) {
                    bounds.add(new Bound(upper, false, upperInclusive));
                }
            }
            alternatives.add(List.copyOf(bounds));
        }
        if (end != text.length() || alternatives.isEmpty()) {
            return Parsed.invalid("invalid Maven range '" + text + "'");
        }
        return new Parsed(List.copyOf(alternatives), null);
    }

    /** {@code ~1.2.3} and {@code ~1.2} stop below {@code 1.3}; {@code ~1} stops below {@code 2}. */
    private static String tildeUpper(String base) {
        int[] parts = numbers(core(normalize(base)));
        int index = parts.length >= 2 ? 1 : 0;
        return bump(parts, index);
    }

    /** Bumps the first non-zero component; when every given component is zero, the last one. */
    private static String caretUpper(String base) {
        int[] parts = numbers(core(normalize(base)));
        int index = parts.length - 1;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != 0) {
                index = i;
                break;
            }
        }
        return bump(parts, index);
    }

    private static String bump(int[] parts, int index) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i <= index; i++) {
            if (i > 0) {
                out.append('.');
            }
            out.append(i == index ? parts[i] + 1 : parts[i]);
        }
        return out.toString();
    }

    private static int[] numbers(String core) {
        String[] parts = core.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9].*$", ""));
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }

    /**
     * Compares two versions. The numeric core ({@code 1.2.3}) is compared
     * first; only when the cores are equal does a pre-release suffix
     * ({@code -SNAPSHOT}, {@code -beta.1}) sort below the release. Build
     * metadata after {@code +} is ignored.
     *
     * @param left  the first version
     * @param right the second version
     * @return a negative number, zero, or a positive number
     */
    public static int compare(String left, String right) {
        String a = stripBuild(normalize(left));
        String b = stripBuild(normalize(right));
        int comparison = compareDotted(core(a), core(b), true);
        if (comparison != 0) {
            return comparison;
        }
        String preA = preRelease(a);
        String preB = preRelease(b);
        if (preA == null || preB == null) {
            if (preA == null && preB == null) {
                return 0;
            }
            return preA == null ? 1 : -1;
        }
        return compareDotted(preA, preB, false);
    }

    private static int compareDotted(String left, String right, boolean padWithZero) {
        String[] partsA = left.split("\\.");
        String[] partsB = right.split("\\.");
        int count = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < count; i++) {
            if (!padWithZero && (i >= partsA.length || i >= partsB.length)) {
                return Integer.compare(partsA.length, partsB.length);
            }
            String partA = i < partsA.length ? partsA[i] : "0";
            String partB = i < partsB.length ? partsB[i] : "0";
            int comparison = comparePart(partA, partB);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int comparePart(String left, String right) {
        boolean numericA = !left.isEmpty() && left.chars().allMatch(Character::isDigit);
        boolean numericB = !right.isEmpty() && right.chars().allMatch(Character::isDigit);
        if (numericA && numericB) {
            return new java.math.BigInteger(left).compareTo(new java.math.BigInteger(right));
        }
        if (numericA != numericB) {
            return numericA ? -1 : 1;
        }
        return left.toLowerCase(Locale.ROOT).compareTo(right.toLowerCase(Locale.ROOT));
    }

    private static String core(String version) {
        int dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(0, dash);
    }

    private static String preRelease(String version) {
        int dash = version.indexOf('-');
        return dash < 0 ? null : version.substring(dash + 1);
    }

    private static String stripBuild(String version) {
        int plus = version.indexOf('+');
        return plus < 0 ? version : version.substring(0, plus);
    }

    private static String normalize(String version) {
        String value = version == null ? "0" : version.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }
        return value.isEmpty() ? "0" : value;
    }

    private record Bound(String version, boolean lower, boolean inclusive) {
        boolean test(String candidate) {
            int comparison = compare(candidate, version);
            if (lower) {
                return inclusive ? comparison >= 0 : comparison > 0;
            }
            return inclusive ? comparison <= 0 : comparison < 0;
        }
    }

    private record Parsed(List<List<Bound>> alternatives, String problem) {
        static Parsed invalid(String problem) {
            return new Parsed(List.of(), problem);
        }
    }
}
