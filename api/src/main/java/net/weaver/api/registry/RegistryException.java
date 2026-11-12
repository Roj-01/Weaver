package net.weaver.api.registry;

/** Signals invalid registry usage or a failed deferred registration. */
public final class RegistryException extends RuntimeException {

    public RegistryException(String message) {
        super(message);
    }

    public RegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
