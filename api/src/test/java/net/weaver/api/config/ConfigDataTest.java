package net.weaver.api.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDataTest
{
    @Test
    void constructorCopiesInputAndSnapshotsAreImmutable()
    {
        List<Object> nestedList = new ArrayList<>(List.of("a", "b"));
        Map<String, Object> nestedMap = new LinkedHashMap<>();
        nestedMap.put("inner", 1);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("flag", true);
        source.put("list", nestedList);
        source.put("map", nestedMap);

        ConfigData data = new ConfigData(4, source);

        source.put("extra", "later");
        nestedList.add("c");
        nestedMap.put("added", 2);

        assertEquals(4, data.schemaVersion());
        assertEquals(3, data.size());
        assertEquals(Set.of("flag", "list", "map"), data.keys());
        assertFalse(data.has("extra"));
        assertEquals(List.of("a", "b"), data.get("list"));

        Map<String, Object> snapshot = data.values();
        assertEquals(List.of("a", "b"), snapshot.get("list"));
        assertEquals(Map.of("inner", 1), snapshot.get("map"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("x", 1));
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) snapshot.get("list")).add("d"));
        assertThrows(UnsupportedOperationException.class,
                () -> ((Map<Object, Object>) snapshot.get("map")).put("k", "v"));
        assertNotSame(snapshot, data.values());
        assertEquals(snapshot, data.values());

        Map<String, Object> nullKey = new HashMap<>();
        nullKey.put(null, "value");
        assertThrows(NullPointerException.class, () -> new ConfigData(1, nullKey));
        assertThrows(NullPointerException.class, () -> new ConfigData(1, null));
    }

    @Test
    void typedGettersReadValuesAndUseFallbacks()
    {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("string", "text");
        values.put("int", 42);
        values.put("intLong", 7L);
        values.put("long", 5_000_000_000L);
        values.put("double", 2.5);
        values.put("boolean", true);
        values.put("list", List.of("a", "b"));
        ConfigData data = new ConfigData(1, values);

        assertEquals("text", data.getString("string", "fallback"));
        assertEquals("fallback", data.getString("missing", "fallback"));
        assertEquals(42, data.getInt("int", -1));
        assertEquals(7, data.getInt("intLong", -1));
        assertEquals(-1, data.getInt("missing", -1));
        assertEquals(5_000_000_000L, data.getLong("long", -1L));
        assertEquals(7L, data.getLong("intLong", -1L));
        assertEquals(-1L, data.getLong("missing", -1L));
        assertEquals(2.5, data.getDouble("double", -1.0), 0.0);
        assertEquals(-1.0, data.getDouble("missing", -1.0), 0.0);
        assertTrue(data.getBoolean("boolean", false));
        assertFalse(data.getBoolean("missing", false));
        assertEquals(List.of("a", "b"), data.getList("list"));
        assertTrue(data.getList("missing").isEmpty());
        assertNull(data.get("missing"));

        assertThrows(ConfigException.class, () -> data.getString("int", "fallback"));
        assertThrows(ConfigException.class, () -> data.getInt("string", -1));
        assertThrows(ConfigException.class, () -> data.getInt("long", -1));
        assertThrows(ConfigException.class, () -> data.getLong("double", -1L));
        assertThrows(ConfigException.class, () -> data.getLong("string", -1L));
        assertThrows(ConfigException.class, () -> data.getDouble("string", -1.0));
        assertThrows(ConfigException.class, () -> data.getBoolean("string", false));
        assertThrows(ConfigException.class, () -> data.getList("string"));
        assertThrows(NullPointerException.class, () -> data.getString(null, "fallback"));
        assertThrows(NullPointerException.class, () -> data.has(null));

        values.put("tooLarge", 5_000_000_000L);
        ConfigData wide = new ConfigData(1, values);
        assertThrows(ConfigException.class, () -> wide.getInt("tooLarge", -1));
    }

    @Test
    void putRemoveAndRenameFollowDocumentSemantics()
    {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("a", 1);
        base.put("b", 2);
        base.put("c", 3);
        ConfigData data = new ConfigData(9, base);

        data.put("d", Map.of("nested", List.of(1, 2)));
        assertEquals(4, data.size());
        assertEquals(Map.of("nested", List.of(1, 2)), data.get("d"));
        assertThrows(NullPointerException.class, () -> data.put(null, 1));
        assertThrows(NullPointerException.class, () -> data.put("x", null));
        assertThrows(IllegalArgumentException.class, () -> data.put("bad", new Object()));
        assertThrows(IllegalArgumentException.class,
                () -> data.put("badMap", Map.of(1, "non-string-key")));

        assertEquals(2, data.remove("b"));
        assertFalse(data.has("b"));
        assertNull(data.remove("missing"));
        assertEquals(3, data.size());

        data.rename("missing", "other");
        assertEquals(List.of("a", "c", "d"), new ArrayList<>(data.keys()));

        data.rename("a", "aa");
        assertEquals(List.of("aa", "c", "d"), new ArrayList<>(data.keys()));
        assertEquals(1, data.get("aa"));
        assertFalse(data.has("a"));

        data.put("taken", 4);
        ConfigException failure = assertThrows(ConfigException.class,
                () -> data.rename("aa", "taken"));
        assertTrue(failure.getMessage().contains("aa"));
        assertTrue(failure.getMessage().contains("taken"));
        assertEquals(1, data.get("aa"));
        assertEquals(4, data.get("taken"));
        assertEquals(9, data.schemaVersion());
        assertThrows(NullPointerException.class, () -> data.rename(null, "x"));
        assertThrows(NullPointerException.class, () -> data.rename("aa", null));
    }

    @Test
    void keysAndValuesPreserveInsertionOrder()
    {
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("z", 1);
        ordered.put("a", 2);
        ordered.put("m", 3);
        ConfigData data = new ConfigData(2, ordered);

        assertEquals(List.of("z", "a", "m"), new ArrayList<>(data.keys()));
        assertEquals(List.of("z", "a", "m"), new ArrayList<>(data.values().keySet()));
        assertEquals(2, data.schemaVersion());
    }
}
