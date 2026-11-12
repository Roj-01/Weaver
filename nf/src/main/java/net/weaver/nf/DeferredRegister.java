package net.weaver.nf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.RegisterEvent;
import net.weaver.api.game.registry.HolderReservations;

/**
 * Registers entries of one registry for one mod, as NeoForge's
 * {@code DeferredRegister}: {@code register} returns a {@link DeferredHolder}
 * right away, and the entries are created and registered during Weaver's
 * register phase.
 *
 * <p>The entries of a registry are registered when Weaver reaches that registry
 * in the register phase, so a mod does not have to call anything. Calling
 * {@link #flushAll()} from {@code onRegister} registers everything pending right
 * away; it is safe to call more than once and from several mods. An entry added
 * after its registry was flushed is registered at once. {@link #register(Object)}
 * exists for code written as {@code REGISTER.register(modEventBus)} and does
 * nothing.</p>
 */
public class DeferredRegister<T> {
    private static final List<DeferredRegister<?>> ALL = new CopyOnWriteArrayList<>();

    static {
        EventBus.GAME.addListener(RegisterEvent.class, event -> {
            try {
                flushRegistry(event.registryId());
            } catch (RuntimeException | LinkageError failure) {
                System.err.println("[weaver-nf] DEFERRED REGISTER FAILED registry=" + event.registryId() + " reason=" + failure);
                failure.printStackTrace();
                throw failure;
            }
        });
    }

    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String namespace;
    private final Map<DeferredHolder<T, ? extends T>, Function<Identifier, ? extends T>> entries = new LinkedHashMap<>();
    private final List<DeferredHolder<T, ? extends T>> pending = new ArrayList<>();
    private boolean flushed;

    protected DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this.registryKey = Objects.requireNonNull(registryKey, "registryKey");
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        ALL.add(this);
    }

    /** A register for {@code registry} in the mod {@code namespace}. */
    public static <T> DeferredRegister<T> create(Registry<T> registry, String namespace) {
        return create(registry.key(), namespace);
    }

    /** A register for the registry {@code registryKey} in the mod {@code namespace}. */
    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        return new DeferredRegister<>(registryKey, namespace);
    }

    public static Items createItems(String namespace) {
        return new Items(namespace);
    }

    public static Blocks createBlocks(String namespace) {
        return new Blocks(namespace);
    }

    public static DataComponents createDataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
        return new DataComponents(registryKey, namespace);
    }

    public static DataComponents createDataComponents(String namespace) {
        return new DataComponents(Registries.DATA_COMPONENT_TYPE, namespace);
    }

    public static Entities createEntities(String namespace) {
        return new Entities(namespace);
    }

    /** Makes the holder for a new entry; subclasses return their own holder type. */
    protected <I extends T> DeferredHolder<T, I> createHolder(ResourceKey<? extends Registry<T>> registryKey, Identifier id) {
        return DeferredHolder.create(registryKey, id);
    }

    /** Adds the entry {@code name}, made by {@code supplier} when it is registered. */
    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return register(name, id -> supplier.get());
    }

    /** Adds the entry {@code name}, made from its id when it is registered. */
    public synchronized <I extends T> DeferredHolder<T, I> register(String name, Function<Identifier, ? extends I> factory) {
        Objects.requireNonNull(factory, "factory");
        Identifier id = Identifier.fromNamespaceAndPath(namespace, name);
        for (DeferredHolder<T, ? extends T> existing : entries.keySet()) {
            if (existing.getId().equals(id)) {
                throw new IllegalArgumentException("duplicate registration " + id + " in " + registryKey.identifier());
            }
        }
        DeferredHolder<T, I> holder = createHolder(registryKey, id);
        entries.put(holder, factory);
        HolderReservations.reserve(holder.getKey(), holder);
        if (flushed) {
            registerNow(holder);
        } else {
            pending.add(holder);
        }
        return holder;
    }

    /** For code written as {@code REGISTER.register(modEventBus)}: Weaver registers the entries by itself. */
    public void register(Object bus) {
    }

    /** The holders of every entry added, in order. */
    public synchronized Collection<DeferredHolder<T, ? extends T>> getEntries() {
        return Collections.unmodifiableCollection(new ArrayList<>(entries.keySet()));
    }

    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return registryKey;
    }

    public Identifier getRegistryName() {
        return registryKey.identifier();
    }

    public String getNamespace() {
        return namespace;
    }

    /** A tag of this registry in this mod's namespace. */
    public TagKey<T> createTagKey(String path) {
        return createTagKey(Identifier.fromNamespaceAndPath(namespace, path));
    }

    public TagKey<T> createTagKey(Identifier id) {
        return TagKey.create(registryKey, id);
    }

    /**
     * The built-in registry {@code registryKey}, or null while it does not exist or
     * the root registry has not bound it yet (mods start while the built-in
     * registries are being created).
     */
    @SuppressWarnings("unchecked")
    static <T> Registry<T> registryOf(ResourceKey<?> registryKey) {
        Registry<?> cached = REGISTRIES.get(registryKey);
        if (cached != null) {
            return (Registry<T>) cached;
        }
        java.util.Optional<? extends Holder<? extends Registry<?>>> holder = BuiltInRegistries.REGISTRY.get(registryKey.identifier());
        Registry<?> found = holder.isPresent() && holder.get().isBound() ? holder.get().value() : builtInField(registryKey);
        if (found != null) {
            REGISTRIES.put(registryKey, found);
        }
        return (Registry<T>) found;
    }

    /** The public static field of {@code BuiltInRegistries} holding {@code registryKey}, for the bootstrap window. */
    private static Registry<?> builtInField(ResourceKey<?> registryKey) {
        for (java.lang.reflect.Field field : BuiltInRegistries.class.getFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !Registry.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                if (field.get(null) instanceof Registry<?> registry && registry.key().equals(registryKey)) {
                    return registry;
                }
            } catch (IllegalAccessException | RuntimeException skipped) {
                // Not readable yet; the root registry answers once bootstrap is done.
            }
        }
        return null;
    }

    private static final java.util.Map<ResourceKey<?>, Registry<?>> REGISTRIES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Registers every pending entry of every register now. Safe to call more than once. */
    public static void flushAll() {
        for (DeferredRegister<?> register : ALL) {
            register.flush();
        }
    }

    private static void flushRegistry(String registryId) {
        for (DeferredRegister<?> register : ALL) {
            if (register.registryKey.identifier().toString().equals(registryId)) {
                register.flush();
            }
        }
    }

    private synchronized void flush() {
        flushed = true;
        List<DeferredHolder<T, ? extends T>> now = new ArrayList<>(pending);
        pending.clear();
        for (DeferredHolder<T, ? extends T> holder : now) {
            registerNow(holder);
        }
    }

    private void registerNow(DeferredHolder<T, ? extends T> holder) {
        Registry<T> registry = registryOf(registryKey);
        if (registry == null) {
            throw new IllegalStateException("no registry " + registryKey.identifier() + " for " + holder.getId()
                    + " (data-driven registries are filled by data packs, not by DeferredRegister)");
        }
        T value = entries.get(holder).apply(holder.getId());
        Holder.Reference<T> registered = Registry.registerForHolder(registry, holder.getKey(), value);
        holder.bindTo(registered);
        // A registry with intrusive holders did not take the reservation.
        HolderReservations.take(holder.getKey());
    }

    /** Items, with the {@code Item.Properties} id set for you. */
    public static class Items extends DeferredRegister<Item> {
        protected Items(String namespace) {
            super(Registries.ITEM, namespace);
        }

        @Override
        protected <I extends Item> DeferredHolder<Item, I> createHolder(ResourceKey<? extends Registry<Item>> registryKey, Identifier id) {
            return DeferredItem.createItem(ResourceKey.create(registryKey, id));
        }

        @Override
        public <I extends Item> DeferredItem<I> register(String name, Function<Identifier, ? extends I> factory) {
            return (DeferredItem<I>) super.register(name, factory);
        }

        @Override
        public <I extends Item> DeferredItem<I> register(String name, Supplier<? extends I> supplier) {
            return (DeferredItem<I>) super.register(name, supplier);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block,
                Supplier<Item.Properties> properties) {
            return registerItem(name, props -> new BlockItem(block.get(), props.useBlockDescriptionPrefix()), properties);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block,
                UnaryOperator<Item.Properties> properties) {
            return registerItem(name, props -> new BlockItem(block.get(), props.useBlockDescriptionPrefix()), properties);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block) {
            return registerSimpleBlockItem(name, block, UnaryOperator.identity());
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(Holder<Block> block, Supplier<Item.Properties> properties) {
            return registerSimpleBlockItem(path(block), block::value, properties);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(Holder<Block> block, UnaryOperator<Item.Properties> properties) {
            return registerSimpleBlockItem(path(block), block::value, properties);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(Holder<Block> block) {
            return registerSimpleBlockItem(block, UnaryOperator.identity());
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory,
                Supplier<Item.Properties> properties) {
            return register(name, id -> factory.apply(properties.get().setId(ResourceKey.create(Registries.ITEM, id))));
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory,
                UnaryOperator<Item.Properties> properties) {
            return register(name, id -> factory.apply(properties.apply(new Item.Properties()).setId(ResourceKey.create(Registries.ITEM, id))));
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory) {
            return registerItem(name, factory, UnaryOperator.identity());
        }

        public DeferredItem<Item> registerSimpleItem(String name, Supplier<Item.Properties> properties) {
            return registerItem(name, Item::new, properties);
        }

        public DeferredItem<Item> registerSimpleItem(String name, UnaryOperator<Item.Properties> properties) {
            return registerItem(name, Item::new, properties);
        }

        public DeferredItem<Item> registerSimpleItem(String name) {
            return registerItem(name, Item::new);
        }

        private static String path(Holder<Block> block) {
            return block.unwrapKey().orElseThrow(() -> new IllegalArgumentException("unregistered block holder " + block))
                    .identifier().getPath();
        }
    }

    /** Blocks, with the {@code BlockBehaviour.Properties} id set for you. */
    public static class Blocks extends DeferredRegister<Block> {
        protected Blocks(String namespace) {
            super(Registries.BLOCK, namespace);
        }

        @Override
        protected <I extends Block> DeferredHolder<Block, I> createHolder(ResourceKey<? extends Registry<Block>> registryKey, Identifier id) {
            return DeferredBlock.createBlock(ResourceKey.create(registryKey, id));
        }

        @Override
        public <B extends Block> DeferredBlock<B> register(String name, Function<Identifier, ? extends B> factory) {
            return (DeferredBlock<B>) super.register(name, factory);
        }

        @Override
        public <B extends Block> DeferredBlock<B> register(String name, Supplier<? extends B> supplier) {
            return (DeferredBlock<B>) super.register(name, supplier);
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory,
                Supplier<BlockBehaviour.Properties> properties) {
            return register(name, id -> factory.apply(properties.get().setId(ResourceKey.create(Registries.BLOCK, id))));
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory,
                UnaryOperator<BlockBehaviour.Properties> properties) {
            return register(name, id -> factory.apply(properties.apply(BlockBehaviour.Properties.of()).setId(ResourceKey.create(Registries.BLOCK, id))));
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory) {
            return registerBlock(name, factory, UnaryOperator.identity());
        }

        public DeferredBlock<Block> registerSimpleBlock(String name, Supplier<BlockBehaviour.Properties> properties) {
            return registerBlock(name, Block::new, properties);
        }

        public DeferredBlock<Block> registerSimpleBlock(String name, UnaryOperator<BlockBehaviour.Properties> properties) {
            return registerBlock(name, Block::new, properties);
        }

        public DeferredBlock<Block> registerSimpleBlock(String name) {
            return registerBlock(name, Block::new);
        }
    }

    /** Data component types. */
    public static class DataComponents extends DeferredRegister<DataComponentType<?>> {
        protected DataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
            super(registryKey, namespace);
        }

        public <D> DeferredHolder<DataComponentType<?>, DataComponentType<D>> registerComponentType(String name,
                UnaryOperator<DataComponentType.Builder<D>> builder) {
            return register(name, () -> builder.apply(DataComponentType.builder()).build());
        }
    }

    /** Entity types, built with the key set for you. */
    public static class Entities extends DeferredRegister<EntityType<?>> {
        protected Entities(String namespace) {
            super(Registries.ENTITY_TYPE, namespace);
            // Entities of this mod may send spawn data (IEntityWithComplexSpawn).
            ComplexSpawn.open(namespace);
        }

        public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name,
                EntityType.EntityFactory<E> factory, MobCategory category) {
            return registerEntityType(name, factory, category, UnaryOperator.identity());
        }

        public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name,
                EntityType.EntityFactory<E> factory, MobCategory category,
                BiFunction<EntityType.EntityFactory<E>, MobCategory, EntityType.Builder<E>> builder) {
            return register(name, id -> builder.apply(factory, category).build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
        }

        public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name,
                EntityType.EntityFactory<E> factory, MobCategory category, UnaryOperator<EntityType.Builder<E>> builder) {
            return register(name, id -> builder.apply(EntityType.Builder.of(factory, category))
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
        }
    }
}
