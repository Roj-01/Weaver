package net.weaver.core.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemVerTest {

    @Test
    void parsesPlainThreeComponentVersions() {
        SemVer version = SemVer.parse("2.1.0");

        assertEquals(new SemVer(2, 1, 0), version);
        assertEquals("2.1.0", version.toString());
    }

    @Test
    void stripsPrefixAndSuffixes() {
        assertEquals(new SemVer(2, 1, 0), SemVer.parse("v2.1.0"));
        assertEquals(new SemVer(2, 1, 0), SemVer.parse("V2.1.0"));
        assertEquals(new SemVer(2, 1, 0), SemVer.parse("v2.1.0-rc.1"));
        assertEquals(new SemVer(2, 1, 0), SemVer.parse("2.1.0+build.5"));
        assertEquals(new SemVer(2, 1, 0), SemVer.parse("  v2.1.0-rc.1+build.5  "));
    }

    @Test
    void missingComponentsDefaultToZero() {
        assertEquals(new SemVer(2, 0, 0), SemVer.parse("2"));
        assertEquals(new SemVer(2, 1, 0), SemVer.parse("2.1"));
    }

    @Test
    void comparesAndTestsMajorEquality() {
        SemVer baseline = SemVer.parse("2.1.0");
        SemVer newer = SemVer.parse("2.2.0");
        SemVer major = SemVer.parse("3.0.0");

        assertTrue(newer.atLeast(baseline));
        assertTrue(baseline.atLeast(baseline));
        assertFalse(baseline.atLeast(newer));
        assertTrue(baseline.sameMajor(newer));
        assertFalse(baseline.sameMajor(major));
        assertTrue(newer.compareTo(baseline) > 0);
        assertTrue(baseline.compareTo(newer) < 0);
        assertEquals(0, baseline.compareTo(SemVer.parse("2.1")));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse(null));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse(""));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("v"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("2.x.0"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("2..1"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("2.1.0.1"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("not-a-version"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("-1.0.0"));
        assertThrows(IllegalArgumentException.class,
                () -> SemVer.parse("999999999999999999999.0.0"));
    }

    @Test
    void rejectsNegativeComponents() {
        assertThrows(IllegalArgumentException.class, () -> new SemVer(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SemVer(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SemVer(0, 0, -1));
    }
}
