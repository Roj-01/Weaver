package net.weaver.core.version;

import net.weaver.api.WeaverApi;

/**
 * A Minecraft game version and the hook target it uses.
 *
 * <p>The game version identifies the release (for example {@code "26.3"}),
 * while the hook target identifies the hook specification set that was written
 * against it. They are equal today, but separating them allows a compatible
 * game release to keep an older hook set.
 *
 * @param id         the game version id, never blank
 * @param hookTarget the hook target id, never blank
 */
public record GameVersion(String id, String hookTarget) {

    /**
     * The game version and hook target this platform build implements.
     */
    public static final GameVersion CURRENT = new GameVersion("26.3", "26.3");

    /**
     * Validates and normalizes the ids.
     *
     * @throws IllegalArgumentException when an id is {@code null} or blank
     */
    public GameVersion {
        id = requireText(id, "game version id");
        hookTarget = requireText(hookTarget, "hook target");
    }

    /**
     * Parses a game version id and uses the same id as the hook target.
     *
     * @param id the game version id, for example {@code "26.3"}
     * @return a game version whose hook target equals {@code id}
     * @throws IllegalArgumentException when {@code id} is {@code null} or blank
     */
    public static GameVersion parse(String id) {
        return new GameVersion(id, id);
    }

    /**
     * Tests whether this game version and hook target are supported by the
     * compatibility matrix for the running Weaver API version.
     *
     * @return {@code true} when the matrix supports this pair
     */
    public boolean isSupported() {
        return CompatibilityMatrix.supports(WeaverApi.VERSION, id, hookTarget);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be null or blank");
        }
        return value.trim();
    }
}
