package net.weaver.gradle.dependency;

import java.io.File;
import java.util.Objects;

/**
 * A Weaver mod discovered during dependency resolution.
 *
 * <p>Repository artifacts contribute their Maven module coordinates, while
 * local jars use a {@code local:} coordinate. The version always comes from the
 * embedded descriptor, not from the artifact's selected version.
 *
 * @param id the stable mod id from the descriptor
 * @param version the mod version from the descriptor
 * @param file the jar the mod was read from
 * @param coordinates the Maven coordinates or {@code local:<file name>}
 * @param requested the requested version expression, or {@code -} when unknown
 * @param transitive whether the artifact was pulled in transitively
 * @param optional whether the mod came from the optional configuration
 */
public record ResolvedMod(
        String id,
        String version,
        File file,
        String coordinates,
        String requested,
        boolean transitive,
        boolean optional) {

    /**
     * Creates a resolved mod with non-null fields.
     *
     * @throws NullPointerException if the id or file is null
     */
    public ResolvedMod {
        id = Objects.requireNonNull(id, "id");
        version = version == null ? "" : version;
        file = Objects.requireNonNull(file, "file");
        coordinates = coordinates == null ? "" : coordinates;
        requested = requested == null || requested.isBlank() ? "-" : requested;
    }
}
