package net.weaver.core.version;

import net.weaver.api.WeaverApi;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * Boot-time version checks that never depend on game classes and never throw.
 *
 * <p>{@link #check(ClassLoader, String)} combines the API support level, the
 * game/hook support level, and the optional typed game API version into one
 * loggable result. The typed {@code api-game} artifact is optional: when the
 * class is absent the base check is unaffected, but when it is present its
 * {@code VERSION} must be compatible.
 *
 * <p><strong>Only Minecraft 26.3 is implemented.</strong> This class is the
 * scaffold for a future multi-version path. Adding a second Minecraft version
 * requires new hook specifications and Mixin refmap work documented in
 * {@code docs/platform-ops.md}; a matrix row alone is not sufficient.
 */
public final class VersionChecks {

    /**
     * The outcome of one boot-time version check.
     *
     * @param compatible {@code true} when the running API, game version, and
     *                   present typed game API are all accepted
     * @param apiLevel   the support level of {@link WeaverApi#VERSION}
     * @param gameLevel  the support level of the supplied game version and hook
     *                   target
     * @param message    a stable one-line diagnostic suitable for boot logs
     */
    public record Result(boolean compatible, CompatibilityMatrix.Level apiLevel,
                         CompatibilityMatrix.Level gameLevel, String message) {

        /**
         * Validates the result fields.
         *
         * @throws NullPointerException when a level or the message is
         *                              {@code null}
         */
        public Result {
            Objects.requireNonNull(apiLevel, "apiLevel");
            Objects.requireNonNull(gameLevel, "gameLevel");
            Objects.requireNonNull(message, "message");
        }
    }

    private static final String GAME_API_CLASS = "net.weaver.api.game.WeaverGameApi";

    private VersionChecks() {
    }

    /**
     * Checks the running API, the supplied game version, and the optional typed
     * game API.
     *
     * <p>The API level comes from the compatibility matrix for
     * {@link WeaverApi#VERSION}. The game level comes from the matrix for
     * {@code gameVersion} used as both the game id and the hook target. When
     * the typed game API class is present in {@code gameApiLoader}, its
     * {@code VERSION} must be compatible with {@link WeaverApi#GAME_VERSION};
     * when the class is absent, the optional artifact is simply reported as
     * absent. This method catches every failure and never throws.
     *
     * @param gameApiLoader the class loader that may contain the typed game
     *                      API, or {@code null}
     * @param gameVersion   the detected Minecraft version id, or {@code null}
     * @return the check result; never {@code null}
     */
    public static Result check(ClassLoader gameApiLoader, String gameVersion) {
        CompatibilityMatrix.Level apiLevel =
                CompatibilityMatrix.levelForApi(WeaverApi.VERSION);
        CompatibilityMatrix.Level gameLevel =
                CompatibilityMatrix.levelForGame(gameVersion, gameVersion);
        GameApiProbe probe = GameApiProbe.inspect(gameApiLoader);
        boolean compatible = apiLevel != CompatibilityMatrix.Level.UNSUPPORTED
                && gameLevel != CompatibilityMatrix.Level.UNSUPPORTED
                && (!probe.present() || probe.compatible());
        String message = "weaver version check: api=" + WeaverApi.VERSION
                + " (" + apiLevel + ")"
                + " game=" + String.valueOf(gameVersion) + " (" + gameLevel + ")"
                + " typedGameApi=" + probe.describe()
                + " compatible=" + compatible;
        return new Result(compatible, apiLevel, gameLevel, message);
    }

    private record GameApiProbe(boolean present, String version, boolean compatible) {

        static GameApiProbe inspect(ClassLoader loader) {
            if (loader == null) {
                return new GameApiProbe(false, null, false);
            }
            try {
                Class<?> type = Class.forName(GAME_API_CLASS, false, loader);
                Field field = type.getField("VERSION");
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    return new GameApiProbe(true, null, false);
                }
                Object value = field.get(null);
                if (!(value instanceof String version)) {
                    return new GameApiProbe(true, null, false);
                }
                return new GameApiProbe(true, version, WeaverApi.isCompatibleGame(version));
            } catch (ClassNotFoundException missing) {
                return new GameApiProbe(false, null, false);
            } catch (Throwable failure) {
                return new GameApiProbe(true, null, false);
            }
        }

        String describe() {
            if (!present) {
                return "absent (optional)";
            }
            if (version == null) {
                return "present (invalid VERSION)";
            }
            return version + (compatible ? " (compatible)" : " (incompatible)");
        }
    }
}
