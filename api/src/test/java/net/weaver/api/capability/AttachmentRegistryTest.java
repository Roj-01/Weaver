package net.weaver.api.capability;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentRegistryTest {

    @Test
    void registrationIsIdempotentAndRejectsDuplicateInstances() {
        AttachmentRegistry registry = new AttachmentRegistry();
        AttachmentType<String> capability = AttachmentType.runtime("test:cap", String.class);

        registry.register(capability);
        registry.register(capability);

        assertEquals(Optional.of(capability), registry.find("test:cap"));
        assertEquals(List.of(capability), registry.registeredCapabilities());
        assertTrue(registry.find("test:missing").isEmpty());
        assertThrows(CapabilityException.class,
                () -> registry.register(AttachmentType.runtime("test:cap", String.class)));
        assertThrows(CapabilityException.class,
                () -> registry.register(AttachmentType.runtime("test:cap", Integer.class)));
        assertThrows(NullPointerException.class, () -> registry.register(null));
        assertThrows(NullPointerException.class, () -> registry.find(null));

        List<Capability<?>> snapshot = registry.registeredCapabilities();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(capability));
    }

    @Test
    void storesRetrievesRemovesAndClearsRuntimeValues() {
        AttachmentRegistry registry = new AttachmentRegistry();
        AttachmentType<String> capability = AttachmentType.runtime("test:value", String.class);
        Object holder = new Object();

        assertNull(registry.get(holder, capability));
        assertTrue(registry.find(holder, capability).isEmpty());
        assertFalse(registry.has(holder, capability));
        assertNull(registry.remove(holder, capability));
        assertTrue(registry.supports(holder, capability));

        assertNull(registry.set(holder, capability, "first"));
        assertEquals("first", registry.get(holder, capability));
        assertEquals(Optional.of("first"), registry.find(holder, capability));
        assertTrue(registry.has(holder, capability));
        assertEquals("first", registry.set(holder, capability, "second"));
        assertEquals("second", registry.get(holder, capability));

        assertEquals("second", registry.remove(holder, capability));
        assertFalse(registry.has(holder, capability));
        assertTrue(registry.find(holder, capability).isEmpty());

        registry.set(holder, capability, "again");
        registry.clear(holder);
        assertFalse(registry.has(holder, capability));
    }

    @Test
    void nullSetRemovesAndMissingValuesUseDefaults() {
        AttachmentRegistry registry = new AttachmentRegistry();
        AttachmentType<String> capability = AttachmentType.builder("test:default", String.class)
                .defaultValue("fallback").build();
        Object holder = new Object();

        assertEquals("fallback", registry.get(holder, capability));
        assertTrue(registry.find(holder, capability).isEmpty());
        assertFalse(registry.has(holder, capability));
        assertNull(registry.remove(holder, capability));

        registry.set(holder, capability, "value");
        assertEquals("value", registry.get(holder, capability));
        assertEquals("value", registry.set(holder, capability, null));
        assertEquals("fallback", registry.get(holder, capability));
        assertTrue(registry.find(holder, capability).isEmpty());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void wrongPayloadTypeIsRejected() {
        AttachmentRegistry registry = new AttachmentRegistry();
        AttachmentType<String> capability = AttachmentType.runtime("test:wrong", String.class);
        Capability<Object> raw = (Capability) capability;
        Object holder = new Object();

        assertThrows(CapabilityException.class, () -> registry.set(holder, raw, 42));
        assertFalse(registry.has(holder, capability));
    }

    @Test
    void isolatedRegistriesDoNotShareValuesAndUseIdentity() {
        AttachmentRegistry first = new AttachmentRegistry();
        AttachmentRegistry second = new AttachmentRegistry();
        AttachmentType<String> capability = AttachmentType.runtime("test:isolated", String.class);
        Object holder = new Object();

        first.set(holder, capability, "value");

        assertEquals("value", first.get(holder, capability));
        assertNull(second.get(holder, capability));
        assertFalse(second.has(holder, capability));
        assertTrue(second.find(holder, capability).isEmpty());

        second.set(holder, capability, "other");

        assertEquals("value", first.get(holder, capability));
        assertEquals("other", second.get(holder, capability));

        Object equalOne = new String("holder");
        Object equalTwo = new String("holder");

        assertEquals(equalOne, equalTwo);
        first.set(equalOne, capability, "one");
        assertNull(first.get(equalTwo, capability));
        first.set(equalTwo, capability, "two");
        assertEquals("one", first.get(equalOne, capability));
        assertEquals("two", first.get(equalTwo, capability));
    }

    @Test
    void storePriorityAndRegistrationOrderChooseTheWriter() {
        AttachmentRegistry registry = new AttachmentRegistry();
        MapStore low = new MapStore(10);
        MapStore high = new MapStore(20);
        registry.registerStore(low);
        registry.registerStore(high);
        AttachmentType<String> capability = AttachmentType.runtime("test:priority", String.class);
        Object holder = new Object();

        assertNull(registry.set(holder, capability, "high"));
        assertEquals("high", high.get(holder, capability));
        assertNull(low.get(holder, capability));
        assertEquals("high", registry.get(holder, capability));
        assertEquals("high", registry.remove(holder, capability));
        assertFalse(high.has(holder, capability));

        AttachmentRegistry ties = new AttachmentRegistry();
        MapStore firstTie = new MapStore(5);
        MapStore secondTie = new MapStore(5);
        ties.registerStore(firstTie);
        ties.registerStore(secondTie);

        assertNull(ties.set(holder, capability, "first"));
        assertEquals("first", firstTie.get(holder, capability));
        assertNull(secondTie.get(holder, capability));
        assertEquals("first", ties.get(holder, capability));
    }

    @Test
    void storeRegistrationIsIdempotentAndRemovable() {
        AttachmentRegistry registry = new AttachmentRegistry();
        MapStore store = new MapStore(10);
        AttachmentType<String> capability = AttachmentType.runtime("test:store", String.class);
        Object holder = new Object();

        registry.registerStore(store);
        registry.registerStore(store);
        registry.set(holder, capability, "value");

        assertEquals("value", store.get(holder, capability));
        assertTrue(registry.removeStore(store));
        assertFalse(registry.removeStore(store));
        assertNull(registry.get(holder, capability));

        assertThrows(NullPointerException.class, () -> registry.registerStore(null));
        assertThrows(NullPointerException.class, () -> registry.supports(null, capability));
        assertThrows(NullPointerException.class, () -> registry.get(holder, null));
        assertThrows(NullPointerException.class, () -> registry.get(null, capability));
    }

    @Test
    void failingStoreFailsSoftAndRecordsDiagnostics() {
        AttachmentRegistry registry = new AttachmentRegistry();
        registry.registerStore(new FailingStore(100));
        AttachmentType<String> capability = AttachmentType.builder("test:failing", String.class)
                .defaultValue("fallback").build();
        Object holder = new Object();

        assertEquals("fallback", registry.get(holder, capability));
        assertTrue(registry.find(holder, capability).isEmpty());
        assertFalse(registry.has(holder, capability));
        assertEquals(3, registry.diagnostics().size());
        assertTrue(registry.diagnostics().get(0).contains("get"));
        assertTrue(registry.diagnostics().get(1).contains("find"));
        assertTrue(registry.diagnostics().get(2).contains("has"));
    }

    @Test
    void setAndRemovePropagateStoreFailures() {
        AttachmentRegistry registry = new AttachmentRegistry();
        registry.registerStore(new FailingStore(100));
        AttachmentType<String> capability = AttachmentType.runtime("test:propagate", String.class);
        Object holder = new Object();

        assertThrows(CapabilityException.class,
                () -> registry.set(holder, capability, "value"));
        assertThrows(CapabilityException.class,
                () -> registry.remove(holder, capability));
    }

    @Test
    void clearContinuesAfterStoreFailure() {
        AttachmentRegistry registry = new AttachmentRegistry();
        AttachmentType<String> capability = AttachmentType.runtime("test:clear", String.class);
        Object holder = new Object();
        registry.set(holder, capability, "value");

        registry.registerStore(new FailingStore(100));
        registry.clear(holder);

        assertTrue(registry.diagnostics().stream()
                .anyMatch(message -> message.contains("clear boom")));
        assertNull(registry.get(holder, capability));
    }

    @Test
    void diagnosticsAreCappedNewestLastAndObservable() {
        AttachmentRegistry registry = new AttachmentRegistry();
        registry.registerStore(new FailingStore(100));
        AttachmentType<String> capability = AttachmentType.runtime("test:diag", String.class);
        Object holder = new Object();
        List<String> observed = new ArrayList<>();
        registry.setDiagnostics(observed::add);

        for (int iteration = 0; iteration < 40; iteration++) {
            registry.get(holder, capability);
        }

        List<String> diagnostics = registry.diagnostics();

        assertEquals(32, diagnostics.size());
        assertTrue(diagnostics.get(0).contains("boom 9"));
        assertTrue(diagnostics.get(31).contains("boom 40"));
        assertEquals(40, observed.size());
        assertTrue(observed.get(39).contains("boom 40"));

        registry.clearDiagnostics();

        assertTrue(registry.diagnostics().isEmpty());

        registry.setDiagnostics(null);

        for (int iteration = 0; iteration < 5; iteration++) {
            registry.get(holder, capability);
        }

        assertEquals(5, registry.diagnostics().size());
        assertEquals(40, observed.size());
    }

    @Test
    void changeListenersObserveSetRemoveAndClear() {
        AttachmentRegistry registry = new AttachmentRegistry();
        AttachmentType<String> capability = AttachmentType.runtime("test:change", String.class);
        Object holder = new Object();
        List<AttachmentChange> changes = new ArrayList<>();
        AttachmentChangeListener listener = changes::add;

        registry.addChangeListener(listener);
        registry.addChangeListener(listener);
        assertEquals(1, registry.changeListenerCount());

        registry.set(holder, capability, "first");
        registry.set(holder, capability, "second");
        registry.remove(holder, capability);
        registry.clear(holder);

        assertEquals(4, changes.size());

        AttachmentChange first = changes.get(0);
        assertEquals(AttachmentChange.Kind.SET, first.kind());
        assertSame(holder, first.holder());
        assertSame(capability, first.capability());
        assertNull(first.previousValue());
        assertEquals("first", first.newValue());

        AttachmentChange second = changes.get(1);
        assertEquals(AttachmentChange.Kind.SET, second.kind());
        assertEquals("first", second.previousValue());
        assertEquals("second", second.newValue());

        AttachmentChange removed = changes.get(2);
        assertEquals(AttachmentChange.Kind.REMOVE, removed.kind());
        assertEquals("second", removed.previousValue());
        assertNull(removed.newValue());

        AttachmentChange cleared = changes.get(3);
        assertEquals(AttachmentChange.Kind.CLEAR, cleared.kind());
        assertSame(holder, cleared.holder());
        assertNull(cleared.capability());

        assertTrue(registry.removeChangeListener(listener));
        assertFalse(registry.removeChangeListener(listener));
        assertEquals(0, registry.changeListenerCount());

        registry.set(holder, capability, "after");
        assertEquals(4, changes.size());
    }

    @Test
    void failingChangeListenersAreIsolatedAndRecorded() {
        AttachmentRegistry registry = new AttachmentRegistry();
        AttachmentType<String> capability = AttachmentType.runtime("test:change_fail", String.class);
        Object holder = new Object();
        List<AttachmentChange> observed = new ArrayList<>();

        registry.addChangeListener(change -> {
            throw new IllegalStateException("listener boom");
        });
        registry.addChangeListener(observed::add);

        registry.set(holder, capability, "value");

        assertEquals(1, observed.size());
        assertTrue(registry.diagnostics().stream()
                .anyMatch(message -> message.contains("listener boom")));
    }

    @Test
    void globalRegistryIsSingletonAndSupportsRuntimeHolders() {
        assertSame(AttachmentRegistry.global(), AttachmentRegistry.global());

        AttachmentRegistry global = AttachmentRegistry.global();
        AttachmentType<String> capability = AttachmentType.runtime("test:global", String.class);
        Object holder = new Object();

        global.remove(holder, capability);
        assertNull(global.set(holder, capability, "global"));
        assertEquals("global", global.get(holder, capability));
        assertEquals("global", global.remove(holder, capability));
    }

    private static final class MapStore implements AttachmentStore {

        private final int priority;
        private final Map<Object, Map<Capability<?>, Object>> values = new IdentityHashMap<>();

        private MapStore(int priority) {
            this.priority = priority;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean supports(Object holder, Capability<?> capability) {
            return true;
        }

        @Override
        public <T> T get(Object holder, Capability<T> capability) {
            Map<Capability<?>, Object> held = values.get(holder);
            Object value = held == null ? null : held.get(capability);
            return value == null ? null : capability.valueType().cast(value);
        }

        @Override
        public <T> T set(Object holder, Capability<T> capability, T value) {
            Map<Capability<?>, Object> held =
                    values.computeIfAbsent(holder, key -> new HashMap<>());
            Object previous = held.put(capability, value);
            return previous == null ? null : capability.valueType().cast(previous);
        }

        @Override
        public <T> T remove(Object holder, Capability<T> capability) {
            Map<Capability<?>, Object> held = values.get(holder);
            if (held == null) {
                return null;
            }
            Object removed = held.remove(capability);
            if (held.isEmpty()) {
                values.remove(holder);
            }
            return removed == null ? null : capability.valueType().cast(removed);
        }

        @Override
        public boolean has(Object holder, Capability<?> capability) {
            Map<Capability<?>, Object> held = values.get(holder);
            return held != null && held.containsKey(capability);
        }

        @Override
        public void clear(Object holder) {
            values.remove(holder);
        }
    }

    private static final class FailingStore implements AttachmentStore {

        private final int priority;
        private int failures;

        private FailingStore(int priority) {
            this.priority = priority;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean supports(Object holder, Capability<?> capability) {
            return true;
        }

        @Override
        public <T> T get(Object holder, Capability<T> capability) {
            throw new CapabilityException("boom " + (++failures));
        }

        @Override
        public <T> T set(Object holder, Capability<T> capability, T value) {
            throw new CapabilityException("set boom");
        }

        @Override
        public <T> T remove(Object holder, Capability<T> capability) {
            throw new CapabilityException("remove boom");
        }

        @Override
        public boolean has(Object holder, Capability<?> capability) {
            throw new CapabilityException("has boom");
        }

        @Override
        public void clear(Object holder) {
            throw new CapabilityException("clear boom");
        }
    }
}
