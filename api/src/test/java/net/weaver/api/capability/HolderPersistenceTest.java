package net.weaver.api.capability;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link AttachmentRegistry#saveHolder}, {@link AttachmentRegistry#loadHolder} and the copy-on-death flag. */
class HolderPersistenceTest {

    @Test
    void copyOnDeathDefaultsToFalseAndCanBeSet() {
        AttachmentType<String> plain = AttachmentType.runtime("test:plain", String.class);
        AttachmentType<String> kept = AttachmentType.builder("test:kept", String.class)
                .copyOnDeath(true).build();
        Capability<String> custom = new Capability<>() {
            @Override
            public CapabilityId key() {
                return CapabilityId.parse("test:custom");
            }

            @Override
            public Class<String> valueType() {
                return String.class;
            }

            @Override
            public String defaultValue() {
                return null;
            }

            @Override
            public CapabilityCodec<String> codec() {
                return null;
            }

            @Override
            public int maxEncodedBytes() {
                return 0;
            }

            @Override
            public boolean copyOnItemCopy() {
                return false;
            }

            @Override
            public CapabilitySync sync() {
                return CapabilitySync.NONE;
            }
        };

        assertFalse(plain.copyOnDeath());
        assertTrue(kept.copyOnDeath());
        assertFalse(custom.copyOnDeath());
    }

    @Test
    void saveAndLoadGoToTheFirstPersistentStoreThatSupportsTheHolder() {
        AttachmentRegistry registry = new AttachmentRegistry();
        RecordingStore strings = new RecordingStore(String.class, 20);
        RecordingStore numbers = new RecordingStore(Integer.class, 10);
        registry.registerStore(numbers);
        registry.registerStore(strings);

        Object output = new Object();
        Object input = new Object();
        assertTrue(registry.saveHolder("holder", output));
        assertTrue(registry.loadHolder(7, input));
        assertFalse(registry.saveHolder(new Object(), output));

        assertEquals(List.of("save:holder"), strings.calls);
        assertEquals(List.of("load:7"), numbers.calls);
        assertThrows(NullPointerException.class, () -> registry.saveHolder(null, output));
        assertThrows(NullPointerException.class, () -> registry.loadHolder("holder", null));
    }

    @Test
    void hasPersistentDataAsksTheStoreAndIsFalseWithoutOne() {
        AttachmentRegistry registry = new AttachmentRegistry();
        assertFalse(registry.hasPersistentData("holder"));
        RecordingStore strings = new RecordingStore(String.class, 20);
        registry.registerStore(strings);
        assertTrue(registry.hasPersistentData("holder"));
        assertFalse(registry.hasPersistentData(7));
    }

    @Test
    void storeFailuresBecomeDiagnostics() {
        AttachmentRegistry registry = new AttachmentRegistry();
        RecordingStore failing = new RecordingStore(String.class, 20);
        failing.fail = true;
        registry.clearDiagnostics();
        registry.registerStore(failing);

        assertTrue(registry.saveHolder("holder", new Object()));
        assertTrue(registry.loadHolder("holder", new Object()));
        assertEquals(2, registry.diagnostics().size());
    }

    private static final class RecordingStore implements PersistentAttachmentStore {
        private final Class<?> type;
        private final int priority;
        private final List<String> calls = new ArrayList<>();
        private boolean fail;

        RecordingStore(Class<?> type, int priority) {
            this.type = type;
            this.priority = priority;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean supportsHolder(Object holder) {
            return type.isInstance(holder);
        }

        @Override
        public void saveTo(Object holder, Object output) {
            if (fail) {
                throw new IllegalStateException("save boom");
            }
            calls.add("save:" + holder);
        }

        @Override
        public void loadFrom(Object holder, Object input) {
            if (fail) {
                throw new IllegalStateException("load boom");
            }
            calls.add("load:" + holder);
        }

        @Override
        public boolean supports(Object holder, Capability<?> capability) {
            return supportsHolder(holder);
        }

        @Override
        public <T> T get(Object holder, Capability<T> capability) {
            return null;
        }

        @Override
        public <T> T set(Object holder, Capability<T> capability, T value) {
            return null;
        }

        @Override
        public <T> T remove(Object holder, Capability<T> capability) {
            return null;
        }

        @Override
        public boolean has(Object holder, Capability<?> capability) {
            return false;
        }

        @Override
        public void clear(Object holder) {
        }
    }
}
