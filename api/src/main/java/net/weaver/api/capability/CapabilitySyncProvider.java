package net.weaver.api.capability;

/**
 * Resolves the stable network identity of a capability holder.
 *
 * <p>The provider is installed by the loader or the game layer, which is the
 * only layer that knows how to name a game object. The API never invents holder
 * identities. A {@code null} kind or key means the holder is not synchronizable,
 * so the capability channel skips it.</p>
 *
 * <p>{@link #resolve(String, String)} is the reverse lookup used by the
 * clientbound handler to find the local holder for an incoming snapshot. A
 * provider that only supports outbound synchronization can leave the default
 * implementation in place; incoming values are then ignored with a diagnostic
 * instead of being applied to the wrong object.</p>
 */
public interface CapabilitySyncProvider {

    /**
     * Returns the stable kind of a holder, for example {@code entity} or
     * {@code block_entity}.
     *
     * @param holder the holder to identify
     * @return the kind, or {@code null} when the holder is not syncable
     */
    String holderKind(Object holder);

    /**
     * Returns the stable key of a holder inside its kind.
     *
     * @param holder the holder to identify
     * @return the key, or {@code null} when the holder is not syncable
     */
    String holderKey(Object holder);

    /**
     * Finds the local holder for an incoming snapshot.
     *
     * @param holderKind the kind sent by the server
     * @param holderKey  the key sent by the server
     * @return the local holder, or {@code null} when it is not available
     */
    default Object resolve(String holderKind, String holderKey) {
        return null;
    }

    /**
     * Whether a holder is a copy received from the other side (for example an
     * entity in a client level). Changes on such a copy are never sent: in
     * single player the client copy shares the JVM with the server, and sending
     * them back would echo every change forever.
     *
     * @param holder the changed holder
     * @return {@code true} for a remote copy
     */
    default boolean isRemoteCopy(Object holder) {
        return false;
    }

    /**
     * Whether a connection should receive a change on a holder, for example only
     * players that can see the entity. Defaults to every connection.
     *
     * @param holder     the changed holder
     * @param connection a candidate recipient
     * @return {@code true} to send the change to it
     */
    default boolean shouldReceive(Object holder, net.weaver.api.network.NetworkConnection connection) {
        return true;
    }
}
