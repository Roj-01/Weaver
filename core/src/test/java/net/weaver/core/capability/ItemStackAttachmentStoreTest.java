package net.weaver.core.capability;

import java.util.List;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.AttachmentType;
import net.weaver.api.capability.Capability;
import net.weaver.api.capability.CapabilityCodec;
import net.weaver.api.capability.CapabilityException;
import net.weaver.api.data.DataCodecs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemStackAttachmentStoreTest
{
    private final FakeItemDataAccess access = new FakeItemDataAccess();
    private final ItemStackAttachmentStore store = new ItemStackAttachmentStore(access);
    private final FakeItemDataAccess.FakeItemHolder holder =
            new FakeItemDataAccess.FakeItemHolder();

    private static Capability<String> text(String id)
    {
        return AttachmentType.of(id, String.class, DataCodecs.utf8String(64));
    }

    @Test
    void priorityAndAccessAreExposed()
    {
        assertEquals(100, store.priority());
        assertSame(access, store.access());
    }

    @Test
    void supportsOnlyNonEmptyItemHoldersWithCodecs()
    {
        Capability<String> persistent = text("weaver:test_supports");
        Capability<String> runtimeOnly = AttachmentType.runtime(
                "weaver:test_supports_runtime", String.class);

        assertTrue(store.supports(holder, persistent));
        assertFalse(store.supports(null, persistent));
        assertFalse(store.supports(holder, null));
        assertFalse(store.supports(new Object(), persistent));
        assertFalse(store.supports(FakeItemDataAccess.FakeItemHolder.empty(), persistent));
        assertFalse(store.supports(holder, runtimeOnly));
    }

    @Test
    void setGetRemoveHasAndPreviousValues()
    {
        Capability<String> capability = text("weaver:test_set_get");

        assertFalse(store.has(holder, capability));
        assertNull(store.get(holder, capability));
        assertNull(store.set(holder, capability, "first"));
        assertEquals("first", store.get(holder, capability));
        assertTrue(store.has(holder, capability));

        assertEquals("first", store.set(holder, capability, "second"));
        assertEquals("second", store.get(holder, capability));

        byte[] raw = access.read(holder, capability.id());
        assertTrue(raw.length > 0);
        raw[0] = 0;
        assertEquals("second", store.get(holder, capability));

        assertEquals("second", store.remove(holder, capability));
        assertFalse(store.has(holder, capability));
        assertNull(store.remove(holder, capability));
    }

    @Test
    void registryFallsBackToTheCapabilityDefault()
    {
        Capability<String> capability = AttachmentType.<String>builder(
                        "weaver:test_default", String.class)
                .defaultValue("fallback")
                .codec(DataCodecs.utf8String(32), 32)
                .build();
        AttachmentRegistry registry = new AttachmentRegistry();
        registry.register(capability);
        registry.registerStore(store);

        assertEquals("fallback", registry.get(holder, capability));
        assertTrue(registry.find(holder, capability).isEmpty());

        registry.set(holder, capability, "explicit");
        assertEquals("explicit", registry.get(holder, capability));
        assertEquals("explicit", registry.find(holder, capability).orElseThrow());
    }

    @Test
    void copiedHolderKeepsIndependentValues()
    {
        Capability<String> capability = text("weaver:test_copy");
        FakeItemDataAccess.FakeItemHolder copy =
                new FakeItemDataAccess.FakeItemHolder();
        store.set(holder, capability, "original");

        access.copy(holder, copy);

        assertEquals("original", store.get(copy, capability));
        store.set(copy, capability, "changed");
        assertEquals("original", store.get(holder, capability));
        assertEquals("changed", store.get(copy, capability));

        store.remove(copy, capability);
        assertEquals("original", store.get(holder, capability));
        assertFalse(store.has(copy, capability));
    }

    @Test
    void corruptPayloadFailsGetAndRegistryFallsBackToDefault()
    {
        Capability<Integer> capability = AttachmentType.<Integer>builder(
                        "weaver:test_corrupt", Integer.class)
                .defaultValue(7)
                .codec(DataCodecs.varInt(), 16)
                .build();
        access.putRaw(holder, capability.id(), new byte[]{(byte) 0x80});

        CapabilityException failure = assertThrows(CapabilityException.class,
                () -> store.get(holder, capability));
        assertTrue(failure.getMessage().contains(capability.id()));

        AttachmentRegistry registry = new AttachmentRegistry();
        registry.register(capability);
        registry.registerStore(store);

        assertEquals(Integer.valueOf(7), registry.get(holder, capability));
        assertTrue(registry.find(holder, capability).isEmpty());
        List<String> diagnostics = registry.diagnostics();
        assertFalse(diagnostics.isEmpty());
        assertTrue(diagnostics.get(diagnostics.size() - 1)
                .contains(capability.id()));
    }

    @Test
    void oversizeEncodedValueIsRejectedBeforeWriting()
    {
        Capability<String> capability = AttachmentType.<String>builder(
                        "weaver:test_oversize", String.class)
                .codec(DataCodecs.utf8String(1024), 4)
                .build();
        store.set(holder, capability, "ok");

        CapabilityException failure = assertThrows(CapabilityException.class,
                () -> store.set(holder, capability, "hello"));
        assertTrue(failure.getMessage().contains(capability.id()));
        assertEquals("ok", store.get(holder, capability));
    }

    @Test
    void emptyEncodedValueIsRejected()
    {
        CapabilityCodec<String> emptyCodec = new CapabilityCodec<>()
        {
            @Override
            public byte[] encode(String value)
            {
                return new byte[0];
            }

            @Override
            public String decode(byte[] encoded)
            {
                return "";
            }
        };
        Capability<String> capability = AttachmentType.<String>builder(
                        "weaver:test_empty_payload", String.class)
                .codec(emptyCodec, 8)
                .build();

        assertThrows(CapabilityException.class,
                () -> store.set(holder, capability, "value"));
        assertFalse(access.contains(holder, capability.id()));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void wrongDecodedTypeIsRejected()
    {
        CapabilityCodec<Integer> lyingCodec =
                (CapabilityCodec) new CapabilityCodec<String>()
                {
                    @Override
                    public byte[] encode(String value)
                    {
                        return new byte[]{1};
                    }

                    @Override
                    public String decode(byte[] encoded)
                    {
                        return "not an integer";
                    }
                };
        Capability<Integer> capability = AttachmentType.<Integer>builder(
                        "weaver:test_wrong_type", Integer.class)
                .defaultValue(11)
                .codec(lyingCodec, 4)
                .build();
        access.putRaw(holder, capability.id(), new byte[]{1});

        CapabilityException failure = assertThrows(CapabilityException.class,
                () -> store.get(holder, capability));
        assertTrue(failure.getMessage().contains(capability.id()));

        AttachmentRegistry registry = new AttachmentRegistry();
        registry.register(capability);
        registry.registerStore(store);

        assertEquals(Integer.valueOf(11), registry.get(holder, capability));
        assertFalse(registry.diagnostics().isEmpty());
    }

    @Test
    void corruptPreviousValueDoesNotBlockSetOrRemove()
    {
        Capability<Integer> capability = AttachmentType.<Integer>builder(
                        "weaver:test_previous_corrupt", Integer.class)
                .codec(DataCodecs.varInt(), 8)
                .build();

        access.putRaw(holder, capability.id(), new byte[]{(byte) 0x80});
        assertNull(store.set(holder, capability, 42));
        assertEquals(Integer.valueOf(42), store.get(holder, capability));

        access.putRaw(holder, capability.id(), new byte[]{(byte) 0x80});
        assertNull(store.remove(holder, capability));
        assertFalse(store.has(holder, capability));
    }

    @Test
    void encodeFailureIsWrappedWithTheCapabilityId()
    {
        CapabilityCodec<String> brokenCodec = new CapabilityCodec<>()
        {
            @Override
            public byte[] encode(String value)
            {
                throw new IllegalStateException("encode boom");
            }

            @Override
            public String decode(byte[] encoded)
            {
                return "";
            }
        };
        Capability<String> capability = AttachmentType.<String>builder(
                        "weaver:test_encode_failure", String.class)
                .codec(brokenCodec, 8)
                .build();

        CapabilityException failure = assertThrows(CapabilityException.class,
                () -> store.set(holder, capability, "value"));
        assertTrue(failure.getMessage().contains(capability.id()));
        assertTrue(failure.getCause() instanceof IllegalStateException);
    }

    @Test
    void emptyStackFallsThroughToTheRuntimeStore()
    {
        Capability<String> capability = text("weaver:test_empty_stack");
        FakeItemDataAccess.FakeItemHolder empty =
                FakeItemDataAccess.FakeItemHolder.empty();
        AttachmentRegistry registry = new AttachmentRegistry();
        registry.register(capability);
        registry.registerStore(store);

        assertFalse(store.supports(empty, capability));
        registry.set(empty, capability, "runtime");
        assertEquals("runtime", registry.get(empty, capability));
        assertEquals(0, access.entryCount(empty));
    }

    @Test
    void runtimeOnlyCapabilityFallsThroughToTheRuntimeStore()
    {
        Capability<String> capability = AttachmentType.runtime(
                "weaver:test_runtime_only", String.class);
        AttachmentRegistry registry = new AttachmentRegistry();
        registry.register(capability);
        registry.registerStore(store);

        assertFalse(store.supports(holder, capability));
        registry.set(holder, capability, "runtime");
        assertEquals("runtime", registry.get(holder, capability));
        assertEquals(0, access.entryCount(holder));
    }

    @Test
    void twoCapabilityIdsStayIsolated()
    {
        Capability<String> first = text("weaver:test_first");
        Capability<String> second = text("weaver:test_second");

        store.set(holder, first, "one");
        store.set(holder, second, "two");

        assertEquals("one", store.get(holder, first));
        assertEquals("two", store.get(holder, second));

        assertEquals("one", store.remove(holder, first));
        assertFalse(store.has(holder, first));
        assertTrue(store.has(holder, second));
        assertEquals("two", store.get(holder, second));
    }

    @Test
    void clearRemovesValuesAndIgnoresUnsupportedHolders()
    {
        Capability<String> first = text("weaver:test_clear_first");
        Capability<String> second = text("weaver:test_clear_second");
        store.set(holder, first, "one");
        store.set(holder, second, "two");

        store.clear(holder);

        assertEquals(0, access.entryCount(holder));
        assertFalse(store.has(holder, first));
        assertFalse(store.has(holder, second));
        assertDoesNotThrow(() -> store.clear(new Object()));
        assertDoesNotThrow(() -> store.clear(null));
    }

    @Test
    void setRejectsNullValuesAndCapabilitiesWithoutCodecs()
    {
        Capability<String> capability = text("weaver:test_set_validation");
        Capability<String> runtimeOnly = AttachmentType.runtime(
                "weaver:test_set_runtime", String.class);

        assertThrows(NullPointerException.class,
                () -> store.set(holder, capability, null));
        CapabilityException failure = assertThrows(CapabilityException.class,
                () -> store.set(holder, runtimeOnly, "value"));
        assertTrue(failure.getMessage().contains(runtimeOnly.id()));
    }
}
