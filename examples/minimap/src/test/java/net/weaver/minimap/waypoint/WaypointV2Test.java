package net.weaver.minimap.waypoint;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointV2Test
{
    @TempDir
    Path temp;

    @Test
    void versionOneLoadsWithVersionTwoDefaults() throws IOException
    {
        String json = """
                {
                  "version": 1,
                  "waypoints": [
                    {
                      "id": "00000000-0000-0000-0000-000000000001",
                      "name": "Home",
                      "dimension": "minecraft:overworld",
                      "x": 10,
                      "y": 64,
                      "z": -20,
                      "color": -16711936,
                      "visible": true
                    }
                  ]
                }
                """;

        List<Waypoint> loaded = new WaypointStore(temp.resolve("waypoints.json"))
                .fromJson(json);

        assertEquals(1, loaded.size());
        Waypoint waypoint = loaded.get(0);
        assertEquals("Home", waypoint.name());
        assertEquals(Waypoint.DEFAULT_ICON, waypoint.icon());
        assertEquals(Waypoint.NO_GROUP, waypoint.group());
        assertFalse(waypoint.temporary());
        assertFalse(waypoint.death());
        assertEquals(0L, waypoint.createdAt());
        assertEquals(0L, waypoint.updatedAt());
        assertTrue(waypoint.extensions().isEmpty());
    }

    @Test
    void versionTwoRoundTripPreservesEveryFieldAndExtension() throws IOException
    {
        WaypointStore store = new WaypointStore(temp.resolve("waypoints.json"));
        JsonObject nested = JsonParser.parseString("{\"mode\":\"deep\",\"level\":3}")
                .getAsJsonObject();
        Waypoint waypoint = new Waypoint(UUID.randomUUID(), "Home", "minecraft:overworld",
                10, 64, -20, 0xFF00FF00, "star", "Favorites", false, true, false,
                1_000L, 2_000L, Map.of("futureFlag", new JsonPrimitive(true),
                "nested", nested));

        String json = store.toJson(List.of(waypoint));
        List<Waypoint> loaded = store.fromJson(json);

        assertEquals(List.of(waypoint), loaded);
        assertEquals(new JsonPrimitive(true), loaded.get(0).extensions().get("futureFlag"));
        assertEquals(nested, loaded.get(0).extensions().get("nested"));
    }

    @Test
    void savingVersionOneRewritesVersionTwoAndPreservesUnknownFields() throws IOException
    {
        Path file = temp.resolve("waypoints.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "waypoints": [
                    {
                      "id": "00000000-0000-0000-0000-000000000002",
                      "name": "Outpost",
                      "dimension": "minecraft:the_nether",
                      "x": 1,
                      "y": 2,
                      "z": 3,
                      "color": 255,
                      "visible": false,
                      "legacyNote": "keep-me"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        WaypointStore store = new WaypointStore(file);

        List<Waypoint> loaded = store.load();
        store.save(loaded);

        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals(2, root.get("version").getAsInt());
        JsonObject entry = root.getAsJsonArray("waypoints").get(0).getAsJsonObject();
        assertEquals("keep-me", entry.get("legacyNote").getAsString());
        assertEquals(Waypoint.DEFAULT_ICON, entry.get("icon").getAsString());
        assertFalse(entry.get("temporary").getAsBoolean());
        assertEquals(0L, entry.get("createdAt").getAsLong());
    }

    @Test
    void extensionKeysCannotShadowCoreFields()
    {
        UUID id = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> new Waypoint(id, "Home", "minecraft:overworld", 0, 0, 0, 0,
                        "pin", "", true, false, false, 0L, 0L,
                        Map.of("name", new JsonPrimitive("shadow"))));
        Waypoint waypoint = new Waypoint(id, "Home", "minecraft:overworld", 0, 0, 0, 0,
                true);
        assertThrows(IllegalArgumentException.class,
                () -> waypoint.withExtension("id", new JsonPrimitive("shadow")));
    }

    @Test
    void eightArgumentConstructorKeepsDefaults()
    {
        Waypoint waypoint = new Waypoint(UUID.randomUUID(), "Home", "minecraft:overworld",
                1, 2, 3, 4, true);

        assertEquals(Waypoint.DEFAULT_ICON, waypoint.icon());
        assertEquals(Waypoint.NO_GROUP, waypoint.group());
        assertTrue(waypoint.createdAt() > 0L);
        assertTrue(waypoint.updatedAt() > 0L);
        assertTrue(waypoint.extensions().isEmpty());
    }

    @Test
    void malformedOptionalFieldIsRejected() throws IOException
    {
        String json = """
                {
                  "version": 2,
                  "waypoints": [
                    {
                      "id": "00000000-0000-0000-0000-000000000003",
                      "name": "Home",
                      "dimension": "minecraft:overworld",
                      "x": 0,
                      "y": 64,
                      "z": 0,
                      "color": -1,
                      "createdAt": "yesterday"
                    }
                  ]
                }
                """;

        IOException error = assertThrows(IOException.class,
                () -> new WaypointStore(temp.resolve("waypoints.json")).fromJson(json));

        assertTrue(error.getMessage().contains("createdAt"));
    }

    @Test
    void withMethodsPreserveCreationTimeAndUpdateStamp()
    {
        Waypoint waypoint = new Waypoint(UUID.randomUUID(), "Home", "minecraft:overworld",
                1, 2, 3, 0xFF112233, "pin", "", true, false, false, 100L, 200L,
                Map.of());

        Waypoint renamed = waypoint.withName("Base");

        assertEquals(100L, renamed.createdAt());
        assertTrue(renamed.updatedAt() >= 200L);
        assertEquals("Home", waypoint.name());
        assertEquals("Base", renamed.name());
    }

    @Test
    void expiredNeedsTemporaryFlagAndKnownAge()
    {
        Waypoint temporary = new Waypoint(UUID.randomUUID(), "Temp", "minecraft:overworld",
                0, 0, 0, 0, "pin", "", true, true, false, 1_000L, 1_000L, Map.of());
        Waypoint permanent = temporary.withTemporary(false);
        Waypoint migrated = new Waypoint(UUID.randomUUID(), "Old", "minecraft:overworld",
                0, 0, 0, 0, "pin", "", true, true, false, 0L, 0L, Map.of());

        assertTrue(temporary.expired(11_000L, 10_000L));
        assertFalse(temporary.expired(10_999L, 10_000L));
        assertFalse(permanent.expired(1_000_000L, 10_000L));
        assertFalse(migrated.expired(1_000_000L, 10_000L));
        assertFalse(temporary.expired(11_000L, 0L));
    }

    @Test
    void extensionMapIsUnmodifiable()
    {
        Waypoint waypoint = Waypoint.create("Home", "minecraft:overworld", 0, 0, 0, 0)
                .withExtension("future", new JsonPrimitive(1));

        assertNotNull(waypoint.extensions().get("future"));
        assertThrows(UnsupportedOperationException.class,
                () -> waypoint.extensions().put("other", new JsonPrimitive(2)));
    }
}
