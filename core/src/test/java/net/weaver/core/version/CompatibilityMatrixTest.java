package net.weaver.core.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityMatrixTest {

    @Test
    void baselineCombinationIsSupported() {
        assertEquals(CompatibilityMatrix.Level.SUPPORTED,
                CompatibilityMatrix.level("2.1.0", "26.3", "26.3"));
        assertTrue(CompatibilityMatrix.supports("2.1.0", "26.3", "26.3"));
    }

    @Test
    void featureLevelEntryIsSupported() {
        assertEquals(CompatibilityMatrix.Level.SUPPORTED,
                CompatibilityMatrix.level("2.2.0", "26.3", "26.3"));
        assertTrue(CompatibilityMatrix.supports("2.2.0", "26.3", "26.3"));
    }

    @Test
    void apiRowsMatchByMajorMinor() {
        assertEquals(CompatibilityMatrix.Level.SUPPORTED,
                CompatibilityMatrix.level("2.1.7", "26.3", "26.3"));
    }

    @Test
    void gameAndHookMatchByIdOrNumericEquality() {
        assertEquals(CompatibilityMatrix.Level.SUPPORTED,
                CompatibilityMatrix.level("2.1.0", "26.3.0", "26.3.0"));
        assertEquals(CompatibilityMatrix.Level.SUPPORTED,
                CompatibilityMatrix.level("2.1.0", "v26.3", "26.3"));
        assertEquals(CompatibilityMatrix.Level.UNSUPPORTED,
                CompatibilityMatrix.level("2.1.0", "26.4", "26.3"));
        assertEquals(CompatibilityMatrix.Level.UNSUPPORTED,
                CompatibilityMatrix.level("2.1.0", "26.3", "26.2"));
    }

    @Test
    void unknownCombinationsAreUnsupported() {
        assertEquals(CompatibilityMatrix.Level.UNSUPPORTED,
                CompatibilityMatrix.level("2.0.0", "26.3", "26.3"));
        assertEquals(CompatibilityMatrix.Level.UNSUPPORTED,
                CompatibilityMatrix.level("3.0.0", "26.3", "26.3"));
        assertEquals(CompatibilityMatrix.Level.UNSUPPORTED,
                CompatibilityMatrix.level("2.1.0", "1.20", "1.20"));
        assertEquals(CompatibilityMatrix.Level.UNSUPPORTED,
                CompatibilityMatrix.level(null, "26.3", "26.3"));
        assertEquals(CompatibilityMatrix.Level.UNSUPPORTED,
                CompatibilityMatrix.level("garbage", "26.3", "26.3"));
        assertEquals(CompatibilityMatrix.Level.UNSUPPORTED,
                CompatibilityMatrix.level("2.1.0", null, null));
        assertFalse(CompatibilityMatrix.supports("2.0.0", "26.3", "26.3"));
    }

    @Test
    void currentLevelIsSupported() {
        assertEquals(CompatibilityMatrix.Level.SUPPORTED, CompatibilityMatrix.currentLevel());
    }

    @Test
    void perAxisLevelsExposeSupport() {
        assertEquals(CompatibilityMatrix.Level.SUPPORTED,
                CompatibilityMatrix.levelForApi("2.1.0"));
        assertEquals(CompatibilityMatrix.Level.SUPPORTED,
                CompatibilityMatrix.levelForApi("2.2.0"));
        assertEquals(CompatibilityMatrix.Level.UNSUPPORTED,
                CompatibilityMatrix.levelForApi("3.0.0"));
        assertEquals(CompatibilityMatrix.Level.SUPPORTED,
                CompatibilityMatrix.levelForGame("26.3", "26.3"));
        assertEquals(CompatibilityMatrix.Level.UNSUPPORTED,
                CompatibilityMatrix.levelForGame("1.20", "1.20"));
    }

    @Test
    void entriesContainBaselineAndAdditiveFeatureLevel() {
        assertTrue(CompatibilityMatrix.ENTRIES.stream().anyMatch(entry ->
                "2.1".equals(entry.apiVersion())
                        && "26.3".equals(entry.gameVersion())
                        && "26.3".equals(entry.hookTarget())
                        && entry.level() == CompatibilityMatrix.Level.SUPPORTED));
        assertTrue(CompatibilityMatrix.ENTRIES.stream().anyMatch(entry ->
                "2.2".equals(entry.apiVersion())
                        && entry.note().toLowerCase(java.util.Locale.ROOT).contains("additive")));
        assertThrows(UnsupportedOperationException.class,
                () -> CompatibilityMatrix.ENTRIES.add(new CompatibilityMatrix.Entry(
                        "2.3", "26.3", "26.3", CompatibilityMatrix.Level.SUPPORTED, "test")));
    }

    @Test
    void describeRendersEveryEntryStably() {
        String first = CompatibilityMatrix.describe();
        String second = CompatibilityMatrix.describe();

        assertEquals(first, second);
        assertTrue(first.startsWith("Weaver compatibility matrix: 2 entries"));
        assertTrue(first.contains("api 2.1 x game 26.3 (hooks 26.3): SUPPORTED"));
        assertTrue(first.contains("api 2.2 x game 26.3 (hooks 26.3): SUPPORTED"));
    }

    @Test
    void entryRejectsBlankFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new CompatibilityMatrix.Entry(
                        " ", "26.3", "26.3", CompatibilityMatrix.Level.SUPPORTED, "test"));
        assertThrows(NullPointerException.class,
                () -> new CompatibilityMatrix.Entry(
                        "2.1", "26.3", "26.3", null, "test"));
        assertThrows(NullPointerException.class,
                () -> new CompatibilityMatrix.Entry(
                        "2.1", "26.3", "26.3", CompatibilityMatrix.Level.SUPPORTED, null));
    }
}
