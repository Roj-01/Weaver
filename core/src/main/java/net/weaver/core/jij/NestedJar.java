package net.weaver.core.jij;

import java.util.Objects;

/**
 * One nested mod jar declared by {@code META-INF/weaver/jij.json}.
 *
 * <p>Instances are returned in manifest order. The id is not unique: the
 * scanner reports duplicate ids without silently removing entries.
 *
 * @param entryName the nested jar entry inside the parent jar
 * @param id the nested mod id
 * @param version the nested mod version
 * @param optional whether the nested mod is optional at load time
 */
public record NestedJar(String entryName, String id, String version, boolean optional) {

    /**
     * Creates a nested jar description.
     *
     * @throws NullPointerException if {@code entryName}, {@code id}, or
     *         {@code version} is {@code null}
     */
    public NestedJar {
        Objects.requireNonNull(entryName, "entryName");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
    }
}
