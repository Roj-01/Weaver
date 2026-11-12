package net.weaver.minimap.api;

/**
 * A client-thread assertion supplied by the integrator.
 *
 * <p>{@link MinimapApi} calls this before every registration, retrieval, and
 * invocation. Implementations typically throw an {@link IllegalStateException}
 * when the current thread is not the client thread. Use {@code () -> { }} when
 * no enforcement is desired, for example in unit tests.</p>
 */
@FunctionalInterface
public interface ThreadCheck
{
    /**
     * Verifies that the caller is on the client thread.
     *
     * @throws IllegalStateException when the caller is on another thread
     */
    void checkClientThread();
}
