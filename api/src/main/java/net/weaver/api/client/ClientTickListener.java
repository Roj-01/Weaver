package net.weaver.api.client;

/**
 * Receives the start or end phase of a client tick.
 *
 * <p>Listeners are registered through
 * {@link ClientRegistry#registerTickListener(ClientTickListener)} for the
 * start phase or
 * {@link ClientRegistry#registerTickListener(ClientTickListener, boolean)} for
 * an explicit phase. {@link #onTick(ClientTickContext)} is called on the client
 * thread.
 */
@FunctionalInterface
public interface ClientTickListener
{
    /**
     * Handles one tick phase.
     *
     * <p>Called on the client thread once per tick for the selected phase.
     * Exceptions should be isolated by the backend so one listener cannot stop
     * the tick loop.
     *
     * @param context the immutable tick snapshot, never {@code null}
     */
    void onTick(ClientTickContext context);
}
