package net.weaver.core.version;

import net.weaver.api.WeaverApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionChecksTest {

    @Test
    void compatibleResultCoversTypedGameApi() {
        VersionChecks.Result result = VersionChecks.check(
                TestGameApiClassLoaders.withVersion("26.3"), "26.3");

        assertTrue(result.compatible());
        assertEquals(CompatibilityMatrix.Level.SUPPORTED, result.apiLevel());
        assertEquals(CompatibilityMatrix.Level.SUPPORTED, result.gameLevel());
        assertTrue(result.message().contains("api=" + WeaverApi.VERSION), result.message());
        assertTrue(result.message().contains("game=26.3 (SUPPORTED)"), result.message());
        assertTrue(result.message().contains("typedGameApi=26.3 (compatible)"), result.message());
        assertTrue(result.message().contains("compatible=true"), result.message());
    }

    @Test
    void absentOptionalGameApiDoesNotFailTheCheck() {
        VersionChecks.Result missing = VersionChecks.check(
                TestGameApiClassLoaders.missing(), "26.3");
        VersionChecks.Result noLoader = VersionChecks.check(null, "26.3");

        assertTrue(missing.compatible());
        assertTrue(missing.message().contains("typedGameApi=absent (optional)"),
                missing.message());
        assertTrue(noLoader.compatible());
        assertTrue(noLoader.message().contains("compatible=true"), noLoader.message());
    }

    @Test
    void incompatibleTypedGameApiFailsTheCheck() {
        VersionChecks.Result result = VersionChecks.check(
                TestGameApiClassLoaders.withVersion("1.20"), "26.3");

        assertFalse(result.compatible());
        assertEquals(CompatibilityMatrix.Level.SUPPORTED, result.apiLevel());
        assertEquals(CompatibilityMatrix.Level.SUPPORTED, result.gameLevel());
        assertTrue(result.message().contains("typedGameApi=1.20 (incompatible)"),
                result.message());
        assertTrue(result.message().contains("compatible=false"), result.message());
    }

    @Test
    void unsupportedGameVersionFailsTheCheckWithoutThrowing() {
        VersionChecks.Result result = VersionChecks.check(
                TestGameApiClassLoaders.missing(), "1.20");

        assertFalse(result.compatible());
        assertEquals(CompatibilityMatrix.Level.SUPPORTED, result.apiLevel());
        assertEquals(CompatibilityMatrix.Level.UNSUPPORTED, result.gameLevel());
        assertTrue(result.message().contains("game=1.20 (UNSUPPORTED)"), result.message());
    }

    @Test
    void nullGameVersionFailsTheCheckWithoutThrowing() {
        VersionChecks.Result result = VersionChecks.check(null, null);

        assertFalse(result.compatible());
        assertEquals(CompatibilityMatrix.Level.UNSUPPORTED, result.gameLevel());
        assertNotNull(result.message());
    }

    @Test
    void messagesAreStableForTheSameInput() {
        String first = VersionChecks.check(TestGameApiClassLoaders.missing(), "26.3").message();
        String second = VersionChecks.check(TestGameApiClassLoaders.missing(), "26.3").message();

        assertEquals(first, second);
    }

    @Test
    void resultRejectsNullFields() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new VersionChecks.Result(true, null,
                        CompatibilityMatrix.Level.SUPPORTED, "message"));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new VersionChecks.Result(true, CompatibilityMatrix.Level.SUPPORTED,
                        null, "message"));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new VersionChecks.Result(true, CompatibilityMatrix.Level.SUPPORTED,
                        CompatibilityMatrix.Level.SUPPORTED, null));
    }
}
