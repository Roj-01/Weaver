package net.weaver.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionRangeTest {

    @Test
    void preReleaseOnlyLosesToTheSameCore() {
        assertTrue(VersionRange.compare("2.1.0-SNAPSHOT", "1.0") > 0);
        assertTrue(VersionRange.compare("1.0", "2.1.0-SNAPSHOT") < 0);
        assertTrue(VersionRange.compare("1.0.0-SNAPSHOT", "1.0.0") < 0);
        assertTrue(VersionRange.compare("1.0.0", "1.0.0-rc.1") > 0);
        assertTrue(VersionRange.compare("1.0.0-alpha", "1.0.0-beta") < 0);
        assertTrue(VersionRange.compare("1.0.0-beta.2", "1.0.0-beta.11") < 0);
        assertEquals(0, VersionRange.compare("1.0", "1.0.0"));
        assertEquals(0, VersionRange.compare("v1.2.3", "1.2.3"));
        assertEquals(0, VersionRange.compare("1.2.3+build.5", "1.2.3"));
        assertTrue(VersionRange.compare("1.10.0", "1.9.0") > 0);
    }

    @Test
    void preReleaseSatisfiesALowerBoundBelowItsCore() {
        assertTrue(VersionRange.matches("2.1.0-SNAPSHOT", ">=1.0"));
        assertFalse(VersionRange.matches("1.0.0-SNAPSHOT", ">=1.0.0"));
    }

    @Test
    void operatorsAcceptWhitespaceBeforeTheVersion() {
        assertTrue(VersionRange.matches("1.5.0", ">= 1.2.0"));
        assertTrue(VersionRange.matches("1.2.0", ">= 1.2.0"));
        assertFalse(VersionRange.matches("1.1.0", ">= 1.2.0"));
        assertTrue(VersionRange.matches("1.5.0", ">= 1.2.0 < 2.0"));
        assertFalse(VersionRange.matches("2.0.0", ">= 1.2.0, < 2.0"));
        assertTrue(VersionRange.matches("1.9", ">=1.2.0,<2.0"));
    }

    @Test
    void previouslyValidFormsKeepTheirMeaning() {
        assertTrue(VersionRange.matches("9.9.9", null));
        assertTrue(VersionRange.matches("9.9.9", ""));
        assertTrue(VersionRange.matches("9.9.9", "*"));
        assertTrue(VersionRange.matches("1.2.0", "1.2.0"));
        assertFalse(VersionRange.matches("1.2.1", "1.2.0"));
        assertTrue(VersionRange.matches("1.2.0", "=1.2.0"));
        assertTrue(VersionRange.matches("1.3", ">1.2"));
        assertTrue(VersionRange.matches("1.2", "<=1.2"));
        assertFalse(VersionRange.matches("1.2", "<1.2"));
        assertTrue(VersionRange.matches("1.2.9", "~1.2.3"));
        assertFalse(VersionRange.matches("1.3.0", "~1.2.3"));
        assertTrue(VersionRange.matches("1.9.0", "^1.2.3"));
        assertFalse(VersionRange.matches("2.0.0", "^1.2.3"));
        assertFalse(VersionRange.matches("1.2.2", "^1.2.3"));
    }

    @Test
    void caretPinsTheFirstNonZeroComponent() {
        assertTrue(VersionRange.matches("0.2.5", "^0.2"));
        assertFalse(VersionRange.matches("0.9.0", "^0.2"));
        assertFalse(VersionRange.matches("0.3.0", "^0.2.3"));
        assertTrue(VersionRange.matches("0.2.9", "^0.2.3"));
        assertTrue(VersionRange.matches("0.0.3", "^0.0.3"));
        assertFalse(VersionRange.matches("0.0.4", "^0.0.3"));
        assertTrue(VersionRange.matches("0.9", "^0"));
        assertFalse(VersionRange.matches("1.0", "^0"));
    }

    @Test
    void tildeAllowsPatchOrMinorChangesDependingOnPrecision() {
        assertTrue(VersionRange.matches("1.9.0", "~1"));
        assertFalse(VersionRange.matches("2.0.0", "~1"));
        assertFalse(VersionRange.matches("0.9.0", "~1"));
        assertTrue(VersionRange.matches("1.2.7", "~1.2"));
        assertFalse(VersionRange.matches("1.3.0", "~1.2"));
    }

    @Test
    void mavenRangesMatch() {
        assertTrue(VersionRange.matches("1.0", "[1.0,2.0)"));
        assertTrue(VersionRange.matches("1.5.3", "[1.0,2.0)"));
        assertFalse(VersionRange.matches("2.0", "[1.0,2.0)"));
        assertFalse(VersionRange.matches("1.0", "(1.0,2.0]"));
        assertTrue(VersionRange.matches("2.0", "(1.0,2.0]"));
        assertTrue(VersionRange.matches("99", "[1.0,)"));
        assertTrue(VersionRange.matches("0.1", "(,2.0]"));
        assertTrue(VersionRange.matches("1.0", "[1.0]"));
        assertFalse(VersionRange.matches("1.0.1", "[1.0]"));
        assertTrue(VersionRange.matches("3.5", "[1.0,2.0),[3.0,4.0)"));
        assertFalse(VersionRange.matches("2.5", "[1.0,2.0),[3.0,4.0)"));
        assertTrue(VersionRange.matches("1.5", "[ 1.0 , 2.0 )"));
    }

    @Test
    void unparseableRangesAreReportedAndNeverMatch() {
        assertNull(VersionRange.problem(">= 1.2.0"));
        assertNull(VersionRange.problem("[1.0,2.0)"));
        assertNull(VersionRange.problem("*"));
        for (String bad : new String[]{">=", ">=abc", "=>1.0", "1.0 - 2.0", "[1.0,2.0",
                "[1.0,2.0)x", "(1.0)", "[,]", "1.0 || 2.0"}) {
            assertFalse(VersionRange.isValid(bad), bad);
            assertNotNull(VersionRange.problem(bad), bad);
            assertFalse(VersionRange.matches("1.0", bad), bad);
        }
    }
}
