package net.weaver.api.capability;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Immutable {@link Capability} implementation created through a builder or one
 * of the convenience factories.
 *
 * <p>The value type and id are fixed at construction. A codec makes the
 * capability persistable and requires a positive encoded-size bound. The bound
 * is at most {@code 16384} bytes for a value that can leave its side (declared
 * {@link CapabilitySync#ITEM_STACK} or {@link CapabilitySync#RUNTIME}); a value
 * declared {@link CapabilitySync#NONE} is only ever saved with its holder and
 * may declare up to {@code 1048576} bytes (1 MiB). Such a large value fits an
 * entity, block entity, level or chunk (which share a 2 MiB budget); on an item
 * stack, which the game sends to clients, a value must still encode to at most
 * {@code 16384} bytes. Without a codec the capability is runtime-only and
 * declares {@link CapabilitySync#NONE}; with a codec it defaults to
 * {@link CapabilitySync#ITEM_STACK} and {@link #copyOnItemCopy()} {@code true}.
 */
public final class AttachmentType<T> implements Capability<T> {

    private static final int MAX_ENCODED_BYTES = 16384;

    /** Bound allowed for a value declared {@link CapabilitySync#NONE}: saved, never sent. */
    private static final int MAX_SAVED_ONLY_BYTES = 1 << 20;

    private final CapabilityId key;
    private final Class<T> type;
    private final T defaultValue;
    private final Supplier<T> defaultSupplier;
    private final CapabilityCodec<T> codec;
    private final int maxEncodedBytes;
    private final boolean copyOnItemCopy;
    private final boolean copyOnDeath;
    private final CapabilitySync sync;

    private AttachmentType(CapabilityId key, Class<T> type, T defaultValue,
            Supplier<T> defaultSupplier, CapabilityCodec<T> codec, int maxEncodedBytes,
            boolean copyOnItemCopy, boolean copyOnDeath, CapabilitySync sync) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.defaultSupplier = defaultSupplier;
        this.codec = codec;
        this.maxEncodedBytes = maxEncodedBytes;
        this.copyOnItemCopy = copyOnItemCopy;
        this.copyOnDeath = copyOnDeath;
        this.sync = sync;
    }

    /**
     * Starts a builder for an attachment type.
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
     * Creates a runtime-only attachment type with no codec.
     *
     * <p>The default value is {@code null}, {@link #copyOnItemCopy()} is
     * {@code false}, and {@link #sync()} is {@link CapabilitySync#NONE}.
     *
     * @param id   the capability id in {@code namespace:path} form
     * @param type the runtime value type
     * @param <T>  the value type
     * @return the runtime-only capability
     * @throws CapabilityException when {@code id} is invalid
     */
    public static <T> AttachmentType<T> runtime(String id, Class<T> type) {
        return builder(id, type).build();
    }

    /**
     * Creates a persistent attachment type with the given codec.
     *
     * <p>The encoded-size bound is {@code 16384} bytes, the maximum allowed.
     * The default value is {@code null}, {@link #copyOnItemCopy()} is
     * {@code true}, and {@link #sync()} is {@link CapabilitySync#ITEM_STACK}.
     *
     * @param id    the capability id in {@code namespace:path} form
     * @param type  the runtime value type
     * @param codec the non-null codec
     * @param <T>   the value type
     * @return the persistent capability
     * @throws NullPointerException when {@code codec} is {@code null}
     * @throws CapabilityException  when {@code id} is invalid
     */
    public static <T> AttachmentType<T> of(String id, Class<T> type,
            CapabilityCodec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        return builder(id, type).codec(codec, MAX_ENCODED_BYTES).build();
    }

    @Override
    public CapabilityId key() {
        return key;
    }

    @Override
    public Class<T> valueType() {
        return type;
    }

    @Override
    public T defaultValue() {
        return defaultSupplier != null ? defaultSupplier.get() : defaultValue;
    }

    @Override
    public CapabilityCodec<T> codec() {
        return codec;
    }

    @Override
    public int maxEncodedBytes() {
        return maxEncodedBytes;
    }

    @Override
    public boolean copyOnItemCopy() {
        return copyOnItemCopy;
    }

    @Override
    public boolean copyOnDeath() {
        return copyOnDeath;
    }

    @Override
    public CapabilitySync sync() {
        return sync;
    }

    @Override
    public String toString() {
        return "AttachmentType[id=" + key.id() + ", type=" + type.getName() + "]";
    }

    /**
     * Builder for an {@link AttachmentType}.
     *
     * <p>The codec and its encoded-size bound must be supplied together. When
     * omitted, the capability is runtime-only.
     *
     * @param <T> the value type
     */
    public static final class Builder<T> {

        private final String id;
        private final Class<T> type;
        private T defaultValue;
        private Supplier<T> defaultSupplier;
        private CapabilityCodec<T> codec;
        private int maxEncodedBytes;
        private Boolean copyOnItemCopy;
        private boolean copyOnDeath;
        private CapabilitySync sync;

        private Builder(String id, Class<T> type) {
            this.id = Objects.requireNonNull(id, "id");
            this.type = Objects.requireNonNull(type, "type");
        }

        /**
         * Sets a constant default value.
         *
         * <p>This replaces any previously configured default supplier. A
         * {@code null} value is allowed and is also the initial default.
         *
         * @param value the default value, possibly {@code null}
         * @return this builder
         */
        public Builder<T> defaultValue(T value) {
            this.defaultValue = value;
            this.defaultSupplier = null;
            return this;
        }

        /**
         * Sets a default value supplier evaluated on every
         * {@link AttachmentType#defaultValue()} call.
         *
         * <p>This replaces any previously configured constant default.
         *
         * @param supplier the non-null default supplier
         * @return this builder
         * @throws NullPointerException when {@code supplier} is {@code null}
         */
        public Builder<T> defaultSupplier(Supplier<T> supplier) {
            this.defaultSupplier = Objects.requireNonNull(supplier, "supplier");
            this.defaultValue = null;
            return this;
        }

        /**
         * Sets the codec and the maximum encoded payload size.
         *
         * <p>Both values must be supplied together. The bound must be greater
         * than zero and no larger than {@code 16384}, or no larger than
         * {@code 1048576} when the type is declared {@link CapabilitySync#NONE}
         * (checked by {@link #build()}, so the order of the calls does not
         * matter).
         *
         * @param codec           the non-null codec
         * @param maxEncodedBytes the positive encoded-size bound
         * @return this builder
         * @throws NullPointerException when {@code codec} is {@code null}
         */
        public Builder<T> codec(CapabilityCodec<T> codec, int maxEncodedBytes) {
            this.codec = Objects.requireNonNull(codec, "codec");
            this.maxEncodedBytes = maxEncodedBytes;
            return this;
        }

        /**
         * Sets whether an ItemStack copy keeps the value.
         *
         * <p>The initial default is {@code true} when a codec is configured and
         * {@code false} otherwise.
         *
         * @param copy whether the value follows an ItemStack copy
         * @return this builder
         */
        public Builder<T> copyOnItemCopy(boolean copy) {
            this.copyOnItemCopy = copy;
            return this;
        }

        /**
         * Sets whether a player keeps the value on death and respawn. The
         * default is {@code false}.
         *
         * @param copy whether the value survives player death
         * @return this builder
         */
        public Builder<T> copyOnDeath(boolean copy) {
            this.copyOnDeath = copy;
            return this;
        }

        /**
         * Sets the synchronization declaration.
         *
         * <p>The initial default is {@link CapabilitySync#ITEM_STACK} when a
         * codec is configured and {@link CapabilitySync#NONE} otherwise.
         *
         * @param sync the non-null synchronization declaration
         * @return this builder
         * @throws NullPointerException when {@code sync} is {@code null}
         */
        public Builder<T> sync(CapabilitySync sync) {
            this.sync = Objects.requireNonNull(sync, "sync");
            return this;
        }

        /**
         * Builds the immutable attachment type.
         *
         * @return the built capability
         * @throws CapabilityException when the id is invalid or the codec and
         *                             encoded-size bound are not a valid pair
         *                             for the declared synchronization
         */
        public AttachmentType<T> build() {
            CapabilityId key;
            try {
                key = CapabilityId.parse(id);
            } catch (IllegalArgumentException failure) {
                throw new CapabilityException("Invalid capability id: " + id, failure);
            }
            if (codec == null && maxEncodedBytes != 0) {
                throw new CapabilityException(
                        "Encoded-size bound requires a codec: " + id);
            }
            CapabilitySync effectiveSync = sync != null ? sync
                    : (codec != null ? CapabilitySync.ITEM_STACK : CapabilitySync.NONE);
            int limit = effectiveSync == CapabilitySync.NONE
                    ? MAX_SAVED_ONLY_BYTES : MAX_ENCODED_BYTES;
            if (codec != null && (maxEncodedBytes <= 0 || maxEncodedBytes > limit)) {
                throw new CapabilityException("Encoded-size bound must be between 1 and "
                        + limit + (limit == MAX_ENCODED_BYTES
                                ? " (up to " + MAX_SAVED_ONLY_BYTES
                                        + " for a type declared sync(NONE))" : "")
                        + ": " + id);
            }
            boolean copy = copyOnItemCopy != null ? copyOnItemCopy : codec != null;
            return new AttachmentType<>(key, type, defaultValue, defaultSupplier, codec,
                    maxEncodedBytes, copy, copyOnDeath, effectiveSync);
        }
    }
}
