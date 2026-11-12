package net.weaver.api.data;

import java.util.Objects;
import java.util.function.Supplier;
import net.weaver.api.capability.AttachmentType;
import net.weaver.api.capability.Capability;
import net.weaver.api.capability.CapabilityCodec;
import net.weaver.api.capability.CapabilityException;
import net.weaver.api.capability.CapabilityId;
import net.weaver.api.capability.CapabilitySync;

/**
 * A typed capability key whose values are persisted through the ItemStack
 * {@code minecraft:custom_data} data component.
 *
 * <p>This is not a new game data component type registration. Weaver stores the
 * encoded value inside the existing custom-data component, so the value travels
 * with the stack only when the game itself synchronizes that stack.
 * BlockEntity, Entity, Level, and Chunk holders are runtime-only in 2.2 and do
 * not receive persistence through this key.
 *
 * <p>Every {@link Capability} method delegates to an internal
 * {@link AttachmentType}, which is exposed through {@link #attachmentType()}.
 *
 * @param <T> the value type
 */
public final class DataComponentKey<T> implements Capability<T> {

    private final AttachmentType<T> delegate;

    private DataComponentKey(AttachmentType<T> delegate) {
        this.delegate = delegate;
    }

    /**
     * Starts a builder for a data component key.
     *
     * @param id   the capability id in {@code namespace:path} form
     * @param type the runtime value type
     * @param <T>  the value type
     * @return a new builder
     * @throws NullPointerException when {@code id} or {@code type} is {@code null}
     */
    public static <T> Builder<T> builder(String id, Class<T> type) {
        return new Builder<>(id, type);
    }

    /**
     * Creates a data component key with the given codec and encoded-size bound.
     *
     * <p>The default value is {@code null}, {@link #copyOnItemCopy()} is
     * {@code true}, and {@link #sync()} is {@link CapabilitySync#ITEM_STACK}.
     *
     * @param id              the capability id in {@code namespace:path} form
     * @param type            the runtime value type
     * @param codec           the non-null codec
     * @param maxEncodedBytes the positive encoded-size bound, at most 16384
     * @param <T>             the value type
     * @return the data component key
     * @throws NullPointerException when {@code codec} is {@code null}
     * @throws CapabilityException  when the id is invalid or the bound is invalid
     */
    public static <T> DataComponentKey<T> of(String id, Class<T> type,
            CapabilityCodec<T> codec, int maxEncodedBytes) {
        Objects.requireNonNull(codec, "codec");
        return new DataComponentKey<>(
                AttachmentType.builder(id, type).codec(codec, maxEncodedBytes).build());
    }

    /**
     * Returns the internal attachment type backing this key.
     *
     * @return the delegate capability
     */
    public AttachmentType<T> attachmentType() {
        return delegate;
    }

    @Override
    public CapabilityId key() {
        return delegate.key();
    }

    @Override
    public Class<T> valueType() {
        return delegate.valueType();
    }

    @Override
    public T defaultValue() {
        return delegate.defaultValue();
    }

    @Override
    public CapabilityCodec<T> codec() {
        return delegate.codec();
    }

    @Override
    public int maxEncodedBytes() {
        return delegate.maxEncodedBytes();
    }

    @Override
    public boolean copyOnItemCopy() {
        return delegate.copyOnItemCopy();
    }

    @Override
    public CapabilitySync sync() {
        return delegate.sync();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    /**
     * Builder for a {@link DataComponentKey}.
     *
     * <p>A codec must be configured before {@link #build()}; the copy flag
     * defaults to {@code true} and the sync declaration defaults to
     * {@link CapabilitySync#ITEM_STACK}.
     *
     * @param <T> the value type
     */
    public static final class Builder<T> {

        private final String id;
        private final AttachmentType.Builder<T> delegate;
        private boolean codecConfigured;

        private Builder(String id, Class<T> type) {
            this.id = Objects.requireNonNull(id, "id");
            this.delegate = AttachmentType.builder(id, type);
        }

        /**
         * Sets a constant default value.
         *
         * <p>This replaces any previously configured default supplier.
         *
         * @param value the default value, possibly {@code null}
         * @return this builder
         */
        public Builder<T> defaultValue(T value) {
            delegate.defaultValue(value);
            return this;
        }

        /**
         * Sets a default value supplier evaluated on every default read.
         *
         * <p>This replaces any previously configured constant default.
         *
         * @param supplier the non-null default supplier
         * @return this builder
         * @throws NullPointerException when {@code supplier} is {@code null}
         */
        public Builder<T> defaultSupplier(Supplier<T> supplier) {
            delegate.defaultSupplier(supplier);
            return this;
        }

        /**
         * Sets the codec and the maximum encoded payload size.
         *
         * @param codec           the non-null codec
         * @param maxEncodedBytes the positive encoded-size bound, at most 16384
         * @return this builder
         * @throws NullPointerException when {@code codec} is {@code null}
         */
        public Builder<T> codec(CapabilityCodec<T> codec, int maxEncodedBytes) {
            delegate.codec(codec, maxEncodedBytes);
            codecConfigured = true;
            return this;
        }

        /**
         * Sets whether an ItemStack copy keeps the value.
         *
         * @param copy whether the value follows an ItemStack copy
         * @return this builder
         */
        public Builder<T> copyOnItemCopy(boolean copy) {
            delegate.copyOnItemCopy(copy);
            return this;
        }

        /**
         * Sets the synchronization declaration.
         *
         * @param sync the non-null synchronization declaration
         * @return this builder
         * @throws NullPointerException when {@code sync} is {@code null}
         */
        public Builder<T> sync(CapabilitySync sync) {
            delegate.sync(sync);
            return this;
        }

        /**
         * Builds the immutable data component key.
         *
         * @return the built key
         * @throws CapabilityException when no codec was configured or the id or
         *                             encoded-size bound is invalid
         */
        public DataComponentKey<T> build() {
            if (!codecConfigured) {
                throw new CapabilityException(
                        "DataComponentKey requires a codec: " + id);
            }
            return new DataComponentKey<>(delegate.build());
        }
    }
}
