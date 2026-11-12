package net.weaver.api.client;

import java.util.Objects;

/**
 * An immutable snapshot of one client resource reload phase.
 *
 * <p>The record is safe to share between threads, but the source object is not.
 * Use the context only on the client thread and do not retain the source past
 * the callback.
 *
 * @param phase  the reload phase, never {@code null}
 * @param source the reload source object, never {@code null}
 * @param client whether this snapshot describes the logical client reload
 */
public record ResourceReloadContext(Phase phase, Object source, boolean client)
{
    /**
     * The phases observed during a client resource reload.
     */
    public enum Phase
    {
        /** The reload has started; old resources may still be in use. */
        START,

        /** The reload finished and new resources are active. */
        FINISHED,

        /** The reload failed; the previous resources may remain active. */
        FAILED
    }

    /**
     * Validates the phase and source.
     *
     * @throws NullPointerException when {@code phase} or {@code source} is
     *                              {@code null}
     */
    public ResourceReloadContext
    {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(source, "source");
    }
}
