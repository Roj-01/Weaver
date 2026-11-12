package net.weaver.gradle.jij;

import java.util.Locale;

/**
 * Constants and naming rules for the Weaver jar-in-jar layout.
 *
 * <p>Nested mod jars live below {@link #ROOT}; {@link #MANIFEST} lists them in
 * deterministic order. The manifest schema is version {@value #SCHEMA_VERSION}
 * with {@code firstWins: true} and a {@code jars} array.
 */
public final class JijLayout {

    /** The jar-in-jar manifest path. */
    public static final String MANIFEST = "META-INF/weaver/jij.json";

    /** The directory that holds nested mod jars; always ends with a slash. */
    public static final String ROOT = "META-INF/weaver/jars/";

    /** The manifest schema version written by the plugin. */
    public static final int SCHEMA_VERSION = 1;

    private JijLayout() {
    }

    /**
     * Returns the nested entry name for a mod id and version.
     *
     * @param id the mod id
     * @param version the mod version
     * @return {@code META-INF/weaver/jars/<id>-<version>.jar}, sanitized
     */
    public static String entryName(String id, String version) {
        return ROOT + fileName(id, version);
    }

    /**
     * Returns the deterministic file name for a mod id and version.
     *
     * @param id the mod id
     * @param version the mod version
     * @return {@code <id>-<version>.jar}, sanitized
     */
    public static String fileName(String id, String version) {
        return sanitize(id) + "-" + sanitize(version) + ".jar";
    }

    /**
     * Restricts a value to {@code [a-z0-9._-]}, replacing runs of other
     * characters with a single dash and trimming leading and trailing dashes.
     *
     * @param value the raw value; {@code null} is treated as blank
     * @return a non-empty safe token
     */
    public static String sanitize(String value) {
        String safe = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return safe.isEmpty() ? "unknown" : safe;
    }

    /**
     * Validates that a nested entry name is relative and cannot escape the
     * parent jar or an extraction directory.
     *
     * @param entryName the candidate entry name
     * @throws IllegalArgumentException if the name is blank, absolute,
     *         contains a backslash or NUL, or contains {@code .} or {@code ..}
     *         segments
     */
    public static void validateEntryName(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            throw new IllegalArgumentException("nested jar entry name is blank");
        }
        if (entryName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("nested jar entry uses a backslash: " + entryName);
        }
        if (entryName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("nested jar entry contains NUL: " + entryName);
        }
        if (entryName.startsWith("/") || entryName.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("nested jar entry is absolute: " + entryName);
        }
        for (String segment : entryName.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("nested jar entry is unsafe: " + entryName);
            }
        }
    }
}
