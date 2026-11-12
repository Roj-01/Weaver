package net.weaver.api.registry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A live view over a game registry. Reads are safe after the registration phase;
 * writes are only allowed before the registry is frozen.
 */
public interface RegistryView<T> {

    RegistryKey<?> registryKey();

    String id();

    Object nativeRegistry();

    boolean isFrozen();

    boolean containsKey(RegistryKey<T> key);

    boolean containsKey(String id);

    T get(RegistryKey<T> key);

    T get(String id);

    Optional<T> getOptional(RegistryKey<T> key);

    Optional<RegistryEntry<T>> getEntry(RegistryKey<T> key);

    Optional<RegistryEntry<T>> getEntry(String id);

    Set<RegistryKey<T>> keySet();

    Collection<T> values();

    List<RegistryEntry<T>> entries();

    int size();

    RegistryEntry<T> register(RegistryKey<T> key, T value);

    RegistryEntry<T> register(String id, T value);

    TagView<T> getTag(TagKey<T> tag);
}
