package net.weaver.api.client;

/**
 * Observes client chunk loads, chunk unloads, and block updates.
 *
 * <p>Listeners are registered through
 * {@link ClientRegistry#registerChunkListener(ClientChunkListener)}. The
 * built-in client chunk mixin dispatches the events on the client thread from
 * the game's chunk and block-update paths; no polling is involved.
 *
 * <p>The same listener instance may be registered only once. Registering an
 * identical instance again throws {@link ClientException}. Different listener
 * instances are independent even when they share a class.
 */
public interface ClientChunkListener
{
    /**
     * Called after a client chunk has been loaded.
     *
     * @param context the immutable chunk snapshot, never {@code null}
     */
    void onChunkLoad(ClientChunkContext context);

    /**
     * Called before a client chunk is unloaded.
     *
     * @param context the immutable chunk snapshot, never {@code null}
     */
    void onChunkUnload(ClientChunkContext context);

    /**
     * Called for a server-confirmed block update.
     *
     * <p>The event covers the single-block and section block-update packet
     * paths. Local predictions that have not been confirmed by the server are
     * not reported.
     *
     * @param context the immutable block snapshot, never {@code null}
     */
    void onBlockUpdate(ClientBlockContext context);
}
