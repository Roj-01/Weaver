package net.weaver.core.modops;

import java.util.Locale;
import java.util.Objects;

/**
 * Loader policy configuration for the two class-space safeguards.
 *
 * <p>Both policies default to {@link Mode#WARN}. Property names are:
 * <ul>
 *   <li>{@value #PROPERTY_BASE} &mdash; default mode for both policies</li>
 *   <li>{@value #PROPERTY_API_SHADOW} &mdash; API shadow policy override</li>
 *   <li>{@value #PROPERTY_DUPLICATE_CLASSES} &mdash; duplicate class policy override</li>
 * </ul>
 *
 * <p>Values are case-insensitive {@code warn} or {@code fatal}; anything else
 * resolves to {@link Mode#WARN}.
 */
public final class ModPolicies {

    /** Base policy property; applies to both policies unless overridden. */
    public static final String PROPERTY_BASE = "weaver.policy";

    /** API shadow policy property. */
    public static final String PROPERTY_API_SHADOW = "weaver.policy.apiShadow";

    /** Duplicate class policy property. */
    public static final String PROPERTY_DUPLICATE_CLASSES = "weaver.policy.duplicateClasses";

    private static final ModPolicies WARN = new ModPolicies(Mode.WARN, Mode.WARN);

    private final Mode apiShadow;
    private final Mode duplicateClasses;

    /**
     * Creates an explicit policy pair.
     *
     * @param apiShadow         policy for mods that physically contain loader API classes
     * @param duplicateClasses  policy for class entries shipped by multiple mods
     * @throws NullPointerException if either mode is {@code null}
     */
    public ModPolicies(Mode apiShadow, Mode duplicateClasses) {
        this.apiShadow = Objects.requireNonNull(apiShadow, "apiShadow");
        this.duplicateClasses = Objects.requireNonNull(duplicateClasses, "duplicateClasses");
    }

    /**
     * Reads policies from system properties.
     *
     * @return configured policies; defaults to warning for absent or invalid values
     */
    public static ModPolicies fromSystemProperties() {
        Mode base = parse(System.getProperty(PROPERTY_BASE), Mode.WARN);
        Mode apiShadow = parse(System.getProperty(PROPERTY_API_SHADOW), base);
        Mode duplicateClasses = parse(System.getProperty(PROPERTY_DUPLICATE_CLASSES), base);
        return new ModPolicies(apiShadow, duplicateClasses);
    }

    /**
     * Returns the default warning-only policies.
     *
     * @return policies with both modes set to {@link Mode#WARN}
     */
    public static ModPolicies warn() {
        return WARN;
    }

    /**
     * Returns policies that treat both violations as fatal.
     *
     * @return policies with both modes set to {@link Mode#FATAL}
     */
    public static ModPolicies fatal() {
        return new ModPolicies(Mode.FATAL, Mode.FATAL);
    }

    /**
     * Returns the API shadow policy mode.
     *
     * @return configured mode
     */
    public Mode apiShadow() {
        return apiShadow;
    }

    /**
     * Returns the duplicate class policy mode.
     *
     * @return configured mode
     */
    public Mode duplicateClasses() {
        return duplicateClasses;
    }

    private static Mode parse(String value, Mode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "warn" -> Mode.WARN;
            case "fatal" -> Mode.FATAL;
            default -> Mode.WARN;
        };
    }

    /** How a policy violation is handled. */
    public enum Mode {

        /** Keep loading, log the violation, and record a diagnostic. */
        WARN,

        /** Log the violation, close open jars, and abort discovery. */
        FATAL
    }
}
