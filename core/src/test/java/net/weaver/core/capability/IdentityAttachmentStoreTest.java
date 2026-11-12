package net.weaver.core.capability;

import java.util.Map;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.AttachmentType;
import net.weaver.api.capability.Capability;
import net.weaver.api.capability.CapabilityCodec;
import net.weaver.api.capability.CapabilitySync;
import net.weaver.api.data.DataCodecs;
import net.weaver.core.data.HolderDataLimits;
import net.weaver.core.data.ValueDataAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityAttachmentStoreTest
{
    private final AttachmentRegistry registry = new AttachmentRegistry();
    private final FakeValueDataAccess access = new FakeValueDataAccess();
    private final TestStore store = new TestStore(registry, access);

    @Test
    void supportsOnlyHoldersOfTheConfiguredKind()
    {
        Capability<String> capability = text("test:kind");

        assertTrue(store.supportsHolder(new TestHolder()));
        assertFalse(store.supportsHolder(new Object()));
        assertFalse(store.supportsHolder(null));
        assertTrue(store.supports(new TestHolder(), capability));
        assertFalse(store.supports(new TestHolder(), null));
    }

    @Test
    void valuesAreHeldByIdentityAndRemovedWithTheHolder()
    {
        Capability<String> capability = text("test:identity");
        TestHolder first = new TestHolder();
        TestHolder second = new TestHolder();

        assertNull(store.set(first, capability, "one"));
        assertEquals("one", store.get(first, capability));
        assertNull(store.get(second, capability));
        assertTrue(store.has(first, capability));
        assertEquals("one", store.remove(first, capability));
        assertFalse(store.has(first, capability));

        store.set(first, capability, "again");
        store.clear(first);
        assertNull(store.get(first, capability));
        assertDoesNotThrow(() -> store.clear(new TestHolder()));
    }

    @Test
    void hasDataTracksValuesAndRetainedBytes()
    {
        Capability<String> capability = text("test:has-data");
        TestHolder holder = new TestHolder();

        assertFalse(store.hasData(holder));
        store.set(holder, capability, "value");
        assertTrue(store.hasData(holder));
        store.remove(holder, capability);
        assertFalse(store.hasData(holder));
        assertFalse(store.hasData(new Object()));
    }

    @Test
    void saveAndLoadRoundTripThroughTheCarrier()
    {
        Capability<String> label = text("test:label");
        Capability<Integer> count = AttachmentType.<Integer>builder("test:count",
                        Integer.class)
                .codec(DataCodecs.varInt(), 8)
                .build();
        registry.register(label);
        registry.register(count);

        TestHolder original = new TestHolder();
        TestHolder loaded = new TestHolder();
        store.set(original, label, "saved");
        store.set(original, count, 42);
        FakeValueDataAccess.FakeCarrier carrier = new FakeValueDataAccess.FakeCarrier();

        store.saveTo(original, carrier);
        store.loadFrom(loaded, carrier);

        assertEquals("saved", store.get(loaded, label));
        assertEquals(Integer.valueOf(42), store.get(loaded, count));
        Map<String, byte[]> stored = access.stored(carrier, HolderDataLimits.ROOT_KEY);
        assertEquals(2, stored.size());
    }

    @Test
    void runtimeOnlyCapabilitiesAreNotSaved()
    {
        Capability<String> persistent = text("test:persistent");
        Capability<String> runtime = AttachmentType.runtime("test:runtime", String.class);
        registry.register(persistent);
        registry.register(runtime);

        TestHolder holder = new TestHolder();
        store.set(holder, persistent, "value");
        store.set(holder, runtime, "runtime");
        FakeValueDataAccess.FakeCarrier carrier = new FakeValueDataAccess.FakeCarrier();

        store.saveTo(holder, carrier);

        Map<String, byte[]> stored = access.stored(carrier, HolderDataLimits.ROOT_KEY);
        assertEquals(1, stored.size());
        assertTrue(stored.containsKey(persistent.id()));
        assertFalse(stored.containsKey(runtime.id()));
    }

    @Test
    void emptySaveRemovesTheWeaverRoot()
    {
        FakeValueDataAccess.FakeCarrier carrier = new FakeValueDataAccess.FakeCarrier();
        access.putRaw(carrier, HolderDataLimits.ROOT_KEY, "test:stale", new byte[]{1});

        store.saveTo(new TestHolder(), carrier);

        assertTrue(access.stored(carrier, HolderDataLimits.ROOT_KEY).isEmpty());
    }

    @Test
    void encodeFailuresAreSkippedWithoutBlockingOtherValues()
    {
        Capability<String> broken = AttachmentType.<String>builder("test:broken",
                        String.class)
                .codec(new CapabilityCodec<>()
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
                }, 16)
                .build();
        Capability<String> good = text("test:good");
        registry.register(broken);
        registry.register(good);

        TestHolder holder = new TestHolder();
        store.set(holder, broken, "broken");
        store.set(holder, good, "good");
        FakeValueDataAccess.FakeCarrier carrier = new FakeValueDataAccess.FakeCarrier();

        store.saveTo(holder, carrier);

        Map<String, byte[]> stored = access.stored(carrier, HolderDataLimits.ROOT_KEY);
        assertEquals(1, stored.size());
        assertTrue(stored.containsKey(good.id()));
    }

    @Test
    void oversizeEncodedValuesAreSkipped()
    {
        Capability<String> oversize = AttachmentType.<String>builder("test:oversize",
                        String.class)
                .codec(DataCodecs.utf8String(64), 2)
                .build();
        registry.register(oversize);

        TestHolder holder = new TestHolder();
        store.set(holder, oversize, "long");
        FakeValueDataAccess.FakeCarrier carrier = new FakeValueDataAccess.FakeCarrier();

        store.saveTo(holder, carrier);

        assertTrue(access.stored(carrier, HolderDataLimits.ROOT_KEY).isEmpty());
    }

    @Test
    void decodeFailuresAreSkippedWithoutBlockingOtherValues()
    {
        Capability<String> corrupt = text("test:corrupt");
        Capability<String> good = text("test:good_load");
        registry.register(corrupt);
        registry.register(good);

        TestHolder holder = new TestHolder();
        FakeValueDataAccess.FakeCarrier carrier = new FakeValueDataAccess.FakeCarrier();
        access.putRaw(carrier, HolderDataLimits.ROOT_KEY, corrupt.id(),
                new byte[]{(byte) 0xFF});
        access.putRaw(carrier, HolderDataLimits.ROOT_KEY, good.id(),
                good.codec().encode("restored"));

        store.loadFrom(holder, carrier);

        assertFalse(store.has(holder, corrupt));
        assertEquals("restored", store.get(holder, good));
    }

    @Test
    void unregisteredCapabilityDataSurvivesLoadAndSave()
    {
        Capability<String> label = text("test:kept_label");
        registry.register(label);
        byte[] foreign = {7, 8, 9};
        FakeValueDataAccess.FakeCarrier loadedFrom = new FakeValueDataAccess.FakeCarrier();
        access.putRaw(loadedFrom, HolderDataLimits.ROOT_KEY, "missingmod:mana", foreign);
        access.putRaw(loadedFrom, HolderDataLimits.ROOT_KEY, label.id(),
                label.codec().encode("hello"));
        TestHolder holder = new TestHolder();

        store.loadFrom(holder, loadedFrom);
        store.set(holder, label, "changed");
        FakeValueDataAccess.FakeCarrier savedTo = new FakeValueDataAccess.FakeCarrier();
        store.saveTo(holder, savedTo);

        Map<String, byte[]> stored = access.stored(savedTo, HolderDataLimits.ROOT_KEY);
        assertArrayEquals(foreign, stored.get("missingmod:mana"));
        assertArrayEquals(label.codec().encode("changed"), stored.get(label.id()));
        assertEquals(2, stored.size());
    }

    @Test
    void undecodableValuesAreWrittenBackUntilReplaced()
    {
        Capability<String> corrupt = text("test:undecodable");
        registry.register(corrupt);
        byte[] raw = {(byte) 0xFF};
        TestHolder holder = new TestHolder();
        FakeValueDataAccess.FakeCarrier carrier = new FakeValueDataAccess.FakeCarrier();
        access.putRaw(carrier, HolderDataLimits.ROOT_KEY, corrupt.id(), raw);
        access.disableEnumeration();

        store.loadFrom(holder, carrier);
        assertFalse(store.has(holder, corrupt));
        FakeValueDataAccess.FakeCarrier first = new FakeValueDataAccess.FakeCarrier();
        store.saveTo(holder, first);
        assertArrayEquals(raw, access.stored(first, HolderDataLimits.ROOT_KEY).get(corrupt.id()));

        store.set(holder, corrupt, "fresh");
        FakeValueDataAccess.FakeCarrier second = new FakeValueDataAccess.FakeCarrier();
        store.saveTo(holder, second);
        assertArrayEquals(corrupt.codec().encode("fresh"),
                access.stored(second, HolderDataLimits.ROOT_KEY).get(corrupt.id()));

        store.remove(holder, corrupt);
        FakeValueDataAccess.FakeCarrier third = new FakeValueDataAccess.FakeCarrier();
        store.saveTo(holder, third);
        assertTrue(access.stored(third, HolderDataLimits.ROOT_KEY).isEmpty());
    }

    @Test
    void removingAnUndecodableCapabilityDropsItsRetainedBytes()
    {
        Capability<String> corrupt = text("test:dropped");
        registry.register(corrupt);
        TestHolder holder = new TestHolder();
        FakeValueDataAccess.FakeCarrier carrier = new FakeValueDataAccess.FakeCarrier();
        access.putRaw(carrier, HolderDataLimits.ROOT_KEY, corrupt.id(), new byte[]{(byte) 0xFF});

        store.loadFrom(holder, carrier);
        assertNull(store.remove(holder, corrupt));
        FakeValueDataAccess.FakeCarrier saved = new FakeValueDataAccess.FakeCarrier();
        store.saveTo(holder, saved);

        assertTrue(access.stored(saved, HolderDataLimits.ROOT_KEY).isEmpty());
    }

    @Test
    void unsupportedCarriersAndCarrierFailuresAreIsolated()
    {
        Capability<String> capability = text("test:carrier");
        registry.register(capability);
        TestHolder holder = new TestHolder();
        store.set(holder, capability, "value");

        access.disableOutputs();
        assertDoesNotThrow(() -> store.saveTo(holder, new FakeValueDataAccess.FakeCarrier()));
        access.disableInputs();
        assertDoesNotThrow(() -> store.loadFrom(new TestHolder(),
                new FakeValueDataAccess.FakeCarrier()));

        FakeValueDataAccess failing = new FakeValueDataAccess();
        TestStore failingStore = new TestStore(registry, failing);
        failingStore.set(holder, capability, "value");
        failing.failWrites();
        assertDoesNotThrow(() -> failingStore.saveTo(holder,
                new FakeValueDataAccess.FakeCarrier()));
        failing.failReads();
        assertDoesNotThrow(() -> failingStore.loadFrom(new TestHolder(),
                new FakeValueDataAccess.FakeCarrier()));

        assertDoesNotThrow(() -> store.saveTo(holder, new Object()));
        assertDoesNotThrow(() -> store.loadFrom(holder, new Object()));
    }

    @Test
    void totalSizeLimitSkipsValuesBeyondTheBudget()
    {
        Capability<byte[]> first = bytes("test:bytes_first");
        Capability<byte[]> second = bytes("test:bytes_second");
        Capability<byte[]> third = bytes("test:bytes_third");
        registry.register(first);
        registry.register(second);
        registry.register(third);

        TestHolder holder = new TestHolder();
        byte[] payload = new byte[HolderDataLimits.MAX_VALUE_BYTES];
        store.set(holder, first, payload);
        store.set(holder, second, payload);
        store.set(holder, third, payload);
        FakeValueDataAccess.FakeCarrier carrier = new FakeValueDataAccess.FakeCarrier();

        store.saveTo(holder, carrier);

        Map<String, byte[]> stored = access.stored(carrier, HolderDataLimits.ROOT_KEY);
        assertEquals(2, stored.size());
        assertTrue(stored.containsKey(first.id()));
        assertTrue(stored.containsKey(second.id()));
        assertFalse(stored.containsKey(third.id()));
    }

    @Test
    void concreteStoresExposeTheirPrioritiesAndRejectForeignHolders()
    {
        assertEquals(50, new EntityAttachmentStore(registry, access).priority());
        assertEquals(50, new BlockEntityAttachmentStore(registry, access).priority());
        assertEquals(40, new LevelAttachmentStore(registry, access).priority());
        assertEquals(40, new ChunkAttachmentStore(registry, access).priority());
        assertEquals(100, new ItemStackAttachmentStore().priority());
        assertFalse(new EntityAttachmentStore(registry, access)
                .supportsHolder(new Object()));
        assertFalse(new BlockEntityAttachmentStore(registry, access)
                .supportsHolder(new Object()));
        assertFalse(new LevelAttachmentStore(registry, access)
                .supportsHolder(new Object()));
        assertFalse(new ChunkAttachmentStore(registry, access)
                .supportsHolder(new Object()));
    }

    @Test
    void installedStoresIncludeEveryRuntimeHolder()
    {
        AttachmentRegistry installedRegistry = new AttachmentRegistry();
        CapabilityStores.Installed installed = CapabilityStores.installed(installedRegistry);

        assertTrue(installed != null);
        assertEquals(50, installed.entity().priority());
        assertEquals(50, installed.blockEntity().priority());
        assertEquals(40, installed.level().priority());
        assertEquals(40, installed.chunk().priority());
        assertEquals(100, installed.itemStack().priority());
    }

    private static Capability<String> text(String id)
    {
        return AttachmentType.of(id, String.class, DataCodecs.utf8String(64));
    }

    private static Capability<byte[]> bytes(String id)
    {
        return AttachmentType.builder(id, byte[].class)
                .codec(DataCodecs.bytes(HolderDataLimits.MAX_VALUE_BYTES),
                        HolderDataLimits.MAX_VALUE_BYTES)
                .sync(CapabilitySync.NONE)
                .build();
    }

    private static final class TestStore extends IdentityAttachmentStore
    {
        private TestStore(AttachmentRegistry registry, ValueDataAccess access)
        {
            super(registry, TestHolder.class.getName(), access);
        }

        @Override
        public int priority()
        {
            return 10;
        }
    }

    private static final class TestHolder
    {
    }
}
