package net.weaver.minimap.waypoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointStoreTest
{
    @TempDir
    Path temp;

    @Test
    void missingFileLoadsAsEmpty() throws IOException
    {
        Path file = temp.resolve("waypoints.json");
        WaypointStore store = new WaypointStore(file);

        assertTrue(store.load().isEmpty());
        assertFalse(Files.exists(file));
    }

    @Test
    void blankFileLoadsAsEmpty() throws IOException
    {
        Path file = temp.resolve("waypoints.json");
        Files.writeString(file, "  \n\t", StandardCharsets.UTF_8);
        WaypointStore store = new WaypointStore(file);

        assertTrue(store.load().isEmpty());
    }

    @Test
    void roundTripPreservesOrderAndFields() throws IOException
    {
        Path file = temp.resolve("waypoints.json");
        WaypointStore store = new WaypointStore(file);
        Waypoint home = new Waypoint(UUID.randomUUID(), "Home", "minecraft:overworld",
                10, 64, -20, 0xFF00FF00, true);
        Waypoint nether = new Waypoint(UUID.randomUUID(), "Nether", "minecraft:the_nether",
                -5, 70, 8, 0x80123456, false);

        store.save(List.of(home, nether));
        List<Waypoint> loaded = store.load();

        assertEquals(List.of(home, nether), loaded);
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals(2, root.get("version").getAsInt());
        JsonArray array = root.getAsJsonArray("waypoints");
        assertEquals(2, array.size());
        assertEquals(home.id().toString(),
                array.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(nether.id().toString(),
                array.get(1).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void emptyListSavesValidFile() throws IOException
    {
        Path file = temp.resolve("waypoints.json");
        WaypointStore store = new WaypointStore(file);

        store.save(List.of());

        assertTrue(Files.exists(file));
        assertTrue(store.load().isEmpty());
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals(2, root.get("version").getAsInt());
        assertEquals(0, root.getAsJsonArray("waypoints").size());
    }

    @Test
    void malformedJsonIsRejected() throws IOException
    {
        Path file = temp.resolve("waypoints.json");
        Files.writeString(file, "{\"version\": 1, \"waypoints\": [", StandardCharsets.UTF_8);
        WaypointStore store = new WaypointStore(file);

        IOException error = assertThrows(IOException.class, store::load);

        assertTrue(error.getMessage().contains(file.getFileName().toString()));
    }

    @Test
    void nonObjectRootIsRejected() throws IOException
    {
        Path file = temp.resolve("waypoints.json");
        Files.writeString(file, "[1, 2, 3]", StandardCharsets.UTF_8);
        WaypointStore store = new WaypointStore(file);

        IOException error = assertThrows(IOException.class, store::load);

        assertTrue(error.getMessage().contains("root"));
    }

    @Test
    void unsupportedVersionIsRejected() throws IOException
    {
        Path file = temp.resolve("waypoints.json");
        Files.writeString(file, "{\"version\": 3, \"waypoints\": []}",
                StandardCharsets.UTF_8);
        WaypointStore store = new WaypointStore(file);

        IOException error = assertThrows(IOException.class, store::load);

        assertTrue(error.getMessage().contains("version"));
    }

    @Test
    void malformedWaypointEntryIsRejected() throws IOException
    {
        Path file = temp.resolve("waypoints.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "waypoints": [
                    {
                      "id": "not-a-uuid",
                      "name": "Home",
                      "dimension": "minecraft:overworld",
                      "x": 0,
                      "y": 64,
                      "z": 0,
                      "color": -1,
                      "visible": true
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        WaypointStore store = new WaypointStore(file);

        IOException error = assertThrows(IOException.class, store::load);

        assertTrue(error.getMessage().contains("UUID"));
    }

    @Test
    void missingWaypointFieldIsRejected() throws IOException
    {
        Path file = temp.resolve("waypoints.json");
        Files.writeString(file, """
                {
                  "version": 1,
                  "waypoints": [
                    {
                      "id": "00000000-0000-0000-0000-000000000000",
                      "dimension": "minecraft:overworld",
                      "x": 0,
                      "y": 64,
                      "z": 0,
                      "color": -1,
                      "visible": true
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        WaypointStore store = new WaypointStore(file);

        IOException error = assertThrows(IOException.class, store::load);

        assertTrue(error.getMessage().contains("name"));
    }

    @Test
    void saveCreatesParentDirectories() throws IOException
    {
        Path file = temp.resolve("nested").resolve("waypoints.json");
        WaypointStore store = new WaypointStore(file);
        Waypoint waypoint = Waypoint.create("Home", "minecraft:overworld", 1, 2, 3, 4);

        store.save(List.of(waypoint));

        assertTrue(Files.exists(file));
        assertEquals(List.of(waypoint), store.load());
    }
}
