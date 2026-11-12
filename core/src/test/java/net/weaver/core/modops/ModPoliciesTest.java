package net.weaver.core.modops;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModPoliciesTest {

    @Test
    void defaultsToWarnAndAppliesBaseAndOverrideProperties() {
        Map<String, String> previous = capture();
        try {
            clear();
            assertEquals(ModPolicies.Mode.WARN, ModPolicies.fromSystemProperties().apiShadow());
            assertEquals(ModPolicies.Mode.WARN,
                    ModPolicies.fromSystemProperties().duplicateClasses());

            System.setProperty(ModPolicies.PROPERTY_BASE, "fatal");
            assertEquals(ModPolicies.Mode.FATAL, ModPolicies.fromSystemProperties().apiShadow());
            assertEquals(ModPolicies.Mode.FATAL,
                    ModPolicies.fromSystemProperties().duplicateClasses());

            System.setProperty(ModPolicies.PROPERTY_API_SHADOW, "WaRn");
            assertEquals(ModPolicies.Mode.WARN, ModPolicies.fromSystemProperties().apiShadow());
            assertEquals(ModPolicies.Mode.FATAL,
                    ModPolicies.fromSystemProperties().duplicateClasses());

            System.setProperty(ModPolicies.PROPERTY_BASE, "warn");
            System.setProperty(ModPolicies.PROPERTY_DUPLICATE_CLASSES, "FATAL");
            assertEquals(ModPolicies.Mode.WARN, ModPolicies.fromSystemProperties().apiShadow());
            assertEquals(ModPolicies.Mode.FATAL,
                    ModPolicies.fromSystemProperties().duplicateClasses());

            System.setProperty(ModPolicies.PROPERTY_BASE, "fatal");
            System.setProperty(ModPolicies.PROPERTY_API_SHADOW, "bogus");
            System.setProperty(ModPolicies.PROPERTY_DUPLICATE_CLASSES, "bogus");
            assertEquals(ModPolicies.Mode.WARN, ModPolicies.fromSystemProperties().apiShadow());
            assertEquals(ModPolicies.Mode.WARN,
                    ModPolicies.fromSystemProperties().duplicateClasses());
        } finally {
            restore(previous);
        }
    }

    @Test
    void factoriesReturnTheExpectedModes() {
        assertEquals(ModPolicies.Mode.WARN, ModPolicies.warn().apiShadow());
        assertEquals(ModPolicies.Mode.WARN, ModPolicies.warn().duplicateClasses());
        assertEquals(ModPolicies.Mode.FATAL, ModPolicies.fatal().apiShadow());
        assertEquals(ModPolicies.Mode.FATAL, ModPolicies.fatal().duplicateClasses());
        assertEquals(ModPolicies.Mode.FATAL, new ModPolicies(
                ModPolicies.Mode.FATAL, ModPolicies.Mode.WARN).apiShadow());
        assertEquals(ModPolicies.Mode.WARN, new ModPolicies(
                ModPolicies.Mode.FATAL, ModPolicies.Mode.WARN).duplicateClasses());
    }

    private static Map<String, String> capture() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : keys()) {
            values.put(key, System.getProperty(key));
        }
        return values;
    }

    private static void clear() {
        for (String key : keys()) {
            System.clearProperty(key);
        }
    }

    private static void restore(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    private static String[] keys() {
        return new String[]{
                ModPolicies.PROPERTY_BASE,
                ModPolicies.PROPERTY_API_SHADOW,
                ModPolicies.PROPERTY_DUPLICATE_CLASSES
        };
    }
}
