package net.weaver.gradle.jij;

import java.util.Objects;

/**
 * One nested mod jar declared in {@code META-INF/weaver/jij.json}.
 *
 * @param entryName the nested jar entry inside the parent jar
 * @param id the mod id
 * @param version the mod version
 * @param optional whether the nested mod is optional at load time
 */
public record JijJar(String entryName, String id, String version, boolean optional) {

    /**
     * Creates a nested jar declaration.
     *
     * @throws NullPointerException if {@code entryName}, {@code id}, or
     *         {@code version} is {@code null}
     * @throws IllegalArgumentException if a value is blank or the entry name is
     *         unsafe
     */
    public JijJar {
        Objects.requireNonNull(entryName, "entryName");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        if (id.isBlank()) {
            throw new IllegalArgumentException("nested jar id is blank");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("nested jar version is blank for " + id);
        }
        JijLayout.validateEntryName(entryName);
    }
}
