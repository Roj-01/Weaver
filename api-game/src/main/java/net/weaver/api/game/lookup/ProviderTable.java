package net.weaver.api.game.lookup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Providers keyed by a game object (block, block entity type, entity type,
 * item) plus fallbacks. Copy on write: lookups read without locking, and
 * registration is expected during setup.
 */
final class ProviderTable<K, P> {
    private volatile Map<K, List<P>> byKey = Map.of();
    private volatile List<P> fallbacks = List.of();

    synchronized void add(K key, P provider) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(provider, "provider");
        Map<K, List<P>> next = new HashMap<>(byKey);
        List<P> list = new ArrayList<>(next.getOrDefault(key, List.of()));
        list.add(provider);
        next.put(key, List.copyOf(list));
        byKey = Map.copyOf(next);
    }

    synchronized void addFallback(P provider) {
        Objects.requireNonNull(provider, "provider");
        List<P> list = new ArrayList<>(fallbacks);
        list.add(provider);
        fallbacks = List.copyOf(list);
    }

    List<P> forKey(K key) {
        return byKey.getOrDefault(key, List.of());
    }

    List<P> fallbacks() {
        return fallbacks;
    }

    boolean has(K key) {
        return byKey.containsKey(key);
    }
}
