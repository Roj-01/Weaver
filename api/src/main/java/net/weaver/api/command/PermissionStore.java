package net.weaver.api.command;

import java.util.Map;

/**
 * A persistence backend for explicit permission decisions.
 *
 * <p>A store serializes decisions keyed by a stable subject key produced by
 * {@link PermissionResolver#subjectKey(Object)} and then by permission node
 * text. The value is {@link Boolean#TRUE} for an explicit allow and
 * {@link Boolean#FALSE} for an explicit deny.
 *
 * <p>Implementations are called from the thread that mutates
 * {@link PermissionService}; they do not need to be fast, but they must be
 * thread-safe and must not retain the supplied map. A store failure is
 * isolated by the service: it is logged at most once and never propagates to
 * command execution.
 *
 * <p>The maps returned by {@link #load()} may be empty but must never be
 * {@code null}. Null keys or null values are ignored.
 */
public interface PermissionStore
{
    /**
     * Loads the persisted decision snapshot.
     *
     * <p>A missing or corrupt backing file should return an empty map rather
     * than throwing when the implementation can recover. A thrown exception is
     * treated by the service as an empty snapshot and is logged once.
     *
     * @return the decision snapshot, never {@code null}
     */
    Map<String, Map<String, Boolean>> load();

    /**
     * Persists a complete decision snapshot.
     *
     * <p>The supplied map is a defensive copy owned by the caller. Store
     * failures may be thrown; the service isolates and logs them.
     *
     * @param decisions the snapshot to persist, never {@code null}
     */
    void save(Map<String, Map<String, Boolean>> decisions);
}
