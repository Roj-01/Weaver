package net.weaver.minimap.map;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncStoreTest
{
    @Test
    void transitionsFromQueuedToLoadingToLoaded() throws Exception
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        AsyncStore<String, String> store = new AsyncStore<>(executor, key ->
        {
            try
            {
                if (!release.await(5, TimeUnit.SECONDS))
                {
                    throw new IOException("timeout");
                }
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", interrupted);
            }
            return key.toUpperCase();
        }, message ->
        {
        });
        try
        {
            assertEquals(RegionLoadState.UNLOADED, store.state("a"));
            store.request("a");
            store.request("a");
            assertEquals(RegionLoadState.QUEUED, store.state("a"));
            assertEquals(1, store.pending());

            store.pump(1);
            assertEquals(RegionLoadState.LOADING, store.state("a"));

            List<String> values = new ArrayList<>();
            store.drain(1, (key, value) -> values.add(key + "=" + value), (key, error) ->
            {
            });
            assertTrue(values.isEmpty());

            release.countDown();
            awaitValues(store, values);
            assertEquals(List.of("a=A"), values);
            assertEquals(RegionLoadState.LOADED, store.state("a"));
            assertEquals(0, store.pending());

            store.forget("a");
            assertEquals(RegionLoadState.UNLOADED, store.state("a"));
        }
        finally
        {
            store.close();
            executor.shutdownNow();
        }
    }

    @Test
    void failureBecomesTerminalAndCanBeRequestedAgain() throws Exception
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger attempts = new AtomicInteger();
        AsyncStore<String, String> store = new AsyncStore<>(executor, key ->
        {
            attempts.incrementAndGet();
            throw new IOException("broken " + key);
        }, message ->
        {
        });
        try
        {
            store.request("a");
            store.pump(1);
            List<String> failures = new ArrayList<>();
            awaitFailures(store, failures);
            assertEquals(1, failures.size());
            assertEquals(RegionLoadState.FAILED, store.state("a"));

            failures.clear();
            store.request("a");
            assertEquals(RegionLoadState.QUEUED, store.state("a"));
            store.pump(1);
            awaitFailures(store, failures);
            assertEquals(2, attempts.get());
        }
        finally
        {
            store.close();
            executor.shutdownNow();
        }
    }

    @Test
    void duplicateRequestsLoadOnce() throws Exception
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicInteger loads = new AtomicInteger();
        AsyncStore<String, String> store = new AsyncStore<>(executor, key ->
        {
            loads.incrementAndGet();
            return key;
        }, message ->
        {
        });
        try
        {
            store.request("a");
            store.request("a");
            store.request("a");
            store.pump(2);
            List<String> values = new ArrayList<>();
            awaitValues(store, values);
            assertEquals(List.of("a=a"), values);
            assertEquals(1, loads.get());
        }
        finally
        {
            store.close();
            executor.shutdownNow();
        }
    }

    private static void awaitValues(AsyncStore<String, String> store, List<String> values)
            throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (values.isEmpty() && System.nanoTime() < deadline)
        {
            store.drain(1, (key, value) -> values.add(key + "=" + value), (key, error) ->
            {
            });
            if (values.isEmpty())
            {
                Thread.sleep(5L);
            }
        }
    }

    private static void awaitFailures(AsyncStore<String, String> store, List<String> failures)
            throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (failures.isEmpty() && System.nanoTime() < deadline)
        {
            store.drain(1, (key, value) ->
            {
            }, (key, error) -> failures.add(key));
            if (failures.isEmpty())
            {
                Thread.sleep(5L);
            }
        }
    }
}
