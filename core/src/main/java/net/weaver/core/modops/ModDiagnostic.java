package net.weaver.core.modops;

import java.util.Objects;

/**
 * One structured loader diagnostic attached to a {@link ModList} report.
 *
 * <p>A diagnostic never changes loading by itself; it records what the loader
 * observed so that a CLI, UI, or log processor can present the same information
 * that was printed to the loader log.
 *
 * @param severity how serious the diagnostic is
 * @param type     the diagnostic category
 * @param subject  the mod id, class entry, or file name the diagnostic is about
 * @param message  a human-readable explanation with the relevant metadata
 */
public record ModDiagnostic(Severity severity, Type type, String subject, String message) {

    /**
     * Creates a diagnostic and validates that no component is {@code null}.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public ModDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(message, "message");
    }

    /** Severity of a diagnostic. */
    public enum Severity {

        /** Informational only; loading is unaffected. */
        INFO,

        /** The loader continued, but something deserves attention. */
        WARN,

        /** A fatal loader policy was violated; loading was aborted. */
        FATAL
    }

    /** Category of a diagnostic. */
    public enum Type {

        /** A mod was skipped because its declared side does not match the process side. */
        WRONG_SIDE,

        /** Two discovered jars declare the same mod id. */
        DUPLICATE_ID,

        /** A jar or descriptor could not be read. */
        READ_FAILED,

        /** A required dependency is absent. */
        MISSING_DEPENDENCY,

        /** A required dependency exists with an incompatible version. */
        VERSION_MISMATCH,

        /** An optional dependency exists with an incompatible version. */
        OPTIONAL_VERSION,

        /** A mod breaks another discovered mod. */
        BREAKS,

        /** The dependency graph contains a cycle. */
        CYCLE,

        /** Two accepted mods ship the same class entry. */
        DUPLICATE_CLASS,

        /** A mod jar physically contains a class from the loader API namespace. */
        API_SHADOW,

        /** A loader policy rejected the load or changed its severity. */
        POLICY
    }
}
