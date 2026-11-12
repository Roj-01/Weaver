package net.weaver.core.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.AttachmentType;
import net.weaver.api.capability.Capability;
import net.weaver.api.capability.CapabilitySync;
import net.weaver.api.capability.CapabilitySyncProvider;
import net.weaver.api.data.DataCodecs;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilitySyncChannelTest
{
    private final Object serverHolder = new Object();
    private final Object clientHolder = new Object();
    private final CapabilitySyncProvider provider = new TestProvider();

    @AfterEach
    void tearDown()
    {
        CapabilitySyncChannel.uninstall();
        CapabilitySyncChannel.setRecipientsSupplier(null);
    }

    @Test
    void channelIdentityIsStable()
    {
        assertEquals("weaver:capability/sync/v1", CapabilitySyncChannel.TYPE.channel());
        assertEquals("weaver", CapabilitySyncChannel.TYPE.modId());
        assertEquals("capability/sync", CapabilitySyncChannel.TYPE.path());
        assertEquals(1, CapabilitySyncChannel.TYPE.version());
    }

    @Test
    void changesAreSentToTheRecipientSupplier()
    {
        AttachmentRegistry registry = new AttachmentRegistry();
        Capability<String> capability = text("test:sync_value", CapabilitySync.RUNTIME);
        registry.register(capability);
        FakeConnection connection = new FakeConnection();

        CapabilitySyncChannel.install(registry, provider);
        CapabilitySyncChannel.setRecipientsSupplier(() -> List.of(connection));

        registry.set(serverHolder, capability, "value");

        assertEquals(1, connection.payloads.size());
        CapabilitySyncPayload set = (CapabilitySyncPayload) connection.payloads.get(0);
        assertEquals("test_holder", set.holderKind());
        assertEquals("test_key", set.holderKey());
        assertEquals(capability.id(), set.capabilityId());
        assertEquals("value", capability.codec().decode(set.encoded()));

        registry.remove(serverHolder, capability);

        assertEquals(2, connection.payloads.size());
        CapabilitySyncPayload removed =
                (CapabilitySyncPayload) connection.payloads.get(1);
        assertTrue(removed.removes());

        registry.set(serverHolder, capability, "again");
        registry.clear(serverHolder);

        assertEquals(3, connection.payloads.size());
    }

    @Test
    void sendRequiresAnInstalledProviderAndSyncEnabledCapability()
    {
        AttachmentRegistry registry = new AttachmentRegistry();
        Capability<String> none = text("test:sync_none", CapabilitySync.NONE);
        Capability<String> item = text("test:sync_item", CapabilitySync.ITEM_STACK);
        Capability<String> runtime = text("test:sync_runtime", CapabilitySync.RUNTIME);
        registry.register(none);
        registry.register(item);
        registry.register(runtime);
        FakeConnection connection = new FakeConnection();

        assertFalse(CapabilitySyncChannel.send(connection, serverHolder, runtime, "value"));

        CapabilitySyncChannel.install(registry, provider);

        assertFalse(CapabilitySyncChannel.send(connection, serverHolder, none, "value"));
        assertFalse(CapabilitySyncChannel.send(null, serverHolder, runtime, "value"));
        assertFalse(CapabilitySyncChannel.send(connection, new Object(), runtime, "value"));
        assertTrue(CapabilitySyncChannel.send(connection, serverHolder, runtime, "value"));
        assertTrue(CapabilitySyncChannel.send(connection, serverHolder, item, "value"));
        assertTrue(CapabilitySyncChannel.send(connection, serverHolder, runtime, null));
        assertEquals(3, connection.payloads.size());
    }

    @Test
    void inboundPayloadsApplyKnownValuesAndRemoveOnEmptyBytes()
    {
        AttachmentRegistry registry = new AttachmentRegistry();
        Capability<String> capability = text("test:sync_in", CapabilitySync.RUNTIME);
        registry.register(capability);
        CapabilitySyncChannel.install(registry, provider);

        CapabilitySyncPayload set = new CapabilitySyncPayload("test_holder", "test_key",
                capability.id(), capability.codec().encode("incoming"));
        CapabilitySyncChannel.handle(set, null);

        assertEquals("incoming", registry.get(clientHolder, capability));

        CapabilitySyncPayload remove = new CapabilitySyncPayload("test_holder",
                "test_key", capability.id(), new byte[0]);
        CapabilitySyncChannel.handle(remove, null);

        assertFalse(registry.has(clientHolder, capability));
        assertNull(registry.get(clientHolder, capability));
    }

    @Test
    void inboundPayloadsIgnoreUnknownCapabilitiesAndInvalidValues()
    {
        AttachmentRegistry registry = new AttachmentRegistry();
        Capability<String> capability = text("test:sync_known", CapabilitySync.RUNTIME);
        registry.register(capability);
        CapabilitySyncChannel.install(registry, provider);

        CapabilitySyncPayload unknown = new CapabilitySyncPayload("test_holder",
                "test_key", "test:missing", new byte[]{1});
        CapabilitySyncChannel.handle(unknown, null);

        assertFalse(registry.has(clientHolder, capability));

        CapabilitySyncPayload corrupt = new CapabilitySyncPayload("test_holder",
                "test_key", capability.id(), new byte[]{(byte) 0xFF});
        CapabilitySyncChannel.handle(corrupt, null);

        assertFalse(registry.has(clientHolder, capability));

        CapabilitySyncPayload missingHolder = new CapabilitySyncPayload("other",
                "other", capability.id(), capability.codec().encode("value"));
        CapabilitySyncChannel.handle(missingHolder, null);

        assertFalse(registry.has(clientHolder, capability));
    }

    @Test
    void payloadsValidateTheirTextAndBounds()
    {
        byte[] encoded = new byte[]{1, 2};
        CapabilitySyncPayload payload = new CapabilitySyncPayload("kind", "key",
                "test:payload", encoded);
        encoded[0] = 9;

        assertEquals(1, payload.encoded()[0]);
        assertFalse(payload.removes());
        assertThrows(IllegalArgumentException.class, () -> new CapabilitySyncPayload(
                "x".repeat(CapabilitySyncPayload.MAX_KIND_CHARS + 1), "key",
                "test:payload", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new CapabilitySyncPayload(
                "kind", "x".repeat(CapabilitySyncPayload.MAX_KEY_CHARS + 1),
                "test:payload", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new CapabilitySyncPayload(
                "kind", "key", "not-a-valid-id", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new CapabilitySyncPayload(
                "kind", "key", "test:payload",
                new byte[net.weaver.api.network.NetworkProtocol.MAX_PAYLOAD_BYTES + 1]));
    }

    @Test
    void payloadCodecRoundTripsAndRejectsTrailingBytes()
    {
        CapabilitySyncPayload payload = new CapabilitySyncPayload("kind", "key",
                "test:codec", new byte[]{4, 5, 6});
        byte[] encoded = CapabilitySyncChannel.TYPE.codec().encode(payload);
        CapabilitySyncPayload decoded = CapabilitySyncChannel.TYPE.codec().decode(encoded);

        assertEquals(payload.holderKind(), decoded.holderKind());
        assertEquals(payload.holderKey(), decoded.holderKey());
        assertEquals(payload.capabilityId(), decoded.capabilityId());
        assertEquals(3, decoded.encoded().length);
        assertThrows(RuntimeException.class,
                () -> CapabilitySyncChannel.TYPE.codec().decode(
                        java.util.Arrays.copyOf(encoded, encoded.length + 1)));
    }

    private static Capability<String> text(String id, CapabilitySync sync)
    {
        return AttachmentType.<String>builder(id, String.class)
                .codec(DataCodecs.utf8String(64), 64)
                .sync(sync)
                .build();
    }

    private final class TestProvider implements CapabilitySyncProvider
    {
        @Override
        public String holderKind(Object holder)
        {
            return holder == serverHolder || holder == clientHolder
                    ? "test_holder" : null;
        }

        @Override
        public String holderKey(Object holder)
        {
            return holder == serverHolder || holder == clientHolder
                    ? "test_key" : null;
        }

        @Override
        public Object resolve(String holderKind, String holderKey)
        {
            return "test_holder".equals(holderKind) && "test_key".equals(holderKey)
                    ? clientHolder : null;
        }
    }

    private static final class FakeConnection implements NetworkConnection
    {
        private final List<PayloadType<?>> types = new ArrayList<>();
        private final List<Object> payloads = new ArrayList<>();
        private boolean open = true;

        @Override
        public NetworkSide side()
        {
            return NetworkSide.SERVER;
        }

        @Override
        public NetworkPhase phase()
        {
            return NetworkPhase.PLAY;
        }

        @Override
        public NetworkPlayer player()
        {
            return NetworkPlayer.of(UUID.fromString(
                    "00000000-0000-0000-0000-000000000001"), "Tester");
        }

        @Override
        public boolean isOpen()
        {
            return open;
        }

        @Override
        public <T> void send(PayloadType<T> type, T payload)
        {
            types.add(type);
            payloads.add(payload);
        }

        @Override
        public void disconnect(String reason)
        {
            open = false;
        }

        @Override
        public void close()
        {
            open = false;
        }
    }
}
