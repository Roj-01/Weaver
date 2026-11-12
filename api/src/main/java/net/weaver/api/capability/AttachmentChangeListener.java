package net.weaver.api.capability;

/**
 * Observes completed attachment mutations on an {@link AttachmentRegistry}.
 *
 * <p>Listeners are called after a {@code set}, {@code remove}, or {@code clear}
 * operation has completed. They run on the thread that performed the mutation,
 * so a listener invoked from a game hook runs on the game thread. Listener
 * failures are isolated and recorded as registry diagnostics; they never undo
 * the mutation or block other listeners.</p>
 *
 * <p>Listeners must not call back into the registry in a way that mutates the
 * same holder and capability recursively.</p>
 */
@FunctionalInterface
public interface AttachmentChangeListener {

    /**
     * Called after one attachment mutation completes.
     *
     * @param change the completed change, never {@code null}
     */
    void changed(AttachmentChange change);
}
