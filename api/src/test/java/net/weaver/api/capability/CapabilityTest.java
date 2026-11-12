package net.weaver.api.capability;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityTest
{
    @Test
    void capabilityIdsAreValidatedAndComparable()
    {
        CapabilityId id = CapabilityId.of("example", "health/current");

        assertEquals("example", id.namespace());
        assertEquals("health/current", id.path());
        assertEquals("example:health/current", id.id());
        assertEquals(id, CapabilityId.parse("example:health/current"));
        assertEquals(0, id.compareTo(CapabilityId.parse("example:health/current")));
        assertTrue(CapabilityId.of("a", "z").compareTo(CapabilityId.of("b", "a")) < 0);
        assertTrue(CapabilityId.of("a", "a").compareTo(CapabilityId.of("a", "b")) < 0);

        assertThrows(IllegalArgumentException.class,
                () -> CapabilityId.of("Bad", "path"));
        assertThrows(IllegalArgumentException.class,
                () -> CapabilityId.of("ok", "Bad Path"));
        assertThrows(IllegalArgumentException.class,
                () -> CapabilityId.of("ok", "path:with:colon"));
        assertThrows(IllegalArgumentException.class, () -> CapabilityId.parse("missing"));
        assertThrows(IllegalArgumentException.class,
                () -> CapabilityId.parse("bad namespace:path"));
        assertThrows(IllegalArgumentException.class, () -> CapabilityId.parse("ok:"));
        assertThrows(IllegalArgumentException.class, () -> CapabilityId.parse(":path"));
        assertThrows(NullPointerException.class, () -> CapabilityId.of(null, "path"));
        assertThrows(NullPointerException.class, () -> CapabilityId.of("ok", null));
        assertThrows(NullPointerException.class, () -> CapabilityId.parse(null));
    }

    @Test
    void attachmentTypeBuilderDefaultsAndOverrides()
    {
        CapabilityCodec<String> codec = DataCodecsHolder.STRING;

        AttachmentType<String> runtime = AttachmentType.runtime("test:runtime", String.class);

        assertEquals("test:runtime", runtime.id());
        assertEquals("test", runtime.namespace());
        assertEquals("runtime", runtime.path());
        assertEquals(String.class, runtime.valueType());
        assertNull(runtime.defaultValue());
        assertNull(runtime.codec());
        assertEquals(0, runtime.maxEncodedBytes());
        assertFalse(runtime.copyOnItemCopy());
        assertEquals(CapabilitySync.NONE, runtime.sync());
        assertFalse(runtime.persistent());
        assertTrue(runtime.toString().contains("test:runtime"));
        assertTrue(runtime.toString().contains(String.class.getName()));

        AttachmentType<String> persistent = AttachmentType.builder("test:persistent",
                String.class).codec(codec, 64).build();

        assertSame(codec, persistent.codec());
        assertEquals(64, persistent.maxEncodedBytes());
        assertTrue(persistent.copyOnItemCopy());
        assertEquals(CapabilitySync.ITEM_STACK, persistent.sync());
        assertTrue(persistent.persistent());

        AttachmentType<String> overridden = AttachmentType.builder("test:overridden",
                String.class).codec(codec, 8).copyOnItemCopy(false)
                .sync(CapabilitySync.NONE).build();

        assertFalse(overridden.copyOnItemCopy());
        assertEquals(CapabilitySync.NONE, overridden.sync());

        AttachmentType<String> factory = AttachmentType.of("test:factory", String.class, codec);

        assertEquals(16384, factory.maxEncodedBytes());
        assertTrue(factory.copyOnItemCopy());
        assertEquals(CapabilitySync.ITEM_STACK, factory.sync());
        assertTrue(factory.persistent());
    }

    @Test
    void attachmentTypeDefaultsAndSuppliersAreEvaluatedOnEveryRead()
    {
        AttachmentType<String> constant = AttachmentType.builder("test:constant",
                String.class).defaultValue("fallback").build();

        assertEquals("fallback", constant.defaultValue());
        assertEquals("fallback", constant.defaultValue());

        AtomicInteger calls = new AtomicInteger();
        AttachmentType<String> supplied = AttachmentType.builder("test:supplied",
                String.class).defaultSupplier(() -> "value" + calls.incrementAndGet()).build();

        assertEquals("value1", supplied.defaultValue());
        assertEquals("value2", supplied.defaultValue());
        assertEquals(2, calls.get());

        AttachmentType<String> replaced = AttachmentType.builder("test:replaced",
                String.class).defaultSupplier(() -> "supplier").defaultValue("constant").build();

        assertEquals("constant", replaced.defaultValue());

        AttachmentType<String> replacedAgain = AttachmentType.builder("test:replaced_again",
                String.class).defaultValue("constant").defaultSupplier(() -> "supplier").build();

        assertEquals("supplier", replacedAgain.defaultValue());
    }

    @Test
    void attachmentTypeBuilderValidation()
    {
        CapabilityCodec<String> codec = DataCodecsHolder.STRING;

        assertThrows(NullPointerException.class,
                () -> AttachmentType.builder("test:x", null));
        assertThrows(NullPointerException.class,
                () -> AttachmentType.builder(null, String.class));
        assertThrows(CapabilityException.class,
                () -> AttachmentType.builder("invalid", String.class).build());
        assertThrows(NullPointerException.class,
                () -> AttachmentType.builder("test:x", String.class).codec(null, 16));
        assertThrows(CapabilityException.class,
                () -> AttachmentType.builder("test:x", String.class).codec(codec, 0).build());
        assertThrows(CapabilityException.class,
                () -> AttachmentType.builder("test:x", String.class).codec(codec, -1).build());
        assertThrows(CapabilityException.class,
                () -> AttachmentType.builder("test:x", String.class).codec(codec, 16385).build());
        assertThrows(NullPointerException.class,
                () -> AttachmentType.builder("test:x", String.class).sync(null));

        assertEquals(16384, AttachmentType.builder("test:x", String.class)
                .codec(codec, 16384).build().maxEncodedBytes());
    }

    @Test
    void savedOnlyTypesMayDeclareALargerBound()
    {
        CapabilityCodec<String> codec = DataCodecsHolder.STRING;

        assertEquals(1 << 20, AttachmentType.builder("test:big", String.class)
                .codec(codec, 1 << 20).sync(CapabilitySync.NONE).build().maxEncodedBytes());
        assertEquals(65536, AttachmentType.builder("test:big", String.class)
                .sync(CapabilitySync.NONE).codec(codec, 65536).build().maxEncodedBytes());
        assertThrows(CapabilityException.class,
                () -> AttachmentType.builder("test:big", String.class)
                        .codec(codec, (1 << 20) + 1).sync(CapabilitySync.NONE).build());
        assertThrows(CapabilityException.class,
                () -> AttachmentType.builder("test:big", String.class)
                        .codec(codec, 16385).sync(CapabilitySync.RUNTIME).build());
        assertThrows(CapabilityException.class,
                () -> AttachmentType.builder("test:big", String.class)
                        .codec(codec, 16385).sync(CapabilitySync.ITEM_STACK).build());
    }

    @Test
    void runtimeStoreStoresReadsRemovesAndClears()
    {
        RuntimeAttachmentStore store = new RuntimeAttachmentStore();
        Capability<String> capability = AttachmentType.runtime("test:value", String.class);
        Object holder = new Object();

        assertTrue(store.supports(holder, capability));
        assertNull(store.get(holder, capability));
        assertFalse(store.has(holder, capability));
        assertEquals(0, store.size());
        assertEquals(0, store.holderCount());

        assertNull(store.set(holder, capability, "first"));
        assertTrue(store.has(holder, capability));
        assertEquals("first", store.get(holder, capability));
        assertEquals(1, store.size());
        assertEquals(1, store.holderCount());

        assertEquals("first", store.set(holder, capability, "second"));
        assertEquals("second", store.get(holder, capability));

        assertEquals("second", store.remove(holder, capability));
        assertNull(store.get(holder, capability));
        assertFalse(store.has(holder, capability));
        assertEquals(0, store.size());
        assertEquals(0, store.holderCount());

        store.set(holder, capability, "again");
        store.clear(holder);
        assertNull(store.get(holder, capability));
        assertEquals(0, store.size());
        assertEquals(0, store.holderCount());
    }

    @Test
    void runtimeStoreUsesIdentitySemantics()
    {
        RuntimeAttachmentStore store = new RuntimeAttachmentStore();
        Capability<String> capability = AttachmentType.runtime("test:identity", String.class);
        Object first = new String("holder");
        Object second = new String("holder");

        assertEquals(first, second);
        assertNotSame(first, second);

        store.set(first, capability, "one");

        assertNull(store.get(second, capability));
        assertFalse(store.has(second, capability));

        store.set(second, capability, "two");

        assertEquals("one", store.get(first, capability));
        assertEquals("two", store.get(second, capability));
        assertEquals(2, store.holderCount());
        assertEquals(2, store.size());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void runtimeStoreRejectsNullAndWrongTypes()
    {
        RuntimeAttachmentStore store = new RuntimeAttachmentStore();
        Capability<String> capability = AttachmentType.runtime("test:typed", String.class);
        Capability<Object> raw = (Capability) capability;
        Object holder = new Object();

        assertThrows(CapabilityException.class, () -> store.set(holder, raw, 42));
        assertThrows(NullPointerException.class, () -> store.set(holder, capability, null));
        assertThrows(NullPointerException.class, () -> store.get(null, capability));
        assertThrows(NullPointerException.class, () -> store.get(holder, null));
        assertThrows(NullPointerException.class, () -> store.set(null, capability, "value"));
        assertThrows(NullPointerException.class, () -> store.remove(holder, null));
        assertThrows(NullPointerException.class, () -> store.has(null, capability));
        assertThrows(NullPointerException.class, () -> store.clear(null));
        assertThrows(NullPointerException.class, () -> store.supports(null, capability));
        assertThrows(NullPointerException.class, () -> store.supports(holder, null));
    }

    @Test
    void runtimeStorePriorityIsLowest()
    {
        RuntimeAttachmentStore store = new RuntimeAttachmentStore();

        assertEquals(Integer.MIN_VALUE, store.priority());
    }

    @Test
    void runtimeStoreHandlesConcurrentAccess() throws Exception
    {
        RuntimeAttachmentStore store = new RuntimeAttachmentStore();
        Capability<Integer> capability = AttachmentType.runtime("test:counter", Integer.class);
        Object shared = new Object();
        store.set(shared, capability, 7);

        int threads = 8;
        List<Object> holders = new ArrayList<>();
        for (int thread = 0; thread < threads; thread++)
        {
            holders.add(new Object());
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try
        {
            for (int thread = 0; thread < threads; thread++)
            {
                final int index = thread;
                final Object local = holders.get(thread);
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int iteration = 0; iteration < 100; iteration++)
                    {
                        assertEquals(7, store.get(shared, capability).intValue());
                        Integer previous =
                                store.set(local, capability, index * 1000 + iteration);
                        if (iteration == 0)
                        {
                            assertNull(previous);
                        }
                        else
                        {
                            assertEquals(index * 1000 + iteration - 1,
                                    previous.intValue());
                        }
                        assertEquals(index * 1000 + iteration,
                                store.get(local, capability).intValue());
                    }
                    assertEquals(index * 1000 + 99,
                            store.get(local, capability).intValue());
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures)
            {
                future.get(30, TimeUnit.SECONDS);
            }
        }
        finally
        {
            pool.shutdownNow();
        }

        assertEquals(threads + 1, store.holderCount());
        assertEquals(threads + 1, store.size());
        assertEquals(7, store.get(shared, capability).intValue());
    }

    private static final class DataCodecsHolder
    {
        private static final CapabilityCodec<String> STRING =
                net.weaver.api.data.DataCodecs.utf8String(64);
    }
}
