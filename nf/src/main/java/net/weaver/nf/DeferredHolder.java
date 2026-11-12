package net.weaver.nf;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * A holder for a registry entry that exists before the entry is registered, as
 * NeoForge's {@code DeferredHolder}.
 *
 * <p>For registries without intrusive holders (attributes, mob effects, sounds,
 * data components, ...) the registry adopts this very object as the entry's
 * holder when it is registered: it is the holder the game uses, so it works as a
 * map key, in tags, and in attribute and effect lookups. For registries whose
 * values carry their own holder (items, blocks, entity types) this object
 * forwards to that holder once the entry is registered.</p>
 *
 * @param <R> the registry type
 * @param <T> the entry type
 */
public class DeferredHolder<R, T extends R> extends Holder.Reference<R> implements Supplier<T> {
    private final ResourceKey<R> key;
    private volatile Holder<R> delegate;

    protected DeferredHolder(ResourceKey<R> key) {
        super(Holder.Reference.Type.STAND_ALONE, ownerOf(key), key, null);
        this.key = key;
    }

    /** A holder for {@code key}. */
    public static <R, T extends R> DeferredHolder<R, T> create(ResourceKey<R> key) {
        return new DeferredHolder<>(key);
    }

    /** A holder for entry {@code id} of the registry {@code registryKey}. */
    public static <R, T extends R> DeferredHolder<R, T> create(ResourceKey<? extends Registry<R>> registryKey, Identifier id) {
        return create(ResourceKey.create(registryKey, id));
    }

    /**
     * The owner of this holder: the registry of the key, looked up when first asked,
     * because mods make holders while the built-in registries are still being
     * created (the root registry has not bound them yet).
     */
    private static <R> HolderOwner<R> ownerOf(ResourceKey<R> key) {
        Objects.requireNonNull(key, "key");
        return new HolderOwner<>() {
            @Override
            public boolean canSerialize(HolderOwner<R> other) {
                return other == this || other != null && other == registryOf(key);
            }
        };
    }

    /** The built-in registry of {@code key}, or null while it does not exist or is not bound yet. */
    static <R> Registry<R> registryOf(ResourceKey<R> key) {
        return DeferredRegister.registryOf(key.registryKey());
    }

    /** Forwards to {@code holder} (the holder of a registry that did not adopt this one). */
    void bindTo(Holder<R> holder) {
        if (holder != this) {
            this.delegate = holder;
        }
    }

    /**
     * The registry's holder when it did not adopt this one, found on first use
     * after registration; null when this holder is the registry's own or the entry
     * is not registered yet.
     */
    @SuppressWarnings("unchecked")
    private Holder<R> forwarded() {
        if (super.isBound()) {
            return null;
        }
        Holder<R> found = delegate;
        if (found != null) {
            return found;
        }
        Registry<R> registry = registryOf(key);
        if (registry != null) {
            Optional<Holder.Reference<R>> holder = registry.get(key);
            if (holder.isPresent() && holder.get() != this && holder.get().isBound()) {
                delegate = holder.get();
                return holder.get();
            }
        }
        return null;
    }

    /** The entry; fails when it is not registered yet. */
    @Override
    @SuppressWarnings("unchecked")
    public T value() {
        Holder<R> forwarded = forwarded();
        return (T) (forwarded != null ? forwarded.value() : super.value());
    }

    /** The entry; fails when it is not registered yet. */
    @Override
    public T get() {
        return value();
    }

    /** The entry, or empty when it is not registered yet. */
    public Optional<T> asOptional() {
        return isBound() ? Optional.of(value()) : Optional.empty();
    }

    /** The entry's id. */
    public Identifier getId() {
        return key.identifier();
    }

    /** The entry's key. */
    public ResourceKey<R> getKey() {
        return key;
    }

    /** The holder the game uses for the entry: this one, or the registry's own. */
    public Holder<R> getDelegate() {
        Holder<R> forwarded = forwarded();
        return forwarded != null ? forwarded : this;
    }

    @Override
    public boolean isBound() {
        return super.isBound() || forwarded() != null;
    }

    @Override
    public boolean areComponentsBound() {
        Holder<R> forwarded = forwarded();
        return forwarded != null ? forwarded.areComponentsBound() : super.areComponentsBound();
    }

    @Override
    public DataComponentMap components() {
        Holder<R> forwarded = forwarded();
        return forwarded != null ? forwarded.components() : super.components();
    }

    @Override
    public boolean is(TagKey<R> tag) {
        Holder<R> forwarded = forwarded();
        return forwarded != null ? forwarded.is(tag) : super.is(tag);
    }

    @Override
    public boolean is(Holder<R> other) {
        return other.is(key);
    }

    @Override
    public Stream<TagKey<R>> tags() {
        Holder<R> forwarded = forwarded();
        return forwarded != null ? forwarded.tags() : super.tags();
    }

    /** Serializable wherever the registry of the key is (codecs check this for every holder they write). */
    @Override
    public boolean canSerializeIn(HolderOwner<R> owner) {
        Holder<R> forwarded = forwarded();
        if (forwarded != null) {
            return forwarded.canSerializeIn(owner);
        }
        if (super.canSerializeIn(owner)) {
            return true;
        }
        Registry<R> registry = registryOf(key);
        return registry != null && owner.canSerialize(registry);
    }

    @Override
    public String toString() {
        return "DeferredHolder{" + key + "}";
    }

    /** Equal to any reference holder of the same key. */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Holder<?> holder && holder.kind() == Holder.Kind.REFERENCE
                && holder.unwrapKey().map(key::equals).orElse(false);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
