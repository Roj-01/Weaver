package net.weaver.minimap.map;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Queues disk loads on a background executor and publishes results back to the
 * client thread through {@link #drain}.
 *
 * <p>All mutating methods except {@link #close()} must run on the client
 * thread. The loader itself runs only on the executor. This class deliberately
 * does not touch the world or any game object.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
final class AsyncStore<K, V>
{
    /**
     * Loads one value, returning {@code null} when the backing file is absent.
     */
    interface Loader<K, V>
    {
        V load(K key) throws IOException;
    }

    private record Completion<K, V>(K key, V value, IOException failure)
    {
    }

    private final ExecutorService executor;
    private final Loader<K, V> loader;
    private final Consumer<String> log;
    private final Set<K> requested = new LinkedHashSet<>();
    private final Set<K> loading = new HashSet<>();
    private final Map<K, RegionLoadState> states = new HashMap<>();
    private final ConcurrentLinkedQueue<Completion<K, V>> completions =
            new ConcurrentLinkedQueue<>();
    private volatile boolean closed;

    AsyncStore(ExecutorService executor, Loader<K, V> loader, Consumer<String> log)
    {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Queues a key when it is not already queued, loading, or loaded.
     */
    void request(K key)
    {
        Objects.requireNonNull(key, "key");
        if (closed)
        {
            return;
        }
        RegionLoadState state = states.get(key);
        if (state == RegionLoadState.QUEUED || state == RegionLoadState.LOADING)
        {
            return;
        }
        if (state == RegionLoadState.LOADED)
        {
            return;
        }
        states.put(key, RegionLoadState.QUEUED);
        requested.add(key);
    }

    /**
     * Submits up to {@code maximum} queued keys to the executor.
     */
    void pump(int maximum)
    {
        if (closed)
        {
            return;
        }
        int submitted = 0;
        while (submitted < maximum && !requested.isEmpty())
        {
            K key = requested.iterator().next();
            requested.remove(key);
            if (states.get(key) != RegionLoadState.QUEUED)
            {
                continue;
            }
            loading.add(key);
            states.put(key, RegionLoadState.LOADING);
            submitted++;
            try
            {
                executor.execute(() -> runLoad(key));
            }
            catch (RejectedExecutionException rejected)
            {
                loading.remove(key);
                states.put(key, RegionLoadState.FAILED);
                log.accept("async load rejected for " + key + ": " + rejected);
            }
        }
    }

    /**
     * Applies up to {@code maximum} completed loads on the calling thread.
     */
    void drain(int maximum, BiConsumer<K, V> onValue, BiConsumer<K, IOException> onFailure)
    {
        Objects.requireNonNull(onValue, "onValue");
        Objects.requireNonNull(onFailure, "onFailure");
        int applied = 0;
        while (applied < maximum)
        {
            Completion<K, V> completion = completions.poll();
            if (completion == null)
            {
                return;
            }
            applied++;
            loading.remove(completion.key());
            if (completion.failure() != null)
            {
                states.put(completion.key(), RegionLoadState.FAILED);
                onFailure.accept(completion.key(), completion.failure());
            }
            else
            {
                states.put(completion.key(), RegionLoadState.LOADED);
                onValue.accept(completion.key(), completion.value());
            }
        }
    }

    RegionLoadState state(K key)
    {
        return states.getOrDefault(key, RegionLoadState.UNLOADED);
    }

    /**
     * Forgets a terminal state, for example after a value was evicted.
     */
    void forget(K key)
    {
        RegionLoadState state = states.get(key);
        if (state == RegionLoadState.QUEUED || state == RegionLoadState.LOADING)
        {
            return;
        }
        states.remove(key);
    }

    int pending()
    {
        return requested.size() + loading.size();
    }

    /**
     * Stops accepting work. In-flight loads finish but their results are
     * discarded by the next {@link #drain} call.
     */
    void close()
    {
        closed = true;
        requested.clear();
        states.clear();
        completions.clear();
    }

    private void runLoad(K key)
    {
        V value = null;
        IOException failure = null;
        try
        {
            value = loader.load(key);
        }
        catch (IOException error)
        {
            failure = error;
        }
        catch (RuntimeException error)
        {
            failure = new IOException("unexpected loader failure for " + key, error);
        }
        if (closed)
        {
            return;
        }
        completions.add(new Completion<>(key, value, failure));
    }
}
