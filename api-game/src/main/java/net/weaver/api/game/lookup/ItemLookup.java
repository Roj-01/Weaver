package net.weaver.api.game.lookup;

import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Finds a {@code T} on an item stack. Providers registered for the stack's
 * item are asked first, then the fallbacks; the first non-null answer wins.
 * Empty stacks return {@code null}.
 *
 * <p>A handler from an item lookup works on the stack it was given, so changes
 * land in that stack (typically in its data components). It cannot replace the
 * stack with a different item, which is why vanilla buckets are not fluid
 * handlers here.</p>
 *
 * @param <T> the looked-up type
 * @param <C> the context type
 */
public final class ItemLookup<T, C> {
    /** Provides for an item. */
    @FunctionalInterface
    public interface Provider<T, C> {
        /** The value for {@code stack}, or {@code null} to let later providers answer. */
        T get(ItemStack stack, C context);
    }

    private final Identifier id;
    private final Class<T> type;
    private final Class<C> contextType;
    private final ProviderTable<Item, Provider<T, C>> table = new ProviderTable<>();

    private ItemLookup(Identifier id, Class<T> type, Class<C> contextType) {
        this.id = id;
        this.type = type;
        this.contextType = contextType;
    }

    /**
     * The item lookup with this id, created on first use.
     *
     * @throws IllegalArgumentException when the id exists with other types
     */
    public static <T, C> ItemLookup<T, C> get(Identifier id, Class<T> type, Class<C> contextType) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(contextType, "contextType");
        return LookupIds.intern("item", id, type, contextType, () -> new ItemLookup<>(id, type, contextType));
    }

    public Identifier id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    public Class<C> contextType() {
        return contextType;
    }

    /** Registers {@code provider} for each of {@code items}. */
    public void registerForItems(Provider<T, C> provider, ItemLike... items) {
        for (ItemLike item : items) {
            table.add(item.asItem(), provider);
        }
    }

    /** Registers a provider asked for every item, after the specific ones. */
    public void registerFallback(Provider<T, C> provider) {
        table.addFallback(provider);
    }

    /** The value for {@code stack}, or {@code null}. */
    public T find(ItemStack stack, C context) {
        if (stack.isEmpty()) {
            return null;
        }
        for (Provider<T, C> provider : table.forKey(stack.getItem())) {
            T value = provider.get(stack, context);
            if (value != null) {
                return value;
            }
        }
        for (Provider<T, C> provider : table.fallbacks()) {
            T value = provider.get(stack, context);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "ItemLookup[" + id + " " + type.getSimpleName() + "/" + contextType.getSimpleName() + "]";
    }
}
