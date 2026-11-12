package net.weaver.api.game.lookup;

import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Finds a {@code T} on an entity. Providers registered for the entity's type
 * are asked first, then the fallbacks; the first non-null answer wins. The
 * context may be {@code null}. Nothing is cached.
 *
 * @param <T> the looked-up type
 * @param <C> the context type
 */
public final class EntityLookup<T, C> {
    /** Provides for an entity type. */
    @FunctionalInterface
    public interface Provider<T, C, E extends Entity> {
        /** The value for {@code entity}, or {@code null} to let later providers answer. */
        T get(E entity, C context);
    }

    private final Identifier id;
    private final Class<T> type;
    private final Class<C> contextType;
    private final ProviderTable<EntityType<?>, Provider<T, C, Entity>> table = new ProviderTable<>();

    private EntityLookup(Identifier id, Class<T> type, Class<C> contextType) {
        this.id = id;
        this.type = type;
        this.contextType = contextType;
    }

    /**
     * The entity lookup with this id, created on first use.
     *
     * @throws IllegalArgumentException when the id exists with other types
     */
    public static <T, C> EntityLookup<T, C> get(Identifier id, Class<T> type, Class<C> contextType) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(contextType, "contextType");
        return LookupIds.intern("entity", id, type, contextType, () -> new EntityLookup<>(id, type, contextType));
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

    /** Registers {@code provider} for entities of each of {@code types}. */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final <E extends Entity> void registerForEntities(Provider<T, C, E> provider,
            EntityType<? extends E>... types) {
        Objects.requireNonNull(provider, "provider");
        for (EntityType<? extends E> entityType : types) {
            table.add(entityType, (Provider<T, C, Entity>) (Provider<T, C, ?>) provider);
        }
    }

    /** Registers a provider asked for every entity, after the specific ones. */
    public void registerFallback(Provider<T, C, Entity> provider) {
        table.addFallback(provider);
    }

    /** The value for {@code entity}, or {@code null}. */
    public T find(Entity entity, C context) {
        for (Provider<T, C, Entity> provider : table.forKey(entity.getType())) {
            T value = provider.get(entity, context);
            if (value != null) {
                return value;
            }
        }
        for (Provider<T, C, Entity> provider : table.fallbacks()) {
            T value = provider.get(entity, context);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "EntityLookup[" + id + " " + type.getSimpleName() + "/" + contextType.getSimpleName() + "]";
    }
}
