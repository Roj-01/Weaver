package net.weaver.api.client;

/**
 * Observes a client resource reload.
 *
 * <p>Listeners are registered through
 * {@link ClientRegistry#registerResourceReloadListener(ResourceReloadListener)}.
 * The backend invokes {@link #onReload(ResourceReloadContext)} for the start,
 * success, and failure phases of a client resource reload.
 */
@FunctionalInterface
public interface ResourceReloadListener
{
    /**
     * Handles one reload phase.
     *
     * <p>Called on the client thread. The start phase runs before new resources
     * are applied, the finished phase after a successful reload, and the failed
     * phase after a reload error. Exceptions should be isolated by the backend
     * so one listener cannot abort the reload.
     *
     * @param context the immutable reload snapshot, never {@code null}
     */
    void onReload(ResourceReloadContext context);
}
