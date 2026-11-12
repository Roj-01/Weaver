package net.weaver.api.game.data;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * A data map: one value of type {@code T} per entry of a registry (items,
 * blocks, biomes, ...), filled from data pack files. For example, a "sell
 * value" per item or a "danger level" per biome.
 *
 * <pre>{@code
 * DataMapType<Item, Integer> SELL_VALUE = DataMaps.register(DataMapType.create(
 *         Identifier.fromNamespaceAndPath("examplemod", "sell_value"), Registries.ITEM, Codec.INT).synced());
 *
 * Integer value = SELL_VALUE.get(stack.typeHolder());
 * }</pre>
 *
 * <p>The values come from {@link #file()}: {@code data/<namespace>/weaver/data_maps/<registry>/<path>.json},
 * where {@code <registry>} is the registry path for a {@code minecraft}
 * registry ({@code item}, {@code worldgen/biome}) and {@code <namespace>/<path>}
 * otherwise. See {@link DataMaps} for the file format.</p>
 *
 * @param <R> the registry's element type
 * @param <T> the value type
 */
public final class DataMapType<R, T> {
    private final Identifier id;
    private final ResourceKey<? extends Registry<R>> registry;
    private final Codec<T> codec;
    private final boolean synced;

    private DataMapType(Identifier id, ResourceKey<? extends Registry<R>> registry, Codec<T> codec, boolean synced) {
        this.id = Objects.requireNonNull(id, "id");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.synced = synced;
    }

    /** A data map with values read by {@code codec}; not sent to clients. */
    public static <R, T> DataMapType<R, T> create(Identifier id, ResourceKey<? extends Registry<R>> registry,
            Codec<T> codec) {
        return new DataMapType<>(id, registry, codec, false);
    }

    /** The same map, sent to clients when they join and after every reload. */
    public DataMapType<R, T> synced() {
        return new DataMapType<>(id, registry, codec, true);
    }

    public Identifier id() {
        return id;
    }

    public ResourceKey<? extends Registry<R>> registry() {
        return registry;
    }

    public Codec<T> codec() {
        return codec;
    }

    public boolean isSynced() {
        return synced;
    }

    /** The registry part of the file path: {@code item}, {@code worldgen/biome}, {@code examplemod/gem}. */
    public String registryFolder() {
        Identifier registryId = registry.identifier();
        return Identifier.DEFAULT_NAMESPACE.equals(registryId.getNamespace())
                ? registryId.getPath() : registryId.getNamespace() + "/" + registryId.getPath();
    }

    /** The data pack file this map is read from. */
    public Identifier file() {
        return Identifier.fromNamespaceAndPath(id.getNamespace(),
                "weaver/data_maps/" + registryFolder() + "/" + id.getPath() + ".json");
    }

    /** The value for {@code holder}, or {@code null}. */
    public T get(Holder<R> holder) {
        return DataMaps.get(this, holder);
    }

    /** The value for {@code key}, or {@code null}. */
    public T get(ResourceKey<R> key) {
        return DataMaps.get(this, key);
    }

    /** Every value, by registry key. */
    public Map<ResourceKey<R>, T> values() {
        return DataMaps.values(this);
    }

    @Override
    public String toString() {
        return "DataMapType[" + id + " on " + registry.identifier() + "]";
    }
}
