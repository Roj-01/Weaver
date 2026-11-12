package net.weaver.api.registry;

import java.util.Objects;

/** A namespace-qualified tag inside one registry. */
public final class TagKey<T> implements Comparable<TagKey<T>> {

    private final RegistryKey<?> registry;
    private final RegistryKey<T> id;

    private TagKey(RegistryKey<?> registry, RegistryKey<T> id) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.id = Objects.requireNonNull(id, "id");
    }

    public static <T> TagKey<T> of(RegistryKey<?> registry, String namespace, String path) {
        return new TagKey<>(registry, RegistryKey.of(namespace, path));
    }

    public static <T> TagKey<T> of(RegistryKey<?> registry, String id) {
        return new TagKey<>(registry, RegistryKey.parse(id));
    }

    public RegistryKey<?> registry() {
        return registry;
    }

    public RegistryKey<T> id() {
        return id;
    }

    public String namespace() {
        return id.namespace();
    }

    public String path() {
        return id.path();
    }

    @Override
    public int compareTo(TagKey<T> other) {
        int registryOrder = registry.id().compareTo(other.registry.id());
        return registryOrder != 0 ? registryOrder : id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TagKey<?> key)) {
            return false;
        }
        return registry.equals(key.registry) && id.equals(key.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registry, id);
    }

    @Override
    public String toString() {
        return registry.id() + "#" + id.id();
    }
}
