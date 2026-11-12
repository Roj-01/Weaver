package net.weaver.api.game.event.support;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.world.RegisterStructureConversionsEvent;

/**
 * Adds mod entries to the pre-1.18.2 structure upgrade table. The vanilla
 * conversion record is package-private, so it is built reflectively.
 */
public final class StructureConversions {
    private static final String CONVERSION = "net.minecraft.util.datafix.fixes.StructuresBecomeConfiguredFix$Conversion";

    private StructureConversions() {
    }

    /** The vanilla table plus every mod entry; vanilla entries win on a clash. */
    public static Map<String, Object> extend(Map<String, ?> vanilla) {
        Map<String, Object> added = new HashMap<>();
        try {
            Class<?> type = Class.forName(CONVERSION, false, StructureConversions.class.getClassLoader());
            Method trivial = type.getDeclaredMethod("trivial", String.class);
            Method biomeMapped = type.getDeclaredMethod("biomeMapped", Map.class, String.class);
            trivial.setAccessible(true);
            biomeMapped.setAccessible(true);
            RegisterStructureConversionsEvent.Conversions factory = new RegisterStructureConversionsEvent.Conversions() {
                @Override
                public Object trivial(String newId) {
                    return invoke(trivial, newId);
                }

                @Override
                public Object biomeMapped(Map<List<String>, String> biomes, String fallback) {
                    return invoke(biomeMapped, biomes, fallback);
                }
            };
            GameEvents.post(new RegisterStructureConversionsEvent(added::put, factory), LogicalSide.SERVER);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            System.out.println("[weaver] STRUCTURE CONVERSIONS UNAVAILABLE reason=" + failure);
            return new HashMap<>(vanilla);
        }
        Map<String, Object> table = new HashMap<>(added);
        table.putAll(vanilla);
        if (!added.isEmpty()) {
            System.out.println("[weaver] STRUCTURE CONVERSIONS added=" + added.keySet());
        }
        return table;
    }

    private static Object invoke(Method method, Object... arguments) {
        try {
            return method.invoke(null, arguments);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("structure conversion: " + failure, failure);
        }
    }
}
