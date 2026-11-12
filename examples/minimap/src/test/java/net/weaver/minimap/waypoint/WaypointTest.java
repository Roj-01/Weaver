package net.weaver.minimap.waypoint;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointTest
{
    @Test
    void createAssignsIdentifierAndVisibility()
    {
        Waypoint first = Waypoint.create("Home", "minecraft:overworld", 10, 64, -20,
                0xFF00FF00);
        Waypoint second = Waypoint.create("Home", "minecraft:overworld", 10, 64, -20,
                0xFF00FF00);

        assertNotNull(first.id());
        assertNotEquals(first.id(), second.id());
        assertEquals("Home", first.name());
        assertEquals("minecraft:overworld", first.dimension());
        assertEquals(10, first.x());
        assertEquals(64, first.y());
        assertEquals(-20, first.z());
        assertEquals(0xFF00FF00, first.color());
        assertTrue(first.visible());
    }

    @Test
    void colorKeepsAllThirtyTwoBits()
    {
        UUID id = UUID.randomUUID();

        Waypoint waypoint = new Waypoint(id, "Deep", "minecraft:overworld", 0, -64, 0,
                -1, true);

        assertEquals(-1, waypoint.color());
        assertEquals(0xFFFFFFFF, waypoint.color());
    }

    @Test
    void nullAndBlankFieldsAreRejected()
    {
        UUID id = UUID.randomUUID();

        assertThrows(NullPointerException.class,
                () -> new Waypoint(null, "Home", "minecraft:overworld", 0, 0, 0, 0, true));
        assertThrows(NullPointerException.class,
                () -> new Waypoint(id, null, "minecraft:overworld", 0, 0, 0, 0, true));
        assertThrows(NullPointerException.class,
                () -> new Waypoint(id, "Home", null, 0, 0, 0, 0, true));

        IllegalArgumentException blankName = assertThrows(IllegalArgumentException.class,
                () -> new Waypoint(id, "  ", "minecraft:overworld", 0, 0, 0, 0, true));
        assertTrue(blankName.getMessage().contains("name"));

        IllegalArgumentException blankDimension = assertThrows(IllegalArgumentException.class,
                () -> new Waypoint(id, "Home", " ", 0, 0, 0, 0, true));
        assertTrue(blankDimension.getMessage().contains("dimension"));
    }

    @Test
    void withVisibleReturnsUpdatedCopy()
    {
        Waypoint waypoint = new Waypoint(UUID.randomUUID(), "Home", "minecraft:overworld",
                1, 2, 3, 0x80123456, true);

        Waypoint hidden = waypoint.withVisible(false);

        assertNotEquals(waypoint, hidden);
        assertEquals(waypoint.id(), hidden.id());
        assertEquals(waypoint.name(), hidden.name());
        assertEquals(waypoint.dimension(), hidden.dimension());
        assertEquals(waypoint.x(), hidden.x());
        assertEquals(waypoint.y(), hidden.y());
        assertEquals(waypoint.z(), hidden.z());
        assertEquals(waypoint.color(), hidden.color());
        assertFalse(hidden.visible());
        assertTrue(waypoint.visible());
        assertTrue(hidden.withVisible(true).visible());
    }
}
