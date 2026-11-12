package net.weaver.api.registry;

import net.weaver.api.ModContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryTest
{
    private FakeBackend backend;
    private List<String> logs;

    @BeforeEach
    void setUp()
    {
        RegistryManager.reset();
        backend = new FakeBackend("minecraft:block", "minecraft:item", "minecraft:entity_type");
        logs = new ArrayList<>();
        RegistryManager.install(backend, logs::add);
    }

    @AfterEach
    void tearDown()
    {
        RegistryManager.reset();
    }

    @Test
    void deferredRegistrationBindsInOrderAndExposesLookups()
    {
        DeferredRegister<String> blocks = DeferredRegister.create(WeaverRegistries.BLOCK, "test");
        AtomicInteger supplierCalls = new AtomicInteger();
        RegistryEntry<String> first = blocks.register("first", () -> {
            supplierCalls.incrementAndGet();
            return "First";
        });
        RegistryEntry<String> second = blocks.register("second", () -> {
            supplierCalls.incrementAndGet();
            return "Second";
        });
        RegistryEntry<String> third = blocks.register("other:third", () -> {
            supplierCalls.incrementAndGet();
            return "Third";
        });

        assertEquals(3, blocks.size());
        assertEquals(0, supplierCalls.get());
        assertEquals(List.of(first, second, third), blocks.entries());
        assertFalse(first.isPresent());
        assertFalse(first.isBound());
        assertNull(first.getOrNull());
        assertTrue(first.optional().isEmpty());
        assertTrue(first.holderOptional(String.class).isEmpty());
        assertThrows(RegistryException.class, first::get);

        RegistryManager.flushAll();

        assertEquals(3, supplierCalls.get());
        assertTrue(first.isPresent());
        assertTrue(first.isBound());
        assertEquals("First", first.get());
        assertEquals("First", first.optional().orElseThrow());
        assertNotNull(first.holder());
        assertEquals(first.holder(), first.holder(String.class));
        assertTrue(first.holderOptional(Integer.class).isEmpty());
        assertThrows(RegistryException.class, () -> first.holder(Integer.class));

        AtomicReference<String> observed = new AtomicReference<>();
        first.ifPresent(observed::set);
        assertEquals("First", observed.get());

        RegistryView<String> registry = RegistryManager.registry(WeaverRegistries.BLOCK);
        assertEquals("First", registry.get(first.key()));
        assertEquals("First", registry.get("test:first"));
        assertEquals("Second", registry.getOptional(second.key()).orElseThrow());
        assertTrue(registry.containsKey(third.key()));
        assertTrue(registry.containsKey("other:third"));
        assertEquals(3, registry.size());
        assertTrue(registry.keySet().contains(first.key()));
        assertTrue(registry.values().contains("Second"));
        assertEquals(3, registry.entries().size());
        assertEquals(first.key(), registry.getEntry(first.key()).orElseThrow().key());
        assertEquals("First", registry.getEntry("test:first").orElseThrow().get());
        assertEquals(List.of("test:first", "test:second", "other:third"),
                backend.keys("minecraft:block"));
    }

    @Test
    void failedSupplierMarksOnlyThatEntryAndIsNotRetried()
    {
        DeferredRegister<String> blocks = DeferredRegister.create(WeaverRegistries.BLOCK, "test");
        RegistryEntry<String> good = blocks.register("good", () -> "Good");
        AtomicInteger badCalls = new AtomicInteger();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        RegistryEntry<String> bad = blocks.register("bad", () -> {
            badCalls.incrementAndGet();
            final IllegalStateException failure = new IllegalStateException("boom");
            thrown.set(failure);
            throw failure;
        });
        RegistryEntry<String> after = blocks.register("after", () -> "After");

        RegistryManager.flushAll();

        assertTrue(good.isPresent());
        assertTrue(after.isPresent());
        assertFalse(bad.isPresent());
        assertFalse(bad.isBound());
        assertNull(bad.getOrNull());
        RegistryException error = assertThrows(RegistryException.class, bad::get);
        assertSame(thrown.get(), error.getCause());
        assertSame(thrown.get(), bad.failure());
        assertFalse(backend.containsKey("minecraft:block", "test", "bad"));
        assertTrue(logs.stream().anyMatch(line -> line.contains("test:bad")));

        RegistryManager.flushAll();

        assertEquals(1, badCalls.get());
        assertFalse(bad.isPresent());
        assertEquals(2, backend.keys("minecraft:block").size());
    }

    @Test
    void nullSupplierReturnedValueFailsOnlyThatEntry()
    {
        DeferredRegister<String> blocks = DeferredRegister.create(WeaverRegistries.BLOCK, "test");
        RegistryEntry<String> nullValue = blocks.register("null_value", () -> null);
        RegistryEntry<String> good = blocks.register("good", () -> "Good");

        RegistryManager.flushAll();

        assertFalse(nullValue.isPresent());
        assertTrue(good.isPresent());
        assertTrue(logs.stream().anyMatch(line -> line.contains("test:null_value")));
    }

    @Test
    void duplicateDeferredKeysAreRejected()
    {
        DeferredRegister<String> blocks = DeferredRegister.create(WeaverRegistries.BLOCK, "test");
        RegistryEntry<String> first = blocks.register("thing", () -> "Thing");

        assertThrows(RegistryException.class, () -> blocks.register("thing", () -> "Other"));
        assertThrows(RegistryException.class, () -> blocks.register("test:thing", () -> "Other"));
        assertThrows(NullPointerException.class, () -> blocks.register("null_supplier", null));

        assertEquals(1, blocks.size());
        assertSame(first, blocks.entries().get(0));
    }

    @Test
    void backendDuplicateFailsOnlyOneEntryAndContinues()
    {
        DeferredRegister<String> firstRegister =
                DeferredRegister.create(WeaverRegistries.BLOCK, "one");
        RegistryEntry<String> first = firstRegister.register("same", () -> "First");

        DeferredRegister<String> secondRegister =
                DeferredRegister.create(WeaverRegistries.BLOCK, "two");
        RegistryEntry<String> duplicate = secondRegister.register("one:same", () -> "Second");
        RegistryEntry<String> after = secondRegister.register("after", () -> "After");

        RegistryManager.flushAll();

        assertTrue(first.isPresent());
        assertFalse(duplicate.isPresent());
        assertTrue(after.isPresent());
        assertEquals("First", backend.get("minecraft:block", "one", "same"));
        assertEquals("After", backend.get("minecraft:block", "two", "after"));
        assertTrue(logs.stream().anyMatch(line -> line.contains("one:same")));
    }

    @Test
    void frozenRegistryRejectsDirectWritesAndFailsDeferredEntries()
    {
        RegistryView<String> registry = RegistryManager.registry(WeaverRegistries.BLOCK);
        registry.register(RegistryKey.of("test", "direct"), "Direct");

        RegistryManager.markFrozen();

        assertTrue(registry.isFrozen());
        assertThrows(RegistryException.class,
                () -> registry.register(RegistryKey.of("test", "late"), "Late"));
        assertEquals("Direct", registry.get("test:direct"));
        assertTrue(RegistryManager.registry(WeaverRegistries.ITEM).isFrozen());

        DeferredRegister<String> blocks = DeferredRegister.create(WeaverRegistries.BLOCK, "test");
        RegistryEntry<String> deferred = blocks.register("deferred", () -> "Deferred");

        RegistryManager.flushAll();

        assertFalse(deferred.isPresent());
        assertFalse(deferred.isBound());
        assertThrows(RegistryException.class, deferred::get);
        assertTrue(logs.stream().anyMatch(line -> line.contains("frozen")));
    }

    @Test
    void readCachesAreRefreshedWhenRegistrationBegins()
    {
        RegistryView<String> blocks = RegistryManager.registry(WeaverRegistries.BLOCK);
        // A mod reads the registry from onInitialize, before vanilla fills it.
        assertEquals(0, blocks.size());

        backend.register(backend.nativeRegistry("minecraft:block"), "minecraft", "stone", "Stone");
        RegistryManager.beginRegistration();

        assertEquals(1, blocks.size());
        assertTrue(blocks.getEntry("minecraft:stone").isPresent());
        assertEquals(List.of("Stone"), List.copyOf(blocks.values()));
    }

    @Test
    void readCachesAreRefreshedAfterAFlush()
    {
        RegistryView<String> blocks = RegistryManager.registry(WeaverRegistries.BLOCK);
        assertEquals(0, blocks.keySet().size());
        backend.register(backend.nativeRegistry("minecraft:block"), "minecraft", "dirt", "Dirt");

        RegistryManager.flushAll();

        assertTrue(blocks.containsKey("minecraft:dirt"));
        assertEquals(1, blocks.keySet().size());
    }

    @Test
    void entryRegisteredAfterFreezeFailsImmediatelyWithoutAFlush()
    {
        RegistryManager.markFrozen();
        DeferredRegister<String> blocks = DeferredRegister.create(WeaverRegistries.BLOCK, "late");
        AtomicInteger supplierCalls = new AtomicInteger();

        RegistryEntry<String> late = blocks.register("setup_entry", () -> {
            supplierCalls.incrementAndGet();
            return "Late";
        });

        assertFalse(late.isBound());
        assertThrows(RegistryException.class, late::get);
        assertEquals(0, supplierCalls.get());
        assertTrue(logs.stream().anyMatch(line -> line.contains("late:setup_entry")
                && line.contains("frozen")), String.join("\n", logs));
    }

    @Test
    void duplicateKeyIsRejectedBeforeTheSupplierRuns()
    {
        DeferredRegister<String> first = DeferredRegister.create(WeaverRegistries.BLOCK, "one");
        first.register("ruby", () -> "First");
        DeferredRegister<String> second = DeferredRegister.create(WeaverRegistries.BLOCK, "two");
        AtomicInteger secondSupplier = new AtomicInteger();
        RegistryEntry<String> duplicate = second.register("one:ruby", () -> {
            secondSupplier.incrementAndGet();
            return "Second";
        });

        RegistryManager.flushAll();

        assertFalse(duplicate.isPresent());
        assertEquals(0, secondSupplier.get(), "a duplicate must not construct its value");
    }

    @Test
    void entriesAddedBySupplierDuringFlushAreAlsoBound()
    {
        DeferredRegister<String> blocks = DeferredRegister.create(WeaverRegistries.BLOCK, "chain");
        AtomicReference<RegistryEntry<String>> nested = new AtomicReference<>();
        RegistryEntry<String> outer = blocks.register("outer", () -> {
            nested.set(blocks.register("nested", () -> "Nested"));
            return "Outer";
        });

        RegistryManager.flushAll();

        assertTrue(outer.isPresent());
        assertTrue(nested.get().isPresent(), "an entry created during the flush must not stay unbound");
    }

    @Test
    void malformedRegistryIdDoesNotAbortFreeze()
    {
        backend.advertise("Invalid Namespace:thing", "bad id:thing");
        RegistryView<String> blocks = RegistryManager.registry(WeaverRegistries.BLOCK);

        RegistryManager.FreezeReport report = RegistryManager.markFrozenWithReport();

        assertTrue(blocks.isFrozen());
        assertTrue(RegistryManager.registry(WeaverRegistries.ITEM).isFrozen());
        assertTrue(report.frozenIds().contains("minecraft:block"));
        assertTrue(report.frozenIds().contains("minecraft:item"));
        assertTrue(report.frozenIds().contains("minecraft:entity_type"));
        assertTrue(report.failures().containsKey("Invalid Namespace:thing"));
        assertTrue(report.failures().containsKey("bad id:thing"));
        assertFalse(report.isComplete());
        assertTrue(logs.stream().anyMatch(line -> line.contains("Invalid Namespace:thing")));
    }

    @Test
    void registryIdDiscoveryFailureDoesNotAbortFreeze()
    {
        RegistryView<String> blocks = RegistryManager.registry(WeaverRegistries.BLOCK);
        backend.throwOnRegistryIds = true;

        RegistryManager.FreezeReport report = RegistryManager.markFrozenWithReport();

        assertTrue(blocks.isFrozen());
        assertTrue(report.discoveryFailed());
        assertEquals(1, report.frozenCount());
        assertEquals(List.of("minecraft:block"), report.frozenIds());
        assertTrue(logs.stream().anyMatch(
                line -> line.contains("REGISTRY FREEZE FAILED discovery")));
    }

    @Test
    void backendReadFailuresReturnEmptyFallbacksAndLog()
    {
        RegistryView<String> items = RegistryManager.registry(WeaverRegistries.ITEM);
        items.register(RegistryKey.of("test", "ruby"), "Ruby");

        backend.throwOnKeys = true;
        backend.throwOnEntries = true;
        backend.throwOnGet = true;
        backend.throwOnContains = true;

        assertTrue(items.keySet().isEmpty());
        assertTrue(items.values().isEmpty());
        assertTrue(items.entries().isEmpty());
        assertEquals(0, items.size());
        assertNull(items.get("test:ruby"));
        assertFalse(items.containsKey("test:ruby"));
        assertTrue(items.getEntry("test:ruby").isEmpty());
        assertTrue(logs.stream().anyMatch(line -> line.contains("KEYS")));
        assertTrue(logs.stream().anyMatch(line -> line.contains("VALUES")));
        assertTrue(logs.stream().anyMatch(line -> line.contains("ENTRIES")));
        assertTrue(logs.stream().anyMatch(line -> line.contains("GET")));
        assertTrue(logs.stream().anyMatch(line -> line.contains("CONTAINS")));

        backend.throwOnKeys = false;
        backend.throwOnEntries = false;
        backend.throwOnGet = false;
        backend.throwOnContains = false;

        assertEquals(1, items.size());
        assertTrue(items.keySet().contains(RegistryKey.of("test", "ruby")));
        assertEquals("Ruby", items.get("test:ruby"));
        assertTrue(items.getEntry("test:ruby").isPresent());
    }

    @Test
    void freezeFailureIsIsolated()
    {
        RegistryView<String> blocks = RegistryManager.registry(WeaverRegistries.BLOCK);
        RegistryView<String> items = RegistryManager.registry(WeaverRegistries.ITEM);
        backend.failFreeze("minecraft:block");

        RegistryManager.FreezeReport report = RegistryManager.markFrozenWithReport();

        assertFalse(blocks.isFrozen());
        assertTrue(items.isFrozen());
        assertTrue(report.failures().containsKey("minecraft:block"));
        assertTrue(report.frozenIds().contains("minecraft:item"));
        assertTrue(report.frozenIds().contains("minecraft:entity_type"));
        assertFalse(report.isComplete());
        assertTrue(logs.stream().anyMatch(
                line -> line.contains("REGISTRY FREEZE FAILED id=minecraft:block")));
    }

    @Test
    void freezeWithoutBackendIsSafe()
    {
        RegistryManager.reset();

        RegistryManager.FreezeReport report = RegistryManager.markFrozenWithReport();

        assertEquals(0, report.frozenCount());
        assertTrue(report.frozenIds().isEmpty());
        assertTrue(report.failures().isEmpty());
        assertFalse(report.discoveryFailed());
        assertTrue(report.isComplete());
        RegistryManager.markFrozen();
    }

    @Test
    void freezeInvalidatesCachesButPreservesContent()
    {
        RegistryView<String> items = RegistryManager.registry(WeaverRegistries.ITEM);
        RegistryEntry<String> ruby = items.register(RegistryKey.of("test", "ruby"), "Ruby");
        assertEquals(1, items.size());
        assertEquals(1, items.entries().size());

        RegistryManager.markFrozen();

        assertEquals(1, items.size());
        assertTrue(items.keySet().contains(ruby.key()));
        assertTrue(items.values().contains("Ruby"));
        assertEquals(1, items.entries().size());
        assertTrue(items.getEntry(ruby.key()).isPresent());
    }

    @Test
    void concurrentReadsAreConsistent() throws Exception
    {
        RegistryView<String> items = RegistryManager.registry(WeaverRegistries.ITEM);
        for (int index = 0; index < 32; index++)
        {
            items.register(RegistryKey.of("test", "key_" + index), "Value" + index);
        }
        RegistryManager.markFrozen();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try
        {
            for (int reader = 0; reader < 8; reader++)
            {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int read = 0; read < 100; read++)
                    {
                        assertEquals(32, items.size());
                        assertEquals(32, items.keySet().size());
                        assertEquals(32, items.values().size());
                        assertEquals(32, items.entries().size());
                        assertEquals("Value7", items.get("test:key_7"));
                    }
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
    }

    @Test
    void missingRegistryFailsOnlyItsGroup()
    {
        DeferredRegister<String> blocks = DeferredRegister.create(WeaverRegistries.BLOCK, "test");
        RegistryEntry<String> block = blocks.register("block", () -> "Block");
        DeferredRegister<String> dialogs =
                DeferredRegister.create(WeaverRegistries.DIALOG_TYPE, "test");
        RegistryEntry<String> dialog = dialogs.register("dialog", () -> "Dialog");

        RegistryManager.flushAll();

        assertTrue(block.isPresent());
        assertFalse(dialog.isPresent());
        assertFalse(dialog.isBound());
        assertThrows(RegistryException.class, dialog::get);
        assertTrue(logs.stream().anyMatch(line -> line.contains("minecraft:dialog_type")));
    }

    @Test
    void missingBackendRejectsRegistryAndFailsDeferredEntries()
    {
        RegistryManager.reset();

        assertFalse(RegistryManager.isInstalled());
        assertTrue(RegistryManager.findRegistry(WeaverRegistries.BLOCK).isEmpty());
        assertThrows(RegistryException.class,
                () -> RegistryManager.registry(WeaverRegistries.BLOCK));
        assertTrue(RegistryManager.registryIds().isEmpty());
        assertTrue(WeaverRegistries.availableIds().isEmpty());

        DeferredRegister<String> blocks = DeferredRegister.create(WeaverRegistries.BLOCK, "test");
        RegistryEntry<String> entry = blocks.register("entry", () -> "Entry");

        RegistryManager.flushAll();

        assertFalse(entry.isPresent());
        assertThrows(RegistryException.class, entry::get);
    }

    @Test
    void directRegistrationInvalidatesCachesAndSupportsStringIds()
    {
        RegistryView<String> registry = RegistryManager.registry(WeaverRegistries.ITEM);
        assertEquals(0, registry.size());
        assertTrue(registry.keySet().isEmpty());
        assertTrue(registry.values().isEmpty());
        assertTrue(registry.entries().isEmpty());

        RegistryEntry<String> entry = registry.register(RegistryKey.of("test", "ruby"), "Ruby");

        assertTrue(entry.isPresent());
        assertTrue(entry.isBound());
        assertEquals("Ruby", entry.get());
        assertEquals(1, registry.size());
        assertTrue(registry.keySet().contains(entry.key()));
        assertTrue(registry.values().contains("Ruby"));
        assertEquals(1, registry.entries().size());
        assertEquals(entry.key(), registry.entries().get(0).key());
        assertEquals("Ruby", registry.get(entry.key()));
        assertEquals("Ruby", registry.get("test:ruby"));
        assertTrue(registry.getOptional(entry.key()).isPresent());
        assertTrue(registry.getEntry(entry.key()).isPresent());
        assertTrue(registry.containsKey("test:ruby"));
        assertFalse(registry.containsKey("test:missing"));
        assertNull(registry.get("test:missing"));
        assertTrue(registry.getOptional(RegistryKey.of("test", "missing")).isEmpty());
        assertTrue(registry.getEntry("test:missing").isEmpty());

        RegistryEntry<String> second = registry.register("test:sapphire", "Sapphire");

        assertEquals(2, registry.size());
        assertEquals("Sapphire", second.get());
        assertEquals("Sapphire", registry.get("test:sapphire"));
    }

    @Test
    void tagViewsAreReadOnlyAndRegistryScoped()
    {
        RegistryView<String> items = RegistryManager.registry(WeaverRegistries.ITEM);
        items.register(RegistryKey.of("test", "ruby"), "Ruby");
        backend.tag("minecraft:item", "test:gems", List.of("Ruby"));

        TagKey<String> gems = TagKey.of(WeaverRegistries.ITEM, "test:gems");
        TagView<String> view = items.getTag(gems);

        assertEquals(gems, view.key());
        assertEquals(1, view.size());
        assertTrue(view.contains("Ruby"));
        assertFalse(view.isEmpty());
        assertEquals(List.of("Ruby"), view.values());
        assertThrows(RegistryException.class,
                () -> items.getTag(TagKey.of(WeaverRegistries.BLOCK, "test:gems")));
    }

    @Test
    void registryLookupAndIdsReflectBackend()
    {
        assertTrue(RegistryManager.isInstalled());
        assertSame(backend, RegistryManager.backend());
        assertTrue(RegistryManager.registryIds().contains("minecraft:block"));
        assertTrue(WeaverRegistries.availableIds().contains("minecraft:block"));
        assertTrue(RegistryManager.findRegistry(WeaverRegistries.BLOCK).isPresent());
        assertTrue(RegistryManager.findRegistry(WeaverRegistries.DIALOG_TYPE).isEmpty());
        assertThrows(RegistryException.class,
                () -> RegistryManager.registry(WeaverRegistries.DIALOG_TYPE));
    }

    @Test
    void registrationPhaseAndLoggingAreTracked()
    {
        assertFalse(RegistryManager.isRegistrationPhase());

        RegistryManager.beginRegistration();
        assertTrue(RegistryManager.isRegistrationPhase());

        RegistryManager.endRegistration();
        assertFalse(RegistryManager.isRegistrationPhase());

        RegistryManager.log("message");
        assertEquals(List.of("message"), logs);
    }

    @Test
    void contextFactoryUsesContextNamespaceAndTracksTheRegister()
    {
        DeferredRegister<String> items =
                DeferredRegister.create(context("ctx"), WeaverRegistries.ITEM);
        RegistryEntry<String> entry = items.register("thing", () -> "Thing");

        assertEquals("ctx", items.namespace());
        assertEquals(WeaverRegistries.ITEM, items.registryKey());
        assertEquals("ctx:thing", entry.id());

        RegistryManager.flushAll();

        assertTrue(entry.isPresent());
        assertEquals("Thing", RegistryManager.<String>registry(WeaverRegistries.ITEM)
                .get("ctx:thing"));
    }

    @Test
    void weaverRegistryConstantsMatchSpecification()
    {
        assertEquals("minecraft:block", WeaverRegistries.BLOCK.id());
        assertEquals("minecraft:item", WeaverRegistries.ITEM.id());
        assertEquals("minecraft:entity_type", WeaverRegistries.ENTITY_TYPE.id());
        assertEquals("minecraft:block_entity_type", WeaverRegistries.BLOCK_ENTITY_TYPE.id());
        assertEquals("minecraft:particle_type", WeaverRegistries.PARTICLE_TYPE.id());
        assertEquals("minecraft:recipe_serializer", WeaverRegistries.RECIPE_SERIALIZER.id());
        assertEquals("minecraft:enchantment_effect_component_type",
                WeaverRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.id());
        assertEquals("minecraft:ticket_type", WeaverRegistries.TICKET_TYPE.id());
        assertEquals("minecraft:worldgen/structure_piece",
                WeaverRegistries.STRUCTURE_PIECE.id());
        assertEquals("minecraft:data_component_predicate_type",
                WeaverRegistries.DATA_COMPONENT_PREDICATE_TYPE.id());
        assertEquals("minecraft:dialog_type", WeaverRegistries.DIALOG_TYPE.id());
        assertEquals("minecraft:environment_attribute",
                WeaverRegistries.ENVIRONMENT_ATTRIBUTE.id());
        assertEquals("minecraft:attribute_type", WeaverRegistries.ATTRIBUTE_TYPE.id());
        assertEquals("examplemod:custom", WeaverRegistries.of("examplemod:custom").id());
        assertEquals("examplemod:custom",
                WeaverRegistries.of("examplemod", "custom").id());
        assertThrows(IllegalArgumentException.class,
                () -> WeaverRegistries.of("Invalid Namespace:thing"));
    }

    private static ModContext context(String id)
    {
        return new ModContext()
        {
            @Override
            public String id()
            {
                return id;
            }

            @Override
            public String side()
            {
                return "server";
            }

            @Override
            public Path configDir()
            {
                return Path.of(".");
            }

            @Override
            public Path dataDir()
            {
                return Path.of(".");
            }

            @Override
            public void log(String message)
            {
            }
        };
    }

    private static final class FakeBackend implements RegistryBackend
    {
        private final Map<String, Map<String, Object>> registries = new LinkedHashMap<>();
        private final Map<String, List<Object>> tags = new LinkedHashMap<>();
        private final Map<Object, Object> holders = new IdentityHashMap<>();
        private final Set<Object> frozen = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<String> advertisedIds = new LinkedHashSet<>();
        private final Set<String> freezeFailures = new LinkedHashSet<>();
        private boolean throwOnRegistryIds;
        private boolean throwOnGet;
        private boolean throwOnContains;
        private boolean throwOnKeys;
        private boolean throwOnEntries;

        private FakeBackend(String... registryIds)
        {
            for (String id : registryIds)
            {
                registries.put(id, new LinkedHashMap<>());
            }
        }

        @Override
        public boolean isAvailable()
        {
            return true;
        }

        @Override
        public Set<String> registryIds()
        {
            if (throwOnRegistryIds)
            {
                throw new IllegalStateException("registryIds boom");
            }
            final Set<String> ids = new LinkedHashSet<>(registries.keySet());
            ids.addAll(advertisedIds);
            return ids;
        }

        private void advertise(String... ids)
        {
            advertisedIds.addAll(List.of(ids));
        }

        private void failFreeze(String registryId)
        {
            freezeFailures.add(registryId);
        }

        @Override
        public Object nativeRegistry(String registryId)
        {
            return registries.containsKey(registryId) ? registryId : null;
        }

        @Override
        public Object register(Object nativeRegistry, String namespace, String path,
                Object value)
        {
            final Map<String, Object> entries = nativeEntries(nativeRegistry);
            if (frozen.contains(nativeRegistry))
            {
                throw new RegistryException("RegistryView is frozen");
            }
            final String id = namespace + ":" + path;
            if (entries.containsKey(id))
            {
                throw new RegistryException("Duplicate entry: " + id);
            }
            entries.put(id, value);
            final Object holder = "holder:" + id;
            holders.put(value, holder);
            return holder;
        }

        @Override
        public Object get(Object nativeRegistry, String namespace, String path)
        {
            if (throwOnGet)
            {
                throw new IllegalStateException("get boom");
            }
            return nativeEntries(nativeRegistry).get(namespace + ":" + path);
        }

        @Override
        public boolean containsKey(Object nativeRegistry, String namespace, String path)
        {
            if (throwOnContains)
            {
                throw new IllegalStateException("containsKey boom");
            }
            return nativeEntries(nativeRegistry).containsKey(namespace + ":" + path);
        }

        @Override
        public List<String> keys(Object nativeRegistry)
        {
            if (throwOnKeys)
            {
                throw new IllegalStateException("keys boom");
            }
            return new ArrayList<>(nativeEntries(nativeRegistry).keySet());
        }

        @Override
        public List<Map.Entry<String, Object>> entries(Object nativeRegistry)
        {
            if (throwOnEntries)
            {
                throw new IllegalStateException("entries boom");
            }
            return new ArrayList<>(nativeEntries(nativeRegistry).entrySet());
        }

        @Override
        public Object holder(Object nativeRegistry, Object value)
        {
            return holders.get(value);
        }

        @Override
        public Object identifier(String namespace, String path)
        {
            return namespace + ":" + path;
        }

        @Override
        public List<Object> tagValues(Object nativeRegistry, String registryId,
                String tagNamespace, String tagPath)
        {
            return tags.getOrDefault(registryId + "#" + tagNamespace + ":" + tagPath, List.of());
        }

        private void tag(String registryId, String tagId, List<Object> values)
        {
            tags.put(registryId + "#" + tagId, List.copyOf(values));
        }

        @Override
        public void markFrozen(Object nativeRegistry)
        {
            if (freezeFailures.contains(nativeRegistry))
            {
                throw new RegistryException("freeze boom: " + nativeRegistry);
            }
            frozen.add(nativeRegistry);
        }

        @Override
        public boolean isFrozen(Object nativeRegistry)
        {
            return frozen.contains(nativeRegistry);
        }

        private Map<String, Object> nativeEntries(Object nativeRegistry)
        {
            final Map<String, Object> entries = registries.get((String) nativeRegistry);
            if (entries == null)
            {
                throw new RegistryException("Unknown registry: " + nativeRegistry);
            }
            return entries;
        }
    }
}
