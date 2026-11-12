package net.weaver.api;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * Version and compatibility policy for the Weaver API.
 *
 * <p>The API follows an additive-first policy. Additive changes, such as new
 * types, new overloads, and new event subclasses, are preferred and ship in a
 * minor release. Removing a public type or changing an existing method,
 * constructor, or field descriptor requires a major version bump. A public
 * member is deprecated for at least one minor release before it may be removed.
 *
 * <p>The base event types intentionally keep their {@link Object}-typed
 * accessors. Typed events in the optional {@code api-game} artifact extend
 * those base types and narrow the accessor return types covariantly, so code
 * compiled against the base accessors still links and keeps working.
 *
 * <p>Migration from the 2.1 baseline to the 2.2 feature level is additive only:
 * no public type, method, constructor, or field is removed or changes its
 * descriptor, event dispatch and event behavior are unchanged, and mods
 * compiled against 2.1 keep linking without a recompile. {@link #VERSION}
 * therefore intentionally stays {@code "2.1.0"}; {@link #FEATURE_LEVEL}
 * identifies the newer platform capabilities without moving the binary API
 * version. The Minecraft game version and hook target are versioned separately
 * through {@link #GAME_VERSION} and {@link #HOOK_TARGET}.
 */
public final class WeaverApi {

    /**
     * The current API version.
     *
     * <p>Format: {@code major.minor.patch}. The loader and the API artifact
     * must agree on the major version.
     */
    public static final String VERSION = "2.1.0";

    /**
     * The major version of the binary API contract.
     *
     * <p>A different major version is not compatible: public descriptors may
     * have changed or been removed.
     */
    public static final int API_MAJOR = 2;

    /**
     * The minor version of the binary API contract.
     *
     * <p>Minor releases are additive only.
     */
    public static final int API_MINOR = 1;

    /**
     * The oldest API version accepted by this build.
     *
     * <p>A version is accepted when it has the same major version as
     * {@link #API_MAJOR} and is greater than or equal to this version.
     */
    public static final String MIN_SUPPORTED_API = "2.1.0";

    /**
     * The Weaver feature level implemented by this build.
     *
     * <p>This is {@code "2.2"} while {@link #VERSION} stays {@code "2.1.0"}
     * because the 2.2 workstream is additive only. Consumers should gate new
     * 2.2 capabilities on this constant, not on {@link #VERSION}.
     */
    public static final String FEATURE_LEVEL = "2.2";

    /**
     * The Minecraft game version this build targets.
     *
     * <p>Game versions are independent of the Weaver API version. This build
     * implements the {@code 26.3} game version only.
     */
    public static final String GAME_VERSION = "26.3";

    /**
     * The game hook target this build's hook specifications are written
     * against.
     *
     * <p>The hook target may differ from {@link #GAME_VERSION} when a game
     * release keeps binary compatibility with an earlier hook set.
     */
    public static final String HOOK_TARGET = "26.3";

    /**
     * The game patch level expected by this build.
     *
     * <p>Patched game artifacts expose {@code weaver$patchLevel()} on
     * {@code net.weaver.patched.PatchedGame}. The loader detects that class
     * reflectively and reports the detected level; this constant records the
     * level this build expects. The value is additive: an unpatched game still
     * boots through the runtime transformer path and reports level {@code 0}.
     */
    public static final String PATCH_LEVEL = "1";

    private static final String GAME_API_CLASS = "net.weaver.api.game.WeaverGameApi";

    private static final Set<String> SUPPORTED_GAME_VERSIONS = Set.of(GAME_VERSION);

    private WeaverApi() {
    }

    /**
     * Tests whether an API version can link against this build.
     *
     * <p>Compatible versions have the same major version as
     * {@link #API_MAJOR} and are greater than or equal to
     * {@link #MIN_SUPPORTED_API}. A {@code v} prefix, a {@code -suffix}, and a
     * {@code +build} suffix are ignored. {@code null}, blank, and unparseable
     * values are not compatible.
     *
     * @param version the API version to test, for example {@code "2.1.0"}
     * @return {@code true} when the version can link against this build
     */
    public static boolean isCompatibleApi(String version) {
        int[] parsed = parseVersion(version);
        int[] minimum = parseVersion(MIN_SUPPORTED_API);
        return parsed != null && minimum != null
                && parsed[0] == API_MAJOR
                && compare(parsed, minimum) >= 0;
    }

    /**
     * Tests whether a game version is supported by the compatibility matrix.
     *
     * <p>Only {@link #GAME_VERSION} is supported by this build. Matching is by
     * exact version id or by numeric version equality, so {@code "26.3"} and
     * {@code "26.3.0"} are equivalent. This mirrors the game and hook-target
     * rows of the core compatibility matrix. {@code null} and unknown values
     * are not supported.
     *
     * @param gameVersion the Minecraft version id to test, for example
     *                    {@code "26.3"}
     * @return {@code true} when the game version is supported
     */
    public static boolean isCompatibleGame(String gameVersion) {
        if (gameVersion == null) {
            return false;
        }
        for (String supported : SUPPORTED_GAME_VERSIONS) {
            if (sameVersion(supported, gameVersion)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requires an API version to be compatible with this build.
     *
     * @param version the API version to require
     * @throws IllegalStateException when {@link #isCompatibleApi(String)} is
     *                               {@code false}
     */
    public static void requireCompatibleApi(String version) {
        if (!isCompatibleApi(version)) {
            throw new IllegalStateException("Weaver API version " + String.valueOf(version)
                    + " is not compatible; this build requires API " + MIN_SUPPORTED_API
                    + " or newer within major version " + API_MAJOR);
        }
    }

    /**
     * Requires a game version to be supported by this build.
     *
     * @param gameVersion the Minecraft version id to require
     * @throws IllegalStateException when
     *                               {@link #isCompatibleGame(String)} is
     *                               {@code false}
     */
    public static void requireCompatibleGame(String gameVersion) {
        if (!isCompatibleGame(gameVersion)) {
            throw new IllegalStateException("Minecraft game version "
                    + String.valueOf(gameVersion) + " is not supported; this build targets "
                    + GAME_VERSION + " with hook target " + HOOK_TARGET);
        }
    }

    /**
     * Reflectively checks the optional typed game API through a class loader.
     *
     * <p>The check reads the public static {@link String} field {@code VERSION}
     * of {@code net.weaver.api.game.WeaverGameApi}. This method deliberately
     * has no compile-time dependency on the {@code api-game} artifact, so the
     * base API still works when the typed artifact is absent. A missing class,
     * a missing or non-static field, a non-{@link String} value, or an
     * unsupported version all return {@code false}. This method never throws.
     *
     * @param loader the class loader that may contain the typed game API, or
     *               {@code null}
     * @return {@code true} when the typed game API exists and declares a
     *         supported version
     */
    public static boolean isGameApiCompatible(ClassLoader loader) {
        if (loader == null) {
            return false;
        }
        try {
            Class<?> type = Class.forName(GAME_API_CLASS, false, loader);
            Field field = type.getField("VERSION");
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                return false;
            }
            Object value = field.get(null);
            return value instanceof String version && isCompatibleGame(version);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Returns a short human-readable statement of the API stability policy.
     *
     * <p>The policy is additive-first: new types, overloads, constants, and
     * event subclasses ship in minor releases; removal or a signature change
     * requires a major release; deprecated members remain for at least one
     * minor release; base event accessors stay {@link Object}-typed so typed
     * game events remain binary compatible.
     *
     * @return the stability policy statement
     */
    public static String stabilityPolicy() {
        return "Weaver API policy: additive-first. New types, overloads, constants, and event "
                + "subclasses ship in minor releases; removal or signature/descriptor changes "
                + "require a major release; deprecated members remain for at least one minor "
                + "release; base event accessors stay Object-typed so typed api-game events "
                + "remain binary compatible.";
    }

    private static int[] parseVersion(String version) {
        if (version == null) {
            return null;
        }
        String value = version.trim();
        if (value.isEmpty()) {
            return null;
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
            return null;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length > 3) {
            return null;
        }
        int[] numbers = new int[3];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || !isDigits(part)) {
                return null;
            }
            try {
                numbers[i] = Integer.parseInt(part);
            } catch (NumberFormatException overflow) {
                return null;
            }
        }
        return numbers;
    }

    private static boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int compare(int[] left, int[] right) {
        for (int i = 0; i < 3; i++) {
            int comparison = Integer.compare(left[i], right[i]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static boolean sameVersion(String left, String right) {
        int[] leftNumbers = parseVersion(left);
        int[] rightNumbers = parseVersion(right);
        if (leftNumbers != null && rightNumbers != null) {
            return compare(leftNumbers, rightNumbers) == 0;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }
}
