package net.weaver.minimap.map;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Small access-ordered least-recently-used cache.
 *
 * <p>Not thread-safe; the map engine confines it to the client thread. An
 * optional eviction listener runs synchronously from {@link #put} or
 * {@link #setMaxEntries} for every entry that leaves the cache.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
final class LruCache<K, V>
{
    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, 0.75F, true);
    private final Consumer<V> evictionListener;
    private int maxEntries;

    LruCache(int maxEntries, Consumer<V> evictionListener)
    {
        this.maxEntries = Math.max(1, maxEntries);
        this.evictionListener = evictionListener;
    }

    V get(K key)
    {
        return entries.get(key);
    }

    boolean containsKey(K key)
    {
        return entries.containsKey(key);
    }

    void put(K key, V value)
    {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        entries.put(key, value);
        evictOverflow();
    }

    V remove(K key)
    {
        return entries.remove(key);
    }

    void setMaxEntries(int maxEntries)
    {
        this.maxEntries = Math.max(1, maxEntries);
        evictOverflow();
    }

    int size()
    {
        return entries.size();
    }

    boolean isEmpty()
    {
        return entries.isEmpty();
    }

    Collection<V> values()
    {
        return entries.values();
    }

    void clear()
    {
        entries.clear();
    }

    private void evictOverflow()
    {
        while (entries.size() > maxEntries)
        {
            Iterator<Map.Entry<K, V>> iterator = entries.entrySet().iterator();
            Map.Entry<K, V> eldest = iterator.next();
            iterator.remove();
            if (evictionListener != null)
            {
                evictionListener.accept(eldest.getValue());
            }
        }
    }
}
