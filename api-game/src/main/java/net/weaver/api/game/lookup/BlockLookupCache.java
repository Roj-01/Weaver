package net.weaver.api.game.lookup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * A cached block lookup at one position, for code that asks the same neighbour
 * every tick (pipes, conveyors, kinetic networks). {@link #get()} answers from
 * the cache and only runs the lookup again after something changed: the block
 * or block entity at the position was replaced or removed, its chunk unloaded,
 * or a provider called {@link #invalidate(Level, BlockPos)} because what it
 * exposes changed.
 *
 * <p>Use it from the thread that owns the level (the server thread for a
 * server level). An optional listener runs on invalidation, for example to
 * rebuild a network.
 *
 * <pre>{@code
 * BlockLookupCache<ItemHandler, Direction> target =
 *         BlockLookupCache.create(Lookups.BLOCK_ITEMS, level, pos.east(), Direction.WEST, this::onNeighbourChanged);
 * ItemHandler handler = target.get(); // cheap after the first call
 * }</pre>
 */
public final class BlockLookupCache<T, C> {
    private static final Map<Level, Map<Long, List<WeakReference<BlockLookupCache<?, ?>>>>> CACHES =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private final BlockLookup<T, C> lookup;
    private final WeakReference<Level> level;
    private final BlockPos pos;
    private final C context;
    private final Runnable onInvalidate;
    private volatile boolean valid;
    private T value;

    private BlockLookupCache(BlockLookup<T, C> lookup, Level level, BlockPos pos, C context, Runnable onInvalidate) {
        this.lookup = lookup;
        this.level = new WeakReference<>(level);
        this.pos = pos;
        this.context = context;
        this.onInvalidate = onInvalidate;
    }

    /** A cache of {@code lookup} at {@code pos}; it holds the level weakly. */
    public static <T, C> BlockLookupCache<T, C> create(BlockLookup<T, C> lookup, Level level, BlockPos pos, C context) {
        return create(lookup, level, pos, context, null);
    }

    /** As {@link #create(BlockLookup, Level, BlockPos, Object)}, calling {@code onInvalidate} when it is invalidated. */
    public static <T, C> BlockLookupCache<T, C> create(BlockLookup<T, C> lookup, Level level, BlockPos pos, C context,
            Runnable onInvalidate) {
        BlockLookupCache<T, C> cache = new BlockLookupCache<>(Objects.requireNonNull(lookup, "lookup"),
                Objects.requireNonNull(level, "level"), Objects.requireNonNull(pos, "pos").immutable(), context, onInvalidate);
        synchronized (CACHES) {
            CACHES.computeIfAbsent(level, key -> new ConcurrentHashMap<>())
                    .computeIfAbsent(cache.pos.asLong(), key -> new ArrayList<>())
                    .add(new WeakReference<>(cache));
        }
        return cache;
    }

    /** The value at the position, from the cache when nothing changed since the last call. */
    public T get() {
        if (valid) {
            return value;
        }
        Level current = level.get();
        T found = current == null ? null : lookup.find(current, pos, context);
        value = found;
        valid = current != null;
        return found;
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean isValid() {
        return valid;
    }

    /** Forces the next {@link #get()} to run the lookup again. */
    public void invalidateNow() {
        valid = false;
        value = null;
        if (onInvalidate != null) {
            try {
                onInvalidate.run();
            } catch (RuntimeException failure) {
                System.out.println("[weaver] LOOKUP CACHE LISTENER FAILED pos=" + pos + " reason=" + failure);
            }
        }
    }

    /**
     * Invalidates every cache at {@code pos}. Weaver calls this when the block or
     * block entity there changes; a provider calls it when what it exposes changes
     * without the block changing (for example a machine switching its output side).
     */
    public static void invalidate(Level level, BlockPos pos) {
        if (CACHES.isEmpty()) {
            return;
        }
        List<WeakReference<BlockLookupCache<?, ?>>> list;
        synchronized (CACHES) {
            Map<Long, List<WeakReference<BlockLookupCache<?, ?>>>> byPos = CACHES.get(level);
            if (byPos == null) {
                return;
            }
            list = byPos.get(pos.asLong());
            if (list == null) {
                return;
            }
            list.removeIf(reference -> reference.get() == null);
            if (list.isEmpty()) {
                byPos.remove(pos.asLong());
                return;
            }
            list = new ArrayList<>(list);
        }
        for (WeakReference<BlockLookupCache<?, ?>> reference : list) {
            BlockLookupCache<?, ?> cache = reference.get();
            if (cache != null) {
                cache.invalidateNow();
            }
        }
    }

    /** Invalidates every cache in a chunk; Weaver calls it when the chunk unloads. */
    public static void invalidateChunk(Level level, ChunkPos chunk) {
        if (CACHES.isEmpty()) {
            return;
        }
        List<BlockPos> positions = new ArrayList<>();
        synchronized (CACHES) {
            Map<Long, List<WeakReference<BlockLookupCache<?, ?>>>> byPos = CACHES.get(level);
            if (byPos == null) {
                return;
            }
            for (Long key : byPos.keySet()) {
                BlockPos pos = BlockPos.of(key);
                if ((pos.getX() >> 4) == chunk.x() && (pos.getZ() >> 4) == chunk.z()) {
                    positions.add(pos);
                }
            }
        }
        for (BlockPos pos : positions) {
            invalidate(level, pos);
        }
    }

    /** Whether any cache exists, so the hooks stay free when no mod uses caches. */
    public static boolean anyCaches() {
        return !CACHES.isEmpty();
    }
}
