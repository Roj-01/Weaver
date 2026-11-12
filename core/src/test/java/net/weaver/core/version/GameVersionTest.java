package net.weaver.core.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameVersionTest {

    @Test
    void currentMatchesTheImplementedTarget() {
        assertEquals("26.3", GameVersion.CURRENT.id());
        assertEquals("26.3", GameVersion.CURRENT.hookTarget());
        assertTrue(GameVersion.CURRENT.isSupported());
    }

    @Test
    void parseUsesTheIdAsTheHookTarget() {
        GameVersion parsed = GameVersion.parse("26.3");

        assertEquals("26.3", parsed.id());
        assertEquals("26.3", parsed.hookTarget());
        assertEquals(GameVersion.CURRENT, parsed);
    }

    @Test
    void unknownVersionsAreNotSupported() {
        assertFalse(new GameVersion("1.20", "1.20").isSupported());
        assertFalse(GameVersion.parse("26.4").isSupported());
    }

    @Test
    void rejectsBlankIds() {
        assertThrows(IllegalArgumentException.class, () -> GameVersion.parse(null));
        assertThrows(IllegalArgumentException.class, () -> GameVersion.parse(" "));
        assertThrows(IllegalArgumentException.class, () -> new GameVersion("26.3", " "));
    }
}
