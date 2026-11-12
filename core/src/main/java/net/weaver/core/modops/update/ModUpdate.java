package net.weaver.core.modops.update;

import java.util.Objects;

/**
 * One available update.
 *
 * @param id               mod id
 * @param installedVersion version currently installed
 * @param availableVersion version available from the update source
 * @param downloadUrl      URL to the new artifact, or an empty string when unknown
 * @param notes            release notes, or an empty string when unavailable
 */
public record ModUpdate(
        String id,
        String installedVersion,
        String availableVersion,
        String downloadUrl,
        String notes) {

    /**
     * Creates an update entry.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public ModUpdate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(installedVersion, "installedVersion");
        Objects.requireNonNull(availableVersion, "availableVersion");
        Objects.requireNonNull(downloadUrl, "downloadUrl");
        Objects.requireNonNull(notes, "notes");
    }
}
