package net.weaver.core.version;

import net.weaver.api.WeaverApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaverApiVersionTest {

    @Test
    void keepsTheBinaryApiVersionAt21() {
        assertEquals("2.1.0", WeaverApi.VERSION);
        assertEquals(2, WeaverApi.API_MAJOR);
        assertEquals(1, WeaverApi.API_MINOR);
        assertEquals("2.1.0", WeaverApi.MIN_SUPPORTED_API);
        assertEquals("2.2", WeaverApi.FEATURE_LEVEL);
        assertEquals("26.3", WeaverApi.GAME_VERSION);
        assertEquals("26.3", WeaverApi.HOOK_TARGET);
    }

    @Test
    void acceptsSameMajorVersionsAtOrAboveTheMinimum() {
        assertTrue(WeaverApi.isCompatibleApi("2.1.0"));
        assertTrue(WeaverApi.isCompatibleApi("2.1.9"));
        assertTrue(WeaverApi.isCompatibleApi("2.5.0"));
        assertTrue(WeaverApi.isCompatibleApi("v2.5.0-rc.1"));
    }

    @Test
    void rejectsOtherMajorsOlderMinorsAndGarbage() {
        assertFalse(WeaverApi.isCompatibleApi("3.0.0"));
        assertFalse(WeaverApi.isCompatibleApi("2.0.0"));
        assertFalse(WeaverApi.isCompatibleApi("1.9.9"));
        assertFalse(WeaverApi.isCompatibleApi("garbage"));
        assertFalse(WeaverApi.isCompatibleApi(""));
        assertFalse(WeaverApi.isCompatibleApi(null));
    }

    @Test
    void gameCompatibilityUsesTheSupportedTarget() {
        assertTrue(WeaverApi.isCompatibleGame("26.3"));
        assertTrue(WeaverApi.isCompatibleGame("v26.3"));
        assertTrue(WeaverApi.isCompatibleGame("26.3.0"));
        assertFalse(WeaverApi.isCompatibleGame("1.20"));
        assertFalse(WeaverApi.isCompatibleGame("26.4"));
        assertFalse(WeaverApi.isCompatibleGame("garbage"));
        assertFalse(WeaverApi.isCompatibleGame(null));
    }

    @Test
    void requireCompatibleApiPassesOrThrowsAClearError() {
        assertDoesNotThrow(() -> WeaverApi.requireCompatibleApi("2.5.0"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> WeaverApi.requireCompatibleApi("3.0.0"));
        assertTrue(failure.getMessage().contains("3.0.0"), failure.getMessage());
        assertTrue(failure.getMessage().contains("not compatible"), failure.getMessage());
        assertTrue(failure.getMessage().contains(WeaverApi.MIN_SUPPORTED_API),
                failure.getMessage());
    }

    @Test
    void requireCompatibleGamePassesOrThrowsAClearError() {
        assertDoesNotThrow(() -> WeaverApi.requireCompatibleGame("26.3"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> WeaverApi.requireCompatibleGame("1.20"));
        assertTrue(failure.getMessage().contains("1.20"), failure.getMessage());
        assertTrue(failure.getMessage().contains("not supported"), failure.getMessage());
        assertTrue(failure.getMessage().contains(WeaverApi.GAME_VERSION),
                failure.getMessage());
    }

    @Test
    void gameApiReflectionHandlesMissingAndCompatibleClasses() {
        assertFalse(WeaverApi.isGameApiCompatible(null));
        assertFalse(WeaverApi.isGameApiCompatible(TestGameApiClassLoaders.missing()));
        assertTrue(WeaverApi.isGameApiCompatible(
                TestGameApiClassLoaders.withVersion("26.3")));
        assertFalse(WeaverApi.isGameApiCompatible(
                TestGameApiClassLoaders.withVersion("1.20")));
    }

    @Test
    void stabilityPolicyStatesTheAdditiveRules() {
        String policy = WeaverApi.stabilityPolicy();

        assertTrue(policy.contains("additive-first"), policy);
        assertTrue(policy.contains("minor"), policy);
        assertTrue(policy.contains("major"), policy);
        assertTrue(policy.contains("deprecated"), policy);
        assertTrue(policy.contains("Object-typed"), policy);
    }
}
