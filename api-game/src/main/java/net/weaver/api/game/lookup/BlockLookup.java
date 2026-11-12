package net.weaver.api.game.lookup;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds a {@code T} (for example an {@link ItemHandler}) at a block position.
 *
 * <p>Providers are asked in order until one returns a non-null value: first
 * those registered for the block, then those registered for the block
 * entity's type, then the fallbacks. The context {@code C} is extra input
 * such as the side being accessed ({@code Direction}); it may be
 * {@code null}.</p>
 *
 * <p>Nothing is cached: call {@link #find} when the value is needed, and do
 * not keep the result past the current operation, since the block can change.
 * Unloaded positions return {@code null} without loading the chunk.</p>
 *
 * @param <T> the looked-up type
 * @param <C> the context type
 */
public final class BlockLookup<T, C> {
    /** Provides for a block, whatever its block entity. */
    @FunctionalInterface
    public interface Provider<T, C> {
        /** The value at {@code pos}, or {@code null} to let later providers answer. */
        T get(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity, C context);
    }

    /** Provides for a block entity type. */
    @FunctionalInterface
    public interface BlockEntityProvider<T, C, E extends BlockEntity> {
        /** The value for {@code blockEntity}, or {@code null} to let later providers answer. */
        T get(E blockEntity, C context);
    }

    private final Identifier id;
    private final Class<T> type;
    private final Class<C> contextType;
    private final ProviderTable<Block, Provider<T, C>> blocks = new ProviderTable<>();
    private final ProviderTable<BlockEntityType<?>, Provider<T, C>> blockEntities = new ProviderTable<>();

    private BlockLookup(Identifier id, Class<T> type, Class<C> contextType) {
        this.id = id;
        this.type = type;
        this.contextType = contextType;
    }

    /**
     * The block lookup with this id, created on first use. Asking again with
     * the same types returns the same lookup.
     *
     * @throws IllegalArgumentException when the id exists with other types
     */
    public static <T, C> BlockLookup<T, C> get(Identifier id, Class<T> type, Class<C> contextType) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(contextType, "contextType");
        return LookupIds.intern("block", id, type, contextType, () -> new BlockLookup<>(id, type, contextType));
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

    /** Registers {@code provider} for each of {@code blocks}. */
    public void registerForBlocks(Provider<T, C> provider, Block... blocks) {
        for (Block block : blocks) {
            this.blocks.add(block, provider);
        }
    }

    /** Registers {@code provider} for block entities of each of {@code types}. */
    @SafeVarargs
    public final <E extends BlockEntity> void registerForBlockEntities(BlockEntityProvider<T, C, E> provider,
            BlockEntityType<? extends E>... types) {
        Objects.requireNonNull(provider, "provider");
        Provider<T, C> adapted = new BlockEntityAdapter<>(provider);
        for (BlockEntityType<? extends E> blockEntityType : types) {
            blockEntities.add(blockEntityType, adapted);
        }
    }

    /** Registers a provider asked for every block, after the specific ones. */
    public void registerFallback(Provider<T, C> provider) {
        blocks.addFallback(provider);
    }

    /** Whether a provider is registered for {@code block} itself. */
    public boolean hasProvider(Block block) {
        return blocks.has(block);
    }

    /** The value at {@code pos}, or {@code null}. */
    public T find(Level level, BlockPos pos, C context) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        return find(level, pos, state, blockEntity, context);
    }

    /** The value for a block whose state and block entity the caller already has. */
    public T find(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity, C context) {
        for (Provider<T, C> provider : blocks.forKey(state.getBlock())) {
            T value = provider.get(level, pos, state, blockEntity, context);
            if (value != null) {
                return value;
            }
        }
        if (blockEntity != null) {
            for (Provider<T, C> provider : blockEntities.forKey(blockEntity.getType())) {
                T value = provider.get(level, pos, state, blockEntity, context);
                if (value != null) {
                    return value;
                }
            }
        }
        for (Provider<T, C> provider : blocks.fallbacks()) {
            T value = provider.get(level, pos, state, blockEntity, context);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "BlockLookup[" + id + " " + type.getSimpleName() + "/" + contextType.getSimpleName() + "]";
    }

    private record BlockEntityAdapter<T, C, E extends BlockEntity>(BlockEntityProvider<T, C, E> provider)
            implements Provider<T, C> {
        @Override
        @SuppressWarnings("unchecked")
        public T get(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity, C context) {
            return blockEntity == null ? null : provider.get((E) blockEntity, context);
        }
    }
}
