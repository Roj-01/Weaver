package net.weaver.api.capability;

/**
 * Unchecked failure raised by the Weaver capability API.
 *
 * <p>It covers invalid ids, duplicate registration, wrong value types, oversize
 * payloads, missing stores, and failures reported by a store or a reflective
 * bridge. Failures from {@link net.weaver.api.data.DataCodecs DataCodecs} are
 * reported with this type as well.
 */
public class CapabilityException extends RuntimeException {

    /**
     * Creates an exception with the given message.
     *
     * @param message the detail message
     */
    public CapabilityException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public CapabilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
