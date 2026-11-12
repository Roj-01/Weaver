package net.weaver.gradle.dependency;

import java.util.List;
import java.util.Objects;

/**
 * A dependency-resolution conflict or validation failure.
 *
 * <p>Kinds are {@code module-version} for multiple requested versions,
 * {@code missing} for unresolved required modules, {@code mod-id} for duplicate
 * mod ids, and {@code invalid} for unreadable or malformed descriptors.
 *
 * @param kind the conflict kind
 * @param module the affected module ({@code group:name}), mod id, or file
 * @param selected the selected version, or an empty string when nothing was selected
 * @param requested the distinct requested versions, sorted and never null
 * @param message a single-line human-readable description
 */
public record ModConflict(
        String kind,
        String module,
        String selected,
        List<String> requested,
        String message) {

    /**
     * Creates a conflict with non-null fields.
     *
     * @throws NullPointerException if the kind is null
     */
    public ModConflict {
        kind = Objects.requireNonNull(kind, "kind");
        module = module == null ? "" : module;
        selected = selected == null ? "" : selected;
        requested = requested == null ? List.of() : List.copyOf(requested);
        message = message == null ? "" : message;
    }
}
