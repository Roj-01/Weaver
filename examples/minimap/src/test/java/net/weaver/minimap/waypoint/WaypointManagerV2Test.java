package net.weaver.minimap.waypoint;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointManagerV2Test
{
    @TempDir
    Path temp;

    private WaypointManager manager()
    {
        return new WaypointManager(new WaypointStore(temp.resolve("waypoints.json")));
    }

    @Test
    void updateMoveAndVisibilityKeepIdentity()
    {
        WaypointManager manager = manager();
        Waypoint waypoint = manager.add("Home", "minecraft:overworld", 1, 2, 3, 4);

        Waypoint renamed = waypoint.withName("Base").withGroup("favorites");
        assertTrue(manager.update(renamed));
        assertEquals("Base", manager.get(waypoint.id()).orElseThrow().name());
        assertEquals("favorites", manager.get(waypoint.id()).orElseThrow().group());

        assertTrue(manager.move(waypoint.id(), 9, 10, 11).isPresent());
        assertEquals(9, manager.get(waypoint.id()).orElseThrow().x());
        assertEquals(10, manager.get(waypoint.id()).orElseThrow().y());
        assertEquals(11, manager.get(waypoint.id()).orElseThrow().z());

        assertTrue(manager.setVisible(waypoint.id(), false));
        assertFalse(manager.get(waypoint.id()).orElseThrow().visible());
        assertFalse(manager.setVisible(UUID.randomUUID(), true));
        assertFalse(manager.update(Waypoint.create("Missing", "minecraft:overworld",
                0, 0, 0, 0)));
    }

    @Test
    void groupsSearchAndSortAreDeterministic()
    {
        WaypointManager manager = manager();
        Waypoint gamma = new Waypoint(UUID.randomUUID(), "Gamma", "minecraft:overworld",
                5, 0, 5, 0x30000000, "pin", "f", true, false, false, 3L, 3L, Map.of());
        Waypoint alpha = new Waypoint(UUID.randomUUID(), "Alpha", "minecraft:overworld",
                1, 0, 1, 0x10000000, "star", "g", true, false, false, 1L, 1L, Map.of());
        Waypoint beta = new Waypoint(UUID.randomUUID(), "Beta", "minecraft:the_nether",
                9, 0, 9, 0x20000000, "pin", "f", true, false, false, 2L, 2L, Map.of());
        manager.add(gamma);
        manager.add(alpha);
        manager.add(beta);

        assertEquals(List.of("f", "g"), manager.groups());
        assertEquals(List.of(gamma, beta), manager.inGroup("f"));
        assertEquals(List.of(alpha), manager.search("STAR"));
        assertEquals(List.of(beta), manager.search("nether"));
        assertEquals(List.of(), manager.search("nothing"));

        assertEquals(List.of("Alpha", "Beta", "Gamma"),
                manager.sortedByName(true).stream().map(Waypoint::name).toList());
        assertEquals(List.of("Gamma", "Beta", "Alpha"),
                manager.sortedByName(false).stream().map(Waypoint::name).toList());
        assertEquals(List.of("Alpha", "Beta", "Gamma"),
                manager.sortedByColor(true).stream().map(Waypoint::name).toList());
        assertEquals(List.of("Alpha", "Gamma", "Beta"),
                manager.sortedByDistance(0, 0, true).stream()
                        .map(Waypoint::name).toList());

        assertEquals(2, manager.renameGroup("f", "shared"));
        assertEquals(List.of("g", "shared"), manager.groups());
    }

    @Test
    void importExportRoundTripPreservesExtensions() throws IOException
    {
        WaypointManager manager = manager();
        Waypoint waypoint = Waypoint.create("Home", "minecraft:overworld", 1, 2, 3, 4)
                .withExtension("future", new JsonPrimitive("kept"))
                .withGroup("base")
                .withTemporary(true);
        manager.add(waypoint);

        String exported = manager.exportJson();
        WaypointManager reloaded = manager();
        assertEquals(1, reloaded.importJson(exported, WaypointManager.ImportMode.REPLACE));
        assertEquals(List.of(waypoint), reloaded.all());
        assertEquals("kept", reloaded.all().get(0).extensions().get("future").getAsString());
    }

    @Test
    void mergeImportOverwritesMatchingIdsAndKeepsOthers() throws IOException
    {
        WaypointManager manager = manager();
        Waypoint first = Waypoint.create("First", "minecraft:overworld", 1, 2, 3, 4);
        manager.add(first);
        Waypoint replacement = first.withName("Renamed");
        String json = new WaypointStore(temp.resolve("other.json"))
                .toJson(List.of(replacement));

        assertEquals(1, manager.importJson(json, WaypointManager.ImportMode.MERGE));

        assertEquals(1, manager.count());
        assertEquals("Renamed", manager.all().get(0).name());
    }

    @Test
    void failedImportLeavesTheCurrentListUntouched() throws IOException
    {
        WaypointManager manager = manager();
        Waypoint first = manager.add("First", "minecraft:overworld", 1, 2, 3, 4);

        assertThrows(IOException.class,
                () -> manager.importJson("{\"version\": 2, \"waypoints\": ["));
        assertEquals(List.of(first), manager.all());
    }

    @Test
    void fileImportExportRoundTrip() throws IOException
    {
        WaypointManager manager = manager();
        manager.add("Home", "minecraft:overworld", 1, 2, 3, 4);
        Path file = temp.resolve("export.json");

        manager.exportFile(file);
        WaypointManager reloaded = manager();
        assertEquals(1, reloaded.importFile(file, WaypointManager.ImportMode.REPLACE));
        assertEquals(manager.all(), reloaded.all());
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"version\": 2"));
    }

    @Test
    void dedupeRemovesRepeatedIdsAndVisibleIdentities()
    {
        WaypointManager manager = manager();
        UUID shared = UUID.randomUUID();
        Waypoint older = new Waypoint(shared, "Home", "minecraft:overworld", 1, 2, 3, 4,
                "pin", "", true, false, false, 10L, 10L, Map.of());
        Waypoint newer = new Waypoint(shared, "Home", "minecraft:overworld", 1, 2, 3, 4,
                "pin", "", true, false, false, 10L, 20L, Map.of());
        Waypoint duplicateIdentity = new Waypoint(UUID.randomUUID(), "Home",
                "minecraft:overworld", 1, 2, 3, 4, "pin", "", true, false, false,
                15L, 15L, Map.of());
        manager.add(older);
        manager.add(newer);
        manager.add(duplicateIdentity);

        assertEquals(2, manager.dedupe());
        assertEquals(1, manager.count());
        assertEquals(20L, manager.all().get(0).updatedAt());
    }

    @Test
    void deathWaypointReplacesOnlyTheSameDimension()
    {
        WaypointManager manager = manager();
        Waypoint first = manager.setDeathWaypoint("minecraft:overworld", 1, 64, 2);
        Waypoint nether = manager.setDeathWaypoint("minecraft:the_nether", 3, 70, 4);
        Waypoint second = manager.setDeathWaypoint("minecraft:overworld", 5, 65, 6);

        assertTrue(first.death());
        assertTrue(second.death());
        assertEquals(WaypointManager.DEATH_WAYPOINT_ICON, second.icon());
        assertEquals(2, manager.deathWaypoints().size());
        assertTrue(manager.deathWaypoints().contains(nether));
        assertTrue(manager.deathWaypoints().contains(second));
        assertFalse(manager.deathWaypoints().contains(first));
    }

    @Test
    void temporaryWaypointsExpire()
    {
        WaypointManager manager = manager();
        Waypoint temporary = new Waypoint(UUID.randomUUID(), "Temp", "minecraft:overworld",
                0, 0, 0, 0, "pin", "", true, true, false, 1_000L, 1_000L, Map.of());
        Waypoint permanent = Waypoint.create("Keep", "minecraft:overworld", 0, 0, 0, 0);
        manager.add(temporary);
        manager.add(permanent);

        assertEquals(0, manager.expireTemporary(2_000L, 60_000L));
        assertEquals(1, manager.expireTemporary(61_000L, 60_000L));
        assertEquals(List.of(permanent), manager.all());
        assertEquals(0, manager.expireTemporaryMinutes(0));
    }

    @Test
    void syncRecordsDescribeTheCurrentList()
    {
        WaypointManager manager = manager();
        Waypoint waypoint = manager.add("Home", "minecraft:overworld", 1, 2, 3, 4);

        List<WaypointSyncRecord> records = manager.syncRecords(7L);

        assertEquals(1, records.size());
        assertEquals(7L, records.get(0).revision());
        assertEquals(waypoint, records.get(0).toWaypoint());
    }

    @Test
    void forWorldUsesTheConventionalFileName()
    {
        WaypointManager manager = WaypointManager.forWorld(temp.resolve("world"));
        assertEquals(temp.resolve("world").resolve("waypoints.json"), manager.store().file());
    }

    @Test
    void clearCountsAndContainsBehave()
    {
        WaypointManager manager = manager();
        assertTrue(manager.isEmpty());
        Waypoint waypoint = manager.add("Home", "minecraft:overworld", 0, 0, 0, 0);
        assertTrue(manager.contains(waypoint.id()));
        assertEquals(1, manager.count());
        manager.clear();
        assertTrue(manager.isEmpty());
        assertEquals(0, manager.count());
    }
}
