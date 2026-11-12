package net.weaver.api.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicalSideTest {

    @Test
    void mapsEveryPhysicalSide() {
        assertEquals(LogicalSide.CLIENT, LogicalSide.from(Side.CLIENT));
        assertEquals(LogicalSide.SERVER, LogicalSide.from(Side.SERVER));
        assertEquals(LogicalSide.BOTH, LogicalSide.from(Side.BOTH));
    }

    @Test
    void treatsNullPhysicalSideAsBoth() {
        assertEquals(LogicalSide.BOTH, LogicalSide.from(null));
    }

    @Test
    void clientAndServerPredicatesIncludeBoth() {
        assertTrue(LogicalSide.CLIENT.isClient());
        assertFalse(LogicalSide.CLIENT.isServer());

        assertTrue(LogicalSide.SERVER.isServer());
        assertFalse(LogicalSide.SERVER.isClient());

        assertTrue(LogicalSide.BOTH.isClient());
        assertTrue(LogicalSide.BOTH.isServer());
    }

    @Test
    void matchesIsSymmetricAndBothMatchesEverything() {
        assertTrue(LogicalSide.BOTH.matches(LogicalSide.CLIENT));
        assertTrue(LogicalSide.BOTH.matches(LogicalSide.SERVER));
        assertTrue(LogicalSide.BOTH.matches(LogicalSide.BOTH));
        assertTrue(LogicalSide.CLIENT.matches(LogicalSide.BOTH));
        assertTrue(LogicalSide.SERVER.matches(LogicalSide.BOTH));
        assertTrue(LogicalSide.CLIENT.matches(LogicalSide.CLIENT));
        assertTrue(LogicalSide.SERVER.matches(LogicalSide.SERVER));
        assertFalse(LogicalSide.CLIENT.matches(LogicalSide.SERVER));
        assertFalse(LogicalSide.SERVER.matches(LogicalSide.CLIENT));
    }
}
