package net.weaver.api.client;

/**
 * An immutable snapshot of one client tick phase.
 *
 * <p>The record is safe to share between threads, but the client object is not.
 * Use the context only on the client thread and do not retain the client
 * reference past the callback.
 *
 * @param tick      the client tick counter
 * @param pre       {@code true} for the start-of-tick phase, {@code false} for
 *                  the end-of-tick phase
 * @param minecraft the client instance, normally non-null in a live game
 */
public record ClientTickContext(long tick, boolean pre, Object minecraft)
{
}
