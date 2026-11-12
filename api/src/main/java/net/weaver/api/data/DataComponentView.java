package net.weaver.api.data;

import java.util.Objects;
import java.util.Optional;
import net.weaver.api.capability.AttachmentRegistry;

/**
 * Typed convenience view over one holder.
 *
 * <p>A view reads and writes {@link DataComponentKey} values through the global
 * {@link AttachmentRegistry}. Values are never cached, so every call observes
 * the current registry state. Holder identity follows the registry rules: two
 * equal but distinct holder instances are different holders.
 */
public interface DataComponentView {

    /**
     * Returns the value for a key.
     *
     * @param key the non-null key
     * @param <T> the value type
     * @return the explicit value, the key default, or {@code null}
     */
    <T> T get(DataComponentKey<T> key);

    /**
     * Returns the explicit value for a key.
     *
     * @param key the non-null key
     * @param <T> the value type
     * @return the explicit value, or an empty optional when absent
     */
    <T> Optional<T> find(DataComponentKey<T> key);

    /**
     * Stores a value and returns the previous explicit value.
     *
     * @param key   the non-null key
     * @param value the value to store, or {@code null} to remove
     * @param <T>   the value type
     * @return the previous explicit value, or {@code null}
     */
    <T> T set(DataComponentKey<T> key, T value);

    /**
     * Removes the explicit value and returns it.
     *
     * @param key the non-null key
     * @param <T> the value type
     * @return the removed explicit value, or {@code null} when none was present
     */
    <T> T remove(DataComponentKey<T> key);

    /**
     * Tests whether an explicit value is present.
     *
     * @param key the non-null key
     * @param <T> the value type
     * @return {@code true} when an explicit value is present
     */
    <T> boolean has(DataComponentKey<T> key);

    /**
     * Creates a view backed by the global attachment registry.
     *
     * @param holder the non-null holder
     * @return a view over the holder
     * @throws NullPointerException when {@code holder} is {@code null}
     */
    static DataComponentView of(Object holder) {
        Objects.requireNonNull(holder, "holder");
        AttachmentRegistry registry = AttachmentRegistry.global();
        return new DataComponentView() {
            @Override
            public <T> T get(DataComponentKey<T> key) {
                return registry.get(holder, key);
            }

            @Override
            public <T> Optional<T> find(DataComponentKey<T> key) {
                return registry.find(holder, key);
            }

            @Override
            public <T> T set(DataComponentKey<T> key, T value) {
                return registry.set(holder, key, value);
            }

            @Override
            public <T> T remove(DataComponentKey<T> key) {
                return registry.remove(holder, key);
            }

            @Override
            public <T> boolean has(DataComponentKey<T> key) {
                return registry.has(holder, key);
            }
        };
    }
}
