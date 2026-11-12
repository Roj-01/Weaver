package net.weaver.api.capability;

/**
 * A typed value that can be attached to a holder through an
 * {@link AttachmentRegistry}.
 *
 * <p>A capability declares its identity, value type, default value, optional
 * byte codec, copy behavior, and synchronization declaration. Missing values
 * are represented by {@link #defaultValue()}; the default may be {@code null}
 * and is evaluated on every call. Setting a {@code null} value through the
 * registry removes the explicit value. A capability without a codec is
 * runtime-only and cannot be persisted.
 *
 * <p>{@link #copyOnItemCopy()} only applies to ItemStack persistence. Item data
 * components are copied by the game, and for runtime-only types Weaver does not
 * provide copy semantics.
 */
public interface Capability<T> {

    /**
     * Returns the validated identity of this capability.
     *
     * @return the capability id
     */
    CapabilityId key();

    /**
     * Returns the full id in {@code namespace:path} form.
     *
     * @return the id of {@link #key()}
     */
    default String id() {
        return key().id();
    }

    /**
     * Returns the namespace part of the id.
     *
     * @return the namespace of {@link #key()}
     */
    default String namespace() {
        return key().namespace();
    }

    /**
     * Returns the path part of the id.
     *
     * @return the path of {@link #key()}
     */
    default String path() {
        return key().path();
    }

    /**
     * Returns the runtime value type.
     *
     * <p>Every explicit value is checked against this type before it is stored.
     *
     * @return the value type
     */
    Class<T> valueType();

    /**
     * Returns the value used when no explicit value is present.
     *
     * <p>The default may be {@code null} and is evaluated on every call, so a
     * supplied default can change over time.
     *
     * @return the current default value, possibly {@code null}
     */
    T defaultValue();

    /**
     * Returns the byte codec, or {@code null} when the capability is
     * runtime-only.
     *
     * @return the codec or {@code null}
     */
    CapabilityCodec<T> codec();

    /**
     * Returns the maximum encoded payload size in bytes.
     *
     * @return the positive bound when a codec is present, otherwise {@code 0}
     */
    int maxEncodedBytes();

    /**
     * Returns whether the value follows an ItemStack copy.
     *
     * <p>This flag only applies to ItemStack persistence. Item data components
     * are copied by the game, and runtime-only types do not receive copy
     * semantics from Weaver.
     *
     * @return {@code true} when an ItemStack copy keeps the value
     */
    boolean copyOnItemCopy();

    /**
     * Returns whether a player keeps the value when they die and respawn.
     *
     * <p>Respawning creates a new player object. Values with this flag are
     * copied to it; the others are dropped. Returning from the End keeps every
     * value regardless of this flag. Other holders ignore it.
     *
     * @return {@code true} when the value survives player death
     */
    default boolean copyOnDeath() {
        return false;
    }

    /**
     * Returns the synchronization declaration.
     *
     * @return the sync declaration, never {@code null}
     */
    CapabilitySync sync();

    /**
     * Returns whether this capability can be persisted.
     *
     * @return {@code true} when {@link #codec()} is not {@code null}
     */
    default boolean persistent() {
        return codec() != null;
    }
}
