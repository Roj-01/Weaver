package net.weaver.core.version;

import net.weaver.api.WeaverApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformTargetTest {

    @Test
    void currentUsesTheRunningApiAndGameVersion() {
        PlatformTarget target = PlatformTarget.current();

        assertEquals(WeaverApi.VERSION, target.apiVersion());
        assertEquals(GameVersion.CURRENT, target.game());
        assertTrue(target.supported());
    }

    @Test
    void knownApiPrefixesAreSupported() {
        assertTrue(new PlatformTarget("2.1.0", GameVersion.CURRENT).supported());
        assertTrue(new PlatformTarget("2.2.3", GameVersion.CURRENT).supported());
    }

    @Test
    void unknownTargetsAreNotSupported() {
        assertFalse(new PlatformTarget("3.0.0", GameVersion.CURRENT).supported());
        assertFalse(new PlatformTarget("2.1.0", GameVersion.parse("1.20")).supported());
    }

    @Test
    void rejectsInvalidTargets() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlatformTarget(" ", GameVersion.CURRENT));
        assertThrows(NullPointerException.class, () -> new PlatformTarget("2.1.0", null));
    }
}
