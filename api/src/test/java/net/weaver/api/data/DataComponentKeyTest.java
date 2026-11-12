package net.weaver.api.data;

import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.CapabilityCodec;
import net.weaver.api.capability.CapabilityException;
import net.weaver.api.capability.CapabilityId;
import net.weaver.api.capability.CapabilitySync;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataComponentKeyTest {

    @Test
    void capabilityMethodsDelegateToTheAttachmentType() {
        CapabilityCodec<String> codec = DataCodecs.utf8String(32);
        DataComponentKey<String> key =
                DataComponentKey.of("test:data_key", String.class, codec, 32);

        assertEquals("test:data_key", key.id());
        assertEquals("test", key.namespace());
        assertEquals("data_key", key.path());
        assertEquals(CapabilityId.parse("test:data_key"), key.key());
        assertEquals(String.class, key.valueType());
        assertEquals(codec, key.codec());
        assertEquals(32, key.maxEncodedBytes());
        assertTrue(key.persistent());
        assertTrue(key.copyOnItemCopy());
        assertEquals(CapabilitySync.ITEM_STACK, key.sync());
        assertNull(key.defaultValue());
        assertSame(key.attachmentType(), key.attachmentType());
        assertEquals(key.key(), key.attachmentType().key());
        assertEquals(key.valueType(), key.attachmentType().valueType());
        assertTrue(key.toString().contains("test:data_key"));
    }

    @Test
    void builderRequiresCodecAndAppliesDefaults() {
        AtomicInteger calls = new AtomicInteger();
        DataComponentKey<String> key = DataComponentKey.builder("test:builder", String.class)
                .codec(DataCodecs.utf8String(16), 16)
                .defaultSupplier(() -> "value" + calls.incrementAndGet())
                .copyOnItemCopy(false)
                .sync(CapabilitySync.NONE)
                .build();

        assertEquals("value1", key.defaultValue());
        assertEquals("value2", key.defaultValue());
        assertFalse(key.copyOnItemCopy());
        assertEquals(CapabilitySync.NONE, key.sync());
        assertEquals(16, key.maxEncodedBytes());
        assertTrue(key.persistent());

        DataComponentKey<String> constant = DataComponentKey.builder("test:constant", String.class)
                .codec(DataCodecs.utf8String(8), 8)
                .defaultValue("fallback")
                .build();

        assertEquals("fallback", constant.defaultValue());
        assertTrue(constant.copyOnItemCopy());
        assertEquals(CapabilitySync.ITEM_STACK, constant.sync());

        assertThrows(CapabilityException.class,
                () -> DataComponentKey.builder("test:no_codec", String.class).build());
        assertThrows(CapabilityException.class,
                () -> DataComponentKey.of("invalid", String.class, DataCodecs.utf8String(4), 4));
        assertThrows(NullPointerException.class,
                () -> DataComponentKey.of("test:null_codec", String.class, null, 4));
    }

    @Test
    void viewReadsAndWritesThroughTheGlobalRegistry() {
        DataComponentKey<String> key = DataComponentKey.of(
                "test:view_" + System.nanoTime(), String.class, DataCodecs.utf8String(32), 32);
        Object holder = new Object();
        DataComponentView view = DataComponentView.of(holder);

        assertNull(view.get(key));
        assertTrue(view.find(key).isEmpty());
        assertFalse(view.has(key));
        assertNull(view.remove(key));

        assertNull(view.set(key, "first"));
        assertEquals("first", view.get(key));
        assertEquals(Optional.of("first"), view.find(key));
        assertTrue(view.has(key));
        assertEquals("first", AttachmentRegistry.global().get(holder, key));

        assertEquals("first", view.set(key, "second"));
        assertEquals("second", view.get(key));
        assertEquals("second", view.remove(key));
        assertFalse(view.has(key));
        assertTrue(view.find(key).isEmpty());

        DataComponentKey<String> withDefault = DataComponentKey.builder(
                        "test:view_default_" + System.nanoTime(), String.class)
                .codec(DataCodecs.utf8String(8), 8)
                .defaultValue("fallback")
                .build();

        assertEquals("fallback", view.get(withDefault));
        assertTrue(view.find(withDefault).isEmpty());
        assertFalse(view.has(withDefault));

        Object equalOne = new String("holder");
        Object equalTwo = new String("holder");
        DataComponentView firstView = DataComponentView.of(equalOne);
        DataComponentView secondView = DataComponentView.of(equalTwo);

        assertEquals(equalOne, equalTwo);
        firstView.set(key, "one");
        assertNull(secondView.get(key));
        assertEquals("one", firstView.get(key));

        assertThrows(NullPointerException.class, () -> DataComponentView.of(null));
    }
}
