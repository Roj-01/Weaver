package net.weaver.core.modops.update;

import java.util.Objects;

/**
 * One installed mod advertised to an update check.
 *
 * @param id      mod id
 * @param version installed version
 */
public record InstalledMod(String id, String version) {

    /**
     * Creates an installed-mod entry.
     *
     * @throws NullPointerException if {@code id} or {@code version} is {@code null}
     */
    public InstalledMod {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
    }
}
