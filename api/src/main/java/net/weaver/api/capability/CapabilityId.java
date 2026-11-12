package net.weaver.api.capability;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A namespace-qualified identifier for a {@link Capability}.
 *
 * <p>The namespace identifies the mod or subsystem that owns the capability and
 * the path identifies the capability within that namespace. Both parts are
 * validated when the id is created, so an id always has the form
 * {@code namespace:path}. Ids are immutable, value-comparable, and ordered by
 * namespace and then by path.
 */
public record CapabilityId(String namespace, String path) implements Comparable<CapabilityId> {

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    /**
     * Creates and validates a capability id.
     *
     * @param namespace the namespace part, matching {@code [a-z0-9_.-]+}
     * @param path      the path part, matching {@code [a-z0-9_./-]+}
     * @throws NullPointerException     when either part is {@code null}
     * @throws IllegalArgumentException when either part does not match its pattern
     */
    public CapabilityId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid capability namespace: " + namespace);
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid capability path: " + path);
        }
    }

    /**
     * Creates a validated capability id from its two parts.
     *
     * @param namespace the namespace part, matching {@code [a-z0-9_.-]+}
     * @param path      the path part, matching {@code [a-z0-9_./-]+}
     * @return the validated id
     * @throws NullPointerException     when either part is {@code null}
     * @throws IllegalArgumentException when either part does not match its pattern
     */
    public static CapabilityId of(String namespace, String path) {
        return new CapabilityId(namespace, path);
    }

    /**
     * Parses a capability id in {@code namespace:path} form.
     *
     * <p>The colon is mandatory. The namespace is everything before the first
     * colon and the path is everything after it, so a second colon makes the
     * path invalid.
     *
     * @param id the id to parse, for example {@code "example:health/current"}
     * @return the validated id
     * @throws NullPointerException     when {@code id} is {@code null}
     * @throws IllegalArgumentException when the colon is absent or either part is
     *                                  invalid
     */
    public static CapabilityId parse(String id) {
        Objects.requireNonNull(id, "id");
        int separator = id.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("Capability id must contain ':': " + id);
        }
        return new CapabilityId(id.substring(0, separator), id.substring(separator + 1));
    }

    /**
     * Returns the namespace part of this id.
     *
     * @return the namespace
     */
    @Override
    public String namespace() {
        return namespace;
    }

    /**
     * Returns the path part of this id.
     *
     * @return the path
     */
    @Override
    public String path() {
        return path;
    }

    /**
     * Returns the full id in {@code namespace:path} form.
     *
     * @return the namespace and path joined by a colon
     */
    public String id() {
        return namespace + ":" + path;
    }

    /**
     * Compares this id to another by namespace and then by path.
     *
     * @param other the id to compare against
     * @return a negative integer, zero, or a positive integer when this id is
     *         less than, equal to, or greater than {@code other}
     */
    @Override
    public int compareTo(CapabilityId other) {
        int namespaceOrder = namespace.compareTo(other.namespace);
        return namespaceOrder != 0 ? namespaceOrder : path.compareTo(other.path);
    }
}
