package net.weaver.api.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An immutable, namespaced permission node such as {@code mymod:command/foo}.
 *
 * <p>A node has a namespace and a slash-separated path. Namespaces match
 * {@code [a-z0-9_.-]+}. Path segments match {@code [a-z0-9_.-]+} and are
 * separated by a single {@code /}. The single path {@code "*"} is reserved for
 * the namespace wildcard created by {@link #wildcard(String)}; it is the only
 * path that may contain an asterisk.
 *
 * <p>Hierarchy is path-based. {@code mymod:command/foo/bar} has the ancestors
 * {@code mymod:command/foo} and {@code mymod:command}, and its wildcard node is
 * {@code mymod:*}. The wildcard node has no ancestors. Nodes are value objects:
 * equality and hashing use the canonical {@code namespace:path} text, and
 * {@link #toString()} returns the same text.
 *
 * <p>This type is immutable and therefore thread-safe.
 */
public final class PermissionNode
{
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH_PATTERN =
            Pattern.compile("[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*");
    private static final String WILDCARD_PATH = "*";

    private final String namespace;
    private final String path;

    private PermissionNode(String namespace, String path)
    {
        this.namespace = namespace;
        this.path = path;
    }

    /**
     * Creates a permission node from a validated namespace and path.
     *
     * @param namespace the namespace, matching {@code [a-z0-9_.-]+}
     * @param path      the path, either {@code "*"} or slash-separated segments
     *                  matching {@code [a-z0-9_.-]+}
     * @return the node, never {@code null}
     * @throws NullPointerException     when {@code namespace} or {@code path} is
     *                                  {@code null}
     * @throws IllegalArgumentException when the namespace or path is malformed
     */
    public static PermissionNode of(String namespace, String path)
    {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE_PATTERN.matcher(namespace).matches())
        {
            throw new IllegalArgumentException("Invalid permission namespace: " + namespace);
        }
        if (!WILDCARD_PATH.equals(path) && !PATH_PATTERN.matcher(path).matches())
        {
            throw new IllegalArgumentException("Invalid permission path: " + path);
        }
        return new PermissionNode(namespace, path);
    }

    /**
     * Parses the canonical {@code namespace:path} form.
     *
     * <p>The text must contain exactly one colon and a non-empty namespace and
     * path. There is no implicit namespace fallback. Use
     * {@link #wildcard(String)} or {@code parse("namespace:*")} for a wildcard.
     *
     * @param text the canonical node text, for example
     *             {@code "mymod:command/foo"}
     * @return the parsed node, never {@code null}
     * @throws NullPointerException     when {@code text} is {@code null}
     * @throws IllegalArgumentException when the text is not a canonical node
     */
    public static PermissionNode parse(String text)
    {
        Objects.requireNonNull(text, "text");
        int separator = text.indexOf(':');
        if (separator <= 0 || separator == text.length() - 1)
        {
            throw new IllegalArgumentException(
                    "Invalid permission node, expected namespace:path: " + text);
        }
        if (text.indexOf(':', separator + 1) >= 0)
        {
            throw new IllegalArgumentException(
                    "Invalid permission node, expected exactly one colon: " + text);
        }
        return of(text.substring(0, separator), text.substring(separator + 1));
    }

    /**
     * Creates the namespace wildcard node {@code namespace:*}.
     *
     * @param namespace the namespace, matching {@code [a-z0-9_.-]+}
     * @return the wildcard node, never {@code null}
     * @throws NullPointerException     when {@code namespace} is {@code null}
     * @throws IllegalArgumentException when the namespace is malformed
     */
    public static PermissionNode wildcard(String namespace)
    {
        return of(namespace, WILDCARD_PATH);
    }

    /**
     * The namespace of this node.
     *
     * @return the namespace, never {@code null}
     */
    public String namespace()
    {
        return namespace;
    }

    /**
     * The path of this node.
     *
     * @return the path, never {@code null}
     */
    public String path()
    {
        return path;
    }

    /**
     * The immediate parent node, obtained by dropping the last path segment.
     *
     * <p>A single-segment path and the wildcard {@code "*"} have no parent.
     *
     * @return the parent, or an empty optional when this node has no parent
     */
    public Optional<PermissionNode> parent()
    {
        if (WILDCARD_PATH.equals(path))
        {
            return Optional.empty();
        }
        int slash = path.lastIndexOf('/');
        if (slash < 0)
        {
            return Optional.empty();
        }
        return Optional.of(new PermissionNode(namespace, path.substring(0, slash)));
    }

    /**
     * The ancestors of this node, nearest first, up to the single-segment root
     * node.
     *
     * <p>The list excludes this node and the namespace wildcard. For example,
     * {@code mymod:command/foo/bar} returns {@code mymod:command/foo} followed
     * by {@code mymod:command}. A single-segment node and the wildcard have no
     * ancestors. The returned list is immutable.
     *
     * @return the immutable ancestor list, never {@code null}
     */
    public List<PermissionNode> ancestors()
    {
        if (WILDCARD_PATH.equals(path))
        {
            return List.of();
        }
        List<PermissionNode> ancestors = new ArrayList<>();
        String current = path;
        while (true)
        {
            int slash = current.lastIndexOf('/');
            if (slash < 0)
            {
                break;
            }
            current = current.substring(0, slash);
            ancestors.add(new PermissionNode(namespace, current));
        }
        return List.copyOf(ancestors);
    }

    /**
     * The namespace wildcard node for this node's namespace.
     *
     * <p>This returns {@code namespace:*} and is the node consulted after the
     * exact node and its ancestors during permission evaluation.
     *
     * @return the wildcard node, never {@code null}
     */
    public PermissionNode wildcard()
    {
        return new PermissionNode(namespace, WILDCARD_PATH);
    }

    /**
     * Whether this node is the namespace wildcard {@code namespace:*}.
     *
     * @return {@code true} when the path is {@code "*"}
     */
    public boolean isWildcard()
    {
        return WILDCARD_PATH.equals(path);
    }

    /**
     * Compares this node with another object by canonical node text.
     *
     * @param other the object to compare
     * @return {@code true} when {@code other} is an equal permission node
     */
    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof PermissionNode node))
        {
            return false;
        }
        return namespace.equals(node.namespace) && path.equals(node.path);
    }

    /**
     * Returns the hash code of the canonical node text.
     *
     * @return the hash code
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(namespace, path);
    }

    /**
     * Returns the canonical {@code namespace:path} text.
     *
     * @return the canonical node text, never {@code null}
     */
    @Override
    public String toString()
    {
        return namespace + ":" + path;
    }
}
