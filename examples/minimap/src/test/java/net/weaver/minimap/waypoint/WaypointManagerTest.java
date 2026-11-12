package net.weaver.minimap.waypoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointManagerTest
{
    @TempDir
    Path temp;

    @Test
    void loadReplacesExistingContents() throws IOException
    {
        WaypointStore store = new WaypointStore(temp.resolve("waypoints.json"));
        Waypoint first = Waypoint.create("First", "minecraft:overworld", 1, 2, 3, 4);
        Waypoint second = Waypoint.create("Second", "minecraft:the_nether", 5, 6, 7, 8);
        store.save(List.of(first, second));
        WaypointManager manager = new WaypointManager(store);
        manager.add("Temporary", "minecraft:the_end", 0, 0, 0, 0);

        manager.load();

        assertEquals(List.of(first, second), manager.all());
    }

    @Test
    void allReturnsUnmodifiableCopy() throws IOException
    {
        WaypointManager manager = new WaypointManager(
                new WaypointStore(temp.resolve("waypoints.json")));
        manager.add("First", "minecraft:overworld", 1, 2, 3, 4);
        List<Waypoint> snapshot = manager.all();

        manager.add("Second", "minecraft:overworld", 5, 6, 7, 8);

        assertEquals(1, snapshot.size());
        assertEquals(2, manager.all().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(Waypoint.create("Third", "minecraft:overworld", 0, 0, 0, 0)));
    }

    @Test
    void inDimensionFiltersInInsertionOrder()
    {
        WaypointManager manager = new WaypointManager(
                new WaypointStore(temp.resolve("waypoints.json")));
        Waypoint first = manager.add("First", "minecraft:overworld", 1, 2, 3, 4);
        manager.add("Nether", "minecraft:the_nether", 5, 6, 7, 8);
        Waypoint second = manager.add("Second", "minecraft:overworld", 9, 10, 11, 12);

        assertEquals(List.of(first, second), manager.inDimension("minecraft:overworld"));
        assertEquals(List.of(), manager.inDimension("minecraft:the_end"));
    }

    @Test
    void addRemoveAndToggleMaintainOrder()
    {
        WaypointManager manager = new WaypointManager(
                new WaypointStore(temp.resolve("waypoints.json")));
        Waypoint first = manager.add("First", "minecraft:overworld", 1, 2, 3, 4);
        Waypoint second = manager.add("Second", "minecraft:overworld", 5, 6, 7, 8);

        assertTrue(manager.remove(first.id()));
        assertFalse(manager.remove(first.id()));
        assertEquals(List.of(second), manager.all());

        assertFalse(manager.toggleVisible(UUID.randomUUID()));
        assertTrue(manager.toggleVisible(second.id()));
        assertFalse(manager.all().get(0).visible());
        assertTrue(manager.toggleVisible(second.id()));
        assertTrue(manager.all().get(0).visible());
        assertEquals(second.id(), manager.all().get(0).id());
    }

    @Test
    void saveRoundTripsThroughAStore() throws IOException
    {
        Path file = temp.resolve("waypoints.json");
        WaypointManager manager = new WaypointManager(new WaypointStore(file));
        Waypoint first = manager.add("First", "minecraft:overworld", 1, 2, 3, 0xFF010203);
        Waypoint second = manager.add("Second", "minecraft:the_nether", 4, 5, 6, 0x80405060);
        manager.toggleVisible(second.id());

        manager.save();
        WaypointManager reloaded = new WaypointManager(new WaypointStore(file));
        reloaded.load();

        assertEquals(List.of(first, manager.all().get(1)), reloaded.all());
    }
}
