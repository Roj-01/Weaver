package net.weaver.patcher;

/**
 * A deterministic, fail-closed patch error. The message is meant for the CLI
 * user and always names the class, member, or entry that caused the failure.
 */
public final class PatchException extends Exception {
    public PatchException(String message) {
        super(message);
    }

    public PatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
