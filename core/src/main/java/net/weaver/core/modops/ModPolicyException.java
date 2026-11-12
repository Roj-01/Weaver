package net.weaver.core.modops;

import java.util.List;
import java.util.Objects;

/**
 * Thrown when a fatal loader policy rejects discovery.
 *
 * <p>The exception carries every policy diagnostic that triggered it. The
 * loader closes any jars it opened before throwing, so callers do not own
 * resources when this exception escapes {@code ModLoader.discover}.
 */
public final class ModPolicyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<ModDiagnostic> diagnostics;

    /**
     * Creates an exception with a generated message.
     *
     * @param diagnostics triggering diagnostics
     * @throws NullPointerException if {@code diagnostics} is {@code null}
     */
    public ModPolicyException(List<ModDiagnostic> diagnostics) {
        this(messageFor(diagnostics), diagnostics);
    }

    /**
     * Creates an exception with an explicit message.
     *
     * @param message     exception message
     * @param diagnostics triggering diagnostics
     * @throws NullPointerException if {@code diagnostics} is {@code null}
     */
    public ModPolicyException(String message, List<ModDiagnostic> diagnostics) {
        super(message);
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    /**
     * Returns the policy diagnostics that triggered this exception.
     *
     * @return immutable diagnostic list
     */
    public List<ModDiagnostic> diagnostics() {
        return diagnostics;
    }

    private static String messageFor(List<ModDiagnostic> diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (diagnostics.isEmpty()) {
            return "mod policy violation";
        }
        ModDiagnostic first = diagnostics.get(0);
        String message = "mod policy violation: " + first.type()
                + " subject=" + first.subject() + " " + first.message();
        if (diagnostics.size() > 1) {
            message += " (+" + (diagnostics.size() - 1) + " more)";
        }
        return message;
    }
}
