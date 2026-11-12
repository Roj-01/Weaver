package net.weaver.api.registry;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bridge between the game-independent registry API and the concrete game
 * registry implementation. All arguments are opaque native objects.
 */
public interface RegistryBackend {

    boolean isAvailable();

    Set<String> registryIds();

    Object nativeRegistry(String registryId);

    Object register(Object nativeRegistry, String namespace, String path, Object value);

    Object get(Object nativeRegistry, String namespace, String path);

    boolean containsKey(Object nativeRegistry, String namespace, String path);

    List<String> keys(Object nativeRegistry);

    List<Map.Entry<String, Object>> entries(Object nativeRegistry);

    Object holder(Object nativeRegistry, Object value);

    Object identifier(String namespace, String path);

    default List<Object> tagValues(Object nativeRegistry, String registryId,
                                   String tagNamespace, String tagPath) {
        return List.of();
    }

    default void markFrozen(Object nativeRegistry) {
    }

    default boolean isFrozen(Object nativeRegistry) {
        return false;
    }
}
