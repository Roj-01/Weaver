package net.weaver.api.config;

import net.weaver.api.event.Side;
import net.weaver.api.network.NetworkException;
import net.weaver.api.network.PayloadBuffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSyncDocumentTest
{
    private static final String MOD_ID = "config.sync.test";

    @Test
    void codecRoundTripsJsonCompatibleValuesAndPreservesOrder()
    {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("a.boolean", true);
        values.put("a.integer", 42);
        values.put("a.long", 9_000_000_000L);
        values.put("a.decimal", 2.5);
        values.put("a.string", "hello \u00e9\u4e16\u754c");
        values.put("a.list", List.of("x", 3, 4.5, false));
        values.put("a.nested", Map.of("inner", List.of(1, 2)));
        values.put("a.null", null);
        ConfigSyncDocument document = new ConfigSyncDocument(MOD_ID, Side.SERVER, 4, values);

        byte[] encoded = ConfigSyncDocument.CODEC.encode(document);
        ConfigSyncDocument decoded = ConfigSyncDocument.CODEC.decode(encoded);

        assertEquals(MOD_ID, decoded.modId());
        assertEquals(Side.SERVER, decoded.side());
        assertEquals(4, decoded.schemaVersion());
        assertEquals(List.copyOf(values.keySet()), List.copyOf(decoded.values().keySet()));
        assertEquals(true, decoded.values().get("a.boolean"));
        assertEquals(42L, decoded.values().get("a.integer"));
        assertEquals(9_000_000_000L, decoded.values().get("a.long"));
        assertEquals(2.5, decoded.values().get("a.decimal"));
        assertEquals("hello \u00e9\u4e16\u754c", decoded.values().get("a.string"));
        assertEquals(List.of("x", 3L, 4.5, false), decoded.values().get("a.list"));
        assertEquals(Map.of("inner", List.of(1L, 2L)), decoded.values().get("a.nested"));
        assertNull(decoded.values().get("a.null"));
        assertEquals(4, decoded.document().schemaVersion());
    }

    @Test
    void codecRejectsTrailingBytesAndMalformedFrames()
    {
        ConfigSyncDocument document = new ConfigSyncDocument(MOD_ID, Side.SERVER, 1,
                Map.of("a", 1));
        byte[] encoded = ConfigSyncDocument.CODEC.encode(document);
        byte[] trailing = new byte[encoded.length + 1];
        System.arraycopy(encoded, 0, trailing, 0, encoded.length);
        assertThrows(NetworkException.class,
                () -> ConfigSyncDocument.CODEC.decode(trailing));

        PayloadBuffer badVersion = PayloadBuffer.writer();
        badVersion.writeByte(999);
        badVersion.writeString(MOD_ID);
        badVersion.writeByte(Side.SERVER.ordinal());
        badVersion.writeVarInt(1);
        badVersion.writeVarInt(0);
        assertThrows(NetworkException.class,
                () -> ConfigSyncDocument.CODEC.decode(badVersion.toByteArray()));

        PayloadBuffer badSide = PayloadBuffer.writer();
        badSide.writeByte(ConfigSyncDocument.FORMAT_VERSION);
        badSide.writeString(MOD_ID);
        badSide.writeByte(99);
        badSide.writeVarInt(1);
        badSide.writeVarInt(0);
        assertThrows(NetworkException.class,
                () -> ConfigSyncDocument.CODEC.decode(badSide.toByteArray()));

        PayloadBuffer badSchema = PayloadBuffer.writer();
        badSchema.writeByte(ConfigSyncDocument.FORMAT_VERSION);
        badSchema.writeString(MOD_ID);
        badSchema.writeByte(Side.SERVER.ordinal());
        badSchema.writeVarInt(0);
        badSchema.writeVarInt(0);
        assertThrows(NetworkException.class,
                () -> ConfigSyncDocument.CODEC.decode(badSchema.toByteArray()));
    }

    @Test
    void codecRejectsExcessiveCountsDuplicatesInvalidUtf8AndDeepNesting()
    {
        PayloadBuffer tooMany = header(ConfigSyncDocument.MAX_KEYS + 1);
        assertThrows(NetworkException.class,
                () -> ConfigSyncDocument.CODEC.decode(tooMany.toByteArray()));

        PayloadBuffer duplicate = header(2);
        duplicate.writeString("same-key");
        duplicate.writeByte(0);
        duplicate.writeString("same-key");
        duplicate.writeByte(0);
        assertThrows(NetworkException.class,
                () -> ConfigSyncDocument.CODEC.decode(duplicate.toByteArray()));

        PayloadBuffer invalidUtf8 = header(1);
        invalidUtf8.writeString("key");
        invalidUtf8.writeByte(4);
        invalidUtf8.writeVarInt(1);
        invalidUtf8.writeByte(0xFF);
        assertThrows(NetworkException.class,
                () -> ConfigSyncDocument.CODEC.decode(invalidUtf8.toByteArray()));

        Object nested = "leaf";
        for (int depth = 0; depth <= ConfigSyncDocument.MAX_DEPTH + 1; depth++)
        {
            List<Object> wrapper = new ArrayList<>();
            wrapper.add(nested);
            nested = wrapper;
        }
        ConfigSyncDocument tooDeep = new ConfigSyncDocument(MOD_ID, Side.SERVER, 1,
                Map.of("deep", nested));
        assertThrows(NetworkException.class,
                () -> ConfigSyncDocument.CODEC.encode(tooDeep));
    }

    @Test
    void constructorAndCodecEnforceBounds()
    {
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigSyncDocument("Bad Id", Side.SERVER, 1, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigSyncDocument(MOD_ID, Side.SERVER, 0, Map.of()));

        LinkedHashMap<String, Object> tooMany = new LinkedHashMap<>();
        for (int index = 0; index <= ConfigSyncDocument.MAX_KEYS; index++)
        {
            tooMany.put("key." + index, index);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigSyncDocument(MOD_ID, Side.SERVER, 1, tooMany));

        String longKey = "k".repeat(ConfigSyncDocument.MAX_KEY_CHARS + 1);
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigSyncDocument(MOD_ID, Side.SERVER, 1,
                        Map.of(longKey, "value")));

        String longValue = "v".repeat(ConfigSyncDocument.MAX_VALUE_CHARS + 1);
        ConfigSyncDocument document = new ConfigSyncDocument(MOD_ID, Side.SERVER, 1,
                Map.of("value", longValue));
        assertThrows(NetworkException.class,
                () -> ConfigSyncDocument.CODEC.encode(document));
    }

    @Test
    void documentIsImmutableAndExposesTheEncodedSnapshot()
    {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("a", new ArrayList<>(List.of("one")));
        ConfigSyncDocument document = new ConfigSyncDocument(MOD_ID, Side.SERVER, 2, values);
        values.get("a");
        @SuppressWarnings("unchecked")
        List<Object> mutable = (List<Object>) values.get("a");
        mutable.add("two");

        assertEquals(List.of("one"), document.values().get("a"));
        assertThrows(UnsupportedOperationException.class,
                () -> document.values().put("b", 2));
        assertEquals(2, document.document().schemaVersion());
        assertFalse(document.values().isEmpty());
        assertTrue(document.toString().contains(MOD_ID));
    }

    private static PayloadBuffer header(int count)
    {
        PayloadBuffer buffer = PayloadBuffer.writer();
        buffer.writeByte(ConfigSyncDocument.FORMAT_VERSION);
        buffer.writeString(MOD_ID);
        buffer.writeByte(Side.SERVER.ordinal());
        buffer.writeVarInt(1);
        buffer.writeVarInt(count);
        return buffer;
    }
}
