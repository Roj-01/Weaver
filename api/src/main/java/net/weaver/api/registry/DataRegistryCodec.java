package net.weaver.api.registry;

import java.util.Map;

/**
 * Decodes one parsed JSON object into a value for a {@link DataRegistry}.
 *
 * <p>The data map is the root object of one registry entry file. JSON numbers
 * are represented as {@link Double}, nested JSON objects as
 * {@link Map} instances, and JSON arrays as {@link java.util.List} instances.
 * The map may be shared by several callers and must not be mutated. Return a
 * new value for every call; the registry stores the returned value directly.</p>
 *
 * @param <T> the entry type
 */
@FunctionalInterface
public interface DataRegistryCodec<T>
{
    /**
     * Decodes one entry.
     *
     * <p>Throwing from this method, or returning {@code null}, marks the entry
     * as {@link DataRegistry.Status#DECODE_FAILED}; an already loaded entry at
     * the same id keeps its previous value.</p>
     *
     * @param entryId the full {@code namespace:path} entry id
     * @param data the parsed root JSON object, never {@code null} and read-only
     * @return the decoded value, not {@code null}
     */
    T decode(String entryId, Map<String, Object> data);
}
