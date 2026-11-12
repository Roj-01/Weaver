package net.weaver.api.registry;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** An immutable read-only view over one registry tag. */
public final class TagView<T> {

    private final TagKey<T> key;
    private final List<T> values;

    TagView(TagKey<T> key, List<T> values) {
        this.key = Objects.requireNonNull(key, "key");
        this.values = List.copyOf(values);
    }

    public TagKey<T> key() {
        return key;
    }

    public List<T> values() {
        return values;
    }

    public Collection<T> contents() {
        return values;
    }

    public boolean contains(T value) {
        return values.contains(value);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int size() {
        return values.size();
    }

    @Override
    public String toString() {
        return "TagView{" + key + ", size=" + values.size() + "}";
    }
}
