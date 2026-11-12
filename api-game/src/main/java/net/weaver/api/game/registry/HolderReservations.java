package net.weaver.api.game.registry;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.weaver.api.annotation.Internal;

/**
 * Holders reserved for keys not registered yet. When the entry is registered in
 * a registry whose holders are not intrusive (attributes, mob effects, sounds,
 * data components, ...), the registry adopts the reserved holder instead of
 * making its own, so the holder a mod kept from before registration is the very
 * one the game uses: map lookups, tags, and components all agree.
 *
 * <p>Registries with intrusive holders (items, blocks, entity types) keep the
 * holder each value makes for itself; a reservation for such a key is simply
 * not used.</p>
 */
@Internal
public final class HolderReservations {
    private static final Map<ResourceKey<?>, Holder.Reference<?>> RESERVED = new ConcurrentHashMap<>();

    private HolderReservations() {
    }

    /** Reserves {@code holder} for {@code key}. The holder's key must be {@code key}. */
    public static <T> void reserve(ResourceKey<T> key, Holder.Reference<T> holder) {
        RESERVED.putIfAbsent(Objects.requireNonNull(key, "key"), Objects.requireNonNull(holder, "holder"));
    }

    /** The holder reserved for {@code key}, removed from the reservations, or null. */
    @SuppressWarnings("unchecked")
    public static <T> Holder.Reference<T> take(ResourceKey<T> key) {
        return (Holder.Reference<T>) RESERVED.remove(key);
    }
}
