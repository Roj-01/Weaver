package net.weaver.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which mod versions may join each other. */
class NetworkCompatibleTest
{
    @AfterEach
    void clear()
    {
        WeaverMods.install(List.of());
    }

    @Test
    void sameVersionAlwaysJoins()
    {
        WeaverMods.install(List.of(new WeaverMods.Mod("exact", "1.0.0", "Exact", "both")));
        assertTrue(WeaverMods.networkCompatible("exact", "1.0.0", "1.0.0"));
        assertFalse(WeaverMods.networkCompatible("exact", "1.0.0", "1.0.1"));
    }

    @Test
    void aDeclaredRangeAcceptsVersionsInsideIt()
    {
        WeaverMods.install(List.of(new WeaverMods.Mod("ranged", "1.2.0", "Ranged", "both", "", List.of(), "", "",
                ">=1.2.0 <2.0.0")));
        assertTrue(WeaverMods.networkCompatible("ranged", "1.2.0", "1.4.7"));
        assertFalse(WeaverMods.networkCompatible("ranged", "1.2.0", "2.0.0"));
        assertFalse(WeaverMods.networkCompatible("ranged", "1.2.0", "1.1.9"));
    }

    @Test
    void aRangeThatExcludesThisSideDoesNotWidenIt()
    {
        WeaverMods.install(List.of(new WeaverMods.Mod("odd", "3.0.0", "Odd", "both", "", List.of(), "", "", "^1.0")));
        assertFalse(WeaverMods.networkCompatible("odd", "3.0.0", "1.5.0"));
    }

    @Test
    void aClientWithoutARangeAcceptsWhatTheServerAccepted()
    {
        WeaverMods.install(List.of(new WeaverMods.Mod("old", "1.0.0", "Old", "both")));
        assertTrue(WeaverMods.clientAcceptsServer("old", "1.0.0", "1.1.0"));
        WeaverMods.install(List.of(new WeaverMods.Mod("strict", "1.0.0", "Strict", "both", "", List.of(), "", "",
                ">=1.0.0 <1.1.0")));
        assertFalse(WeaverMods.clientAcceptsServer("strict", "1.0.0", "1.1.0"));
        assertTrue(WeaverMods.clientAcceptsServer("strict", "1.0.0", "1.0.5"));
    }

    @Test
    void anUnknownModNeedsTheSameVersion()
    {
        assertTrue(WeaverMods.networkCompatible("missing", "1.0", "1.0"));
        assertFalse(WeaverMods.networkCompatible("missing", "1.0", "1.1"));
    }
}
