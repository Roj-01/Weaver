package net.weaver.minimap.map;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LruCacheTest
{
    @Test
    void evictsLeastRecentlyUsedEntry()
    {
        List<String> evicted = new ArrayList<>();
        LruCache<String, String> cache = new LruCache<>(2, evicted::add);

        cache.put("a", "A");
        cache.put("b", "B");
        assertEquals("A", cache.get("a"));
        cache.put("c", "C");

        assertTrue(cache.containsKey("a"));
        assertTrue(cache.containsKey("c"));
        assertFalse(cache.containsKey("b"));
        assertEquals(List.of("B"), evicted);
        assertEquals(2, cache.size());
    }

    @Test
    void shrinkingTheBudgetEvictsOldestEntries()
    {
        List<String> evicted = new ArrayList<>();
        LruCache<String, String> cache = new LruCache<>(4, evicted::add);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.put("c", "C");
        cache.put("d", "D");

        cache.setMaxEntries(2);

        assertEquals(2, cache.size());
        assertEquals(List.of("A", "B"), evicted);
        assertTrue(cache.containsKey("c"));
        assertTrue(cache.containsKey("d"));
    }

    @Test
    void removeAndClearDoNotCallTheEvictionListener()
    {
        List<String> evicted = new ArrayList<>();
        LruCache<String, String> cache = new LruCache<>(2, evicted::add);
        cache.put("a", "A");

        assertEquals("A", cache.remove("a"));
        assertNull(cache.remove("a"));
        cache.put("b", "B");
        cache.clear();

        assertTrue(cache.isEmpty());
        assertTrue(evicted.isEmpty());
    }
}
