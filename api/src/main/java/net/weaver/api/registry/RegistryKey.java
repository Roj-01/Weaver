package net.weaver.api.registry;

import java.util.Objects;
import java.util.regex.Pattern;

/** A namespace-qualified registry or entry identifier. */
public final class RegistryKey<T> implements Comparable<RegistryKey<T>> {

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    private final String namespace;
    private final String path;

    private RegistryKey(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static <T> RegistryKey<T> of(String namespace, String path) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid registry namespace: " + namespace);
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid registry path: " + path);
        }
        return new RegistryKey<>(namespace, path);
    }

    public static <T> RegistryKey<T> parse(String id) {
        Objects.requireNonNull(id, "id");
        int separator = id.indexOf(':');
        if (separator < 0) {
            return of("minecraft", id);
        }
        return of(id.substring(0, separator), id.substring(separator + 1));
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return path;
    }

    public String id() {
        return namespace + ":" + path;
    }

    public RegistryKey<T> withNamespace(String namespace) {
        return of(namespace, path);
    }

    public RegistryKey<T> withPath(String path) {
        return of(namespace, path);
    }

    public RegistryKey<T> withPrefix(String prefix) {
        return of(namespace, prefix + path);
    }

    public RegistryKey<T> withSuffix(String suffix) {
        return of(namespace, path + suffix);
    }

    @Override
    public int compareTo(RegistryKey<T> other) {
        int namespaceOrder = namespace.compareTo(other.namespace);
        return namespaceOrder != 0 ? namespaceOrder : path.compareTo(other.path);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RegistryKey<?> key)) {
            return false;
        }
        return namespace.equals(key.namespace) && path.equals(key.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    @Override
    public String toString() {
        return id();
    }
}
