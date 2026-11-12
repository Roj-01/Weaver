package net.weaver.core.version;

import net.weaver.api.WeaverApi;

import java.util.Objects;

/**
 * A concrete API and game target pair.
 *
 * <p>A platform target combines the Weaver API version a mod was built against
 * with the Minecraft game version and hook target it expects.
 *
 * @param apiVersion the Weaver API version, never blank
 * @param game       the game version and hook target, never {@code null}
 */
public record PlatformTarget(String apiVersion, GameVersion game) {

    /**
     * Validates the target.
     *
     * @throws IllegalArgumentException when {@code apiVersion} is {@code null}
     *                                  or blank
     * @throws NullPointerException     when {@code game} is {@code null}
     */
    public PlatformTarget {
        if (apiVersion == null || apiVersion.isBlank()) {
            throw new IllegalArgumentException("apiVersion must not be null or blank");
        }
        apiVersion = apiVersion.trim();
        Objects.requireNonNull(game, "game");
    }

    /**
     * Returns the target implemented by this build: the current API version
     * and {@link GameVersion#CURRENT}.
     *
     * @return the current platform target
     */
    public static PlatformTarget current() {
        return new PlatformTarget(WeaverApi.VERSION, GameVersion.CURRENT);
    }

    /**
     * Tests whether the compatibility matrix supports this API/game pair.
     *
     * <p>{@link CompatibilityMatrix.Level#EXPERIMENTAL} is treated as
     * supported (usable with a warning), while
     * {@link CompatibilityMatrix.Level#UNSUPPORTED} is not.
     *
     * @return {@code true} when the matrix supports this target
     */
    public boolean supported() {
        return CompatibilityMatrix.supports(apiVersion, game.id(), game.hookTarget());
    }
}
