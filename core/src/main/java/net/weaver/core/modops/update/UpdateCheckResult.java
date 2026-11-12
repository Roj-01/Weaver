package net.weaver.core.modops.update;

import java.util.List;
import java.util.Objects;

/**
 * Immutable result of an update check.
 *
 * @param status  overall outcome
 * @param updates available updates; empty when none or unknown
 * @param message human-readable detail, or an empty string
 */
public record UpdateCheckResult(UpdateStatus status, List<ModUpdate> updates, String message) {

    /**
     * Creates a result and copies the update list.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public UpdateCheckResult {
        Objects.requireNonNull(status, "status");
        updates = List.copyOf(Objects.requireNonNull(updates, "updates"));
        Objects.requireNonNull(message, "message");
    }
}
