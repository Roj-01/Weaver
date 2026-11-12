package net.weaver.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Constants that mods add to existing enums, vanilla ones included (a new
 * {@code Rarity}, a new arm pose). A mod lists them in
 * {@code META-INF/enumextensions.json}:
 *
 * <pre>{@code
 * {
 *   "entries": [
 *     {
 *       "enum": "net/minecraft/world/item/Rarity",
 *       "name": "EXAMPLEMOD_MYTHIC",
 *       "constructor": "(ILjava/lang/String;Lnet/minecraft/ChatFormatting;)V",
 *       "parameters": {"class": "com/example/ExampleEnums", "method": "mythic"}
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code constructor} is the descriptor of the enum's constructor without
 *   the name and ordinal every enum constructor starts with.</li>
 *   <li>{@code parameters} names a static method of the mod that returns
 *   the constructor's arguments as an {@code Object[]} (boxed for primitives). It
 *   may take an {@code int}: the new constant's ordinal, for enums whose ids must
 *   follow on.</li>
 *   <li>{@code name} must start with the mod id in upper case and an underscore,
 *   so two mods never clash.</li>
 * </ul>
 *
 * <p>Weaver adds the constants when the enum loads: they are in
 * {@code values()}, {@code valueOf}, and everything vanilla builds from them
 * (codecs, id maps, {@code EnumMap}). The method runs while the enum
 * initializes, so it must not use the enum itself. Mods get their constant with
 * {@link #get}.</p>
 */
public final class EnumExtensions {
    /** One added constant, as the loader read it. Integration type. */
    @net.weaver.api.annotation.Internal
    public record Entry(String modId, String enumName, String constant, String factoryClass,
                        String factoryMethod, ClassLoader loader) {
        public Entry {
            Objects.requireNonNull(modId, "modId");
            Objects.requireNonNull(enumName, "enumName");
            Objects.requireNonNull(constant, "constant");
            Objects.requireNonNull(factoryClass, "factoryClass");
            Objects.requireNonNull(factoryMethod, "factoryMethod");
        }
    }

    private static volatile Map<String, Entry> entries = Map.of();

    private EnumExtensions() {
    }

    /** Installs the entries of every mod. Called by the loader before the game loads; mod code must not call it. */
    @net.weaver.api.annotation.Internal
    public static synchronized void install(List<Entry> list) {
        Map<String, Entry> map = new HashMap<>();
        for (Entry entry : list) {
            map.put(key(entry.enumName(), entry.constant()), entry);
        }
        entries = Map.copyOf(map);
    }

    /**
     * The constant {@code name} of {@code type}, a vanilla constant or one a mod
     * added.
     *
     * @throws IllegalArgumentException when the enum has no such constant
     */
    public static <E extends Enum<E>> E get(Class<E> type, String name) {
        return Enum.valueOf(type, name);
    }

    /** Whether a mod asked for constant {@code name} in the enum {@code enumName} (internal or dotted name). */
    public static boolean declared(String enumName, String name) {
        return entries.containsKey(key(enumName, name));
    }

    /**
     * The constructor arguments of an added constant, from the mod's method.
     * Called by the transformed enum initializer; mod code must not call it.
     */
    @net.weaver.api.annotation.Internal
    public static Object[] arguments(String enumName, String name, int ordinal) {
        Entry entry = entries.get(key(enumName, name));
        if (entry == null) {
            throw new IllegalStateException("no enum extension " + enumName + "." + name);
        }
        try {
            ClassLoader loader = entry.loader() != null ? entry.loader() : EnumExtensions.class.getClassLoader();
            Class<?> owner = Class.forName(entry.factoryClass().replace('/', '.'), true, loader);
            Object result;
            Method withOrdinal = find(owner, entry.factoryMethod(), int.class);
            if (withOrdinal != null) {
                result = withOrdinal.invoke(null, ordinal);
            } else {
                Method plain = find(owner, entry.factoryMethod());
                if (plain == null) {
                    throw new IllegalStateException("no static method " + entry.factoryMethod()
                            + "() or " + entry.factoryMethod() + "(int) in " + owner.getName());
                }
                result = plain.invoke(null);
            }
            if (!(result instanceof Object[] arguments)) {
                throw new IllegalStateException(owner.getName() + "." + entry.factoryMethod() + " did not return Object[]");
            }
            return arguments;
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("enum extension " + enumName + "." + name + " (mod " + entry.modId()
                    + ") failed: " + failure.getCause(), failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("enum extension " + enumName + "." + name + " (mod " + entry.modId()
                    + ") failed: " + failure, failure);
        }
    }

    private static Method find(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            if (!Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            // The class may be package-private; the method only has to be static.
            method.trySetAccessible();
            return method;
        } catch (NoSuchMethodException missing) {
            return null;
        }
    }

    private static String key(String enumName, String name) {
        return enumName.replace('.', '/') + "#" + name;
    }
}
