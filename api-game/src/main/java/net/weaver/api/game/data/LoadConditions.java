package net.weaver.api.game.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.weaver.api.WeaverMods;

/**
 * Conditions on data and resource files: a file whose conditions fail is not
 * loaded at all, as if it were not in the pack. This is how a mod ships recipes,
 * loot, tags, or worldgen that only apply when another mod is present.
 *
 * <pre>{@code
 * {
 *   "weaver:conditions": [ { "type": "weaver:mod_loaded", "modid": "biomes" } ],
 *   "type": "minecraft:crafting_shaped", ...
 * }
 * }</pre>
 *
 * <p>Files written for other loaders work unchanged: {@code neoforge:conditions}
 * and {@code forge:conditions} (entries with {@code "type"}) and
 * {@code fabric:load_conditions} (entries with {@code "condition"}) are read the
 * same way. All conditions in the list must hold. Built-in condition types, under
 * any of the {@code weaver}, {@code neoforge}, {@code forge}, {@code fabric}, or
 * {@code c} namespaces:</p>
 *
 * <ul>
 *   <li>{@code mod_loaded} ({@code "modid"}), {@code all_mods_loaded} and
 *       {@code any_mods_loaded} ({@code "values"}): Weaver mods, plus
 *       {@code minecraft} and {@code weaver};</li>
 *   <li>{@code not} ({@code "value"}), {@code and} and {@code or} ({@code "values"});</li>
 *   <li>{@code true}, {@code false};</li>
 *   <li>{@code item_exists} ({@code "item"}), and {@code registered} /
 *       {@code registry_contains} ({@code "registry"} with {@code "value"} or
 *       {@code "values"}).</li>
 * </ul>
 *
 * <p>Mods add types with {@link #register}. An unknown type keeps the file and is
 * logged once. Files from the vanilla pack are not checked.</p>
 */
public final class LoadConditions {
    private static final List<String> KEYS = List.of("weaver:conditions", "neoforge:conditions", "forge:conditions",
            "fabric:load_conditions");
    private static final List<String> NAMESPACES = List.of("weaver", "neoforge", "forge", "fabric", "c");
    private static final Map<String, Predicate<JsonObject>> TYPES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> REPORTED = new ConcurrentHashMap<>();
    private static final byte[][] MARKERS = {
            "conditions\"".getBytes(StandardCharsets.US_ASCII), "load_conditions\"".getBytes(StandardCharsets.US_ASCII)};

    static {
        builtIn("mod_loaded", condition -> modLoaded(string(condition, "modid")));
        builtIn("all_mods_loaded", condition -> {
            for (JsonElement value : array(condition, "values")) {
                if (!modLoaded(value.getAsString())) {
                    return false;
                }
            }
            return true;
        });
        builtIn("any_mods_loaded", condition -> {
            for (JsonElement value : array(condition, "values")) {
                if (modLoaded(value.getAsString())) {
                    return true;
                }
            }
            return false;
        });
        // not, and, or are three-valued (see evaluate); these entries only mark the types as known.
        builtIn("not", condition -> true);
        builtIn("and", condition -> true);
        builtIn("or", condition -> true);
        builtIn("true", condition -> true);
        builtIn("false", condition -> false);
        builtIn("item_exists", condition -> BuiltInRegistries.ITEM.containsKey(Identifier.parse(string(condition, "item"))));
        Predicate<JsonObject> registered = condition -> {
            Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(Identifier.parse(string(condition, "registry")));
            if (registry == null) {
                // A data-driven registry (biomes, structures, ...) is not loaded when files are listed.
                throw new IllegalArgumentException("registry " + string(condition, "registry")
                        + " is not a built-in registry; only built-in registries can be checked");
            }
            if (condition.has("value")) {
                return registry.containsKey(Identifier.parse(string(condition, "value")));
            }
            for (JsonElement value : array(condition, "values")) {
                if (!registry.containsKey(Identifier.parse(value.getAsString()))) {
                    return false;
                }
            }
            return true;
        };
        builtIn("registered", registered);
        builtIn("registry_contains", registered);
    }

    private LoadConditions() {
    }

    private static void builtIn(String path, Predicate<JsonObject> test) {
        for (String namespace : NAMESPACES) {
            TYPES.put(namespace + ":" + path, test);
        }
    }

    /**
     * Adds a condition type. The condition object (with its {@code type} and its
     * own fields) is passed to {@code test}.
     *
     * @param type the full type id, such as {@code examplemod:feature_enabled}
     * @param test whether the condition holds
     */
    public static void register(String type, Predicate<JsonObject> test) {
        TYPES.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(test, "test"));
    }

    /** Whether every condition of a file's root object holds (true when it has none). */
    public static boolean shouldLoad(JsonObject root) {
        for (String key : KEYS) {
            JsonElement conditions = root.get(key);
            if (conditions == null) {
                continue;
            }
            if (conditions.isJsonObject()) {
                if (!test(conditions.getAsJsonObject())) {
                    return false;
                }
            } else if (conditions.isJsonArray()) {
                for (JsonElement condition : conditions.getAsJsonArray()) {
                    if (condition.isJsonObject() && !test(condition.getAsJsonObject())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Whether one condition object holds. An unknown or invalid condition keeps the
     * file (true), also inside {@code not}, {@code and}, and {@code or}.
     */
    public static boolean test(JsonObject condition) {
        return evaluate(condition) != Boolean.FALSE;
    }

    /** True, false, or null for a condition that is unknown or invalid (and keeps the file). */
    private static Boolean evaluate(JsonObject condition) {
        String type;
        try {
            type = condition.has("type") ? string(condition, "type")
                    : condition.has("condition") ? string(condition, "condition") : null;
        } catch (RuntimeException invalid) {
            type = null;
        }
        if (type == null) {
            report("<none>", "a condition with no type; the file is kept");
            return null;
        }
        if (type.indexOf(':') < 0) {
            type = "weaver:" + type;
        }
        Predicate<JsonObject> predicate = TYPES.get(type);
        if (predicate == null) {
            report(type, "unknown condition type; the file is kept");
            return null;
        }
        try {
            String path = type.substring(type.indexOf(':') + 1);
            if (NAMESPACES.contains(type.substring(0, type.indexOf(':')))) {
                switch (path) {
                    case "not" -> {
                        Boolean inner = evaluate(condition.getAsJsonObject("value"));
                        return inner == null ? null : !inner;
                    }
                    case "and", "or" -> {
                        boolean unknown = false;
                        for (JsonElement value : array(condition, "values")) {
                            Boolean part = evaluate(value.getAsJsonObject());
                            if (part == null) {
                                unknown = true;
                            } else if (part == path.equals("or")) {
                                return part;
                            }
                        }
                        return unknown ? null : !path.equals("or");
                    }
                    default -> {
                    }
                }
            }
            return predicate.test(condition);
        } catch (RuntimeException invalid) {
            report(type, "invalid condition: " + invalid.getMessage() + "; the file is kept");
            return null;
        }
    }

    /**
     * Removes from a resource listing every JSON file whose conditions fail. Called
     * by the loader's resource listing hook; files from the vanilla pack and files
     * without a conditions key are kept without parsing.
     *
     * @return the number of files removed
     */
    public static int filter(Map<Identifier, Resource> resources) {
        int removed = 0;
        Iterator<Map.Entry<Identifier, Resource>> entries = resources.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Identifier, Resource> entry = entries.next();
            if (!keep(entry.getKey(), entry.getValue())) {
                entries.remove();
                removed++;
            }
        }
        return removed;
    }

    /** As {@link #filter(Map)} for stacked listings (tags): each failing file leaves its stack. */
    public static int filterStacks(Map<Identifier, List<Resource>> stacks) {
        int removed = 0;
        Iterator<Map.Entry<Identifier, List<Resource>>> entries = stacks.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Identifier, List<Resource>> entry = entries.next();
            List<Resource> stack = entry.getValue();
            int before = stack.size();
            stack.removeIf(resource -> !keep(entry.getKey(), resource));
            removed += before - stack.size();
            if (stack.isEmpty()) {
                entries.remove();
            }
        }
        return removed;
    }

    private static boolean keep(Identifier id, Resource resource) {
        if (!id.getPath().endsWith(".json") || "vanilla".equals(resource.sourcePackId())) {
            return true;
        }
        byte[] bytes;
        try (InputStream in = resource.open()) {
            bytes = in.readAllBytes();
        } catch (IOException unreadable) {
            return true;
        }
        if (!containsMarker(bytes)) {
            return true;
        }
        try {
            JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            boolean load = !root.isJsonObject() || shouldLoad(root.getAsJsonObject());
            if (!load) {
                System.out.println("[weaver] DATA CONDITION SKIPPED file=" + id + " pack=" + resource.sourcePackId());
            }
            return load;
        } catch (RuntimeException malformed) {
            return true;
        }
    }

    private static boolean containsMarker(byte[] bytes) {
        for (byte[] marker : MARKERS) {
            outer:
            for (int i = 0; i + marker.length <= bytes.length; i++) {
                for (int j = 0; j < marker.length; j++) {
                    if (bytes[i + j] != marker[j]) {
                        continue outer;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static boolean modLoaded(String id) {
        return "minecraft".equals(id) || "weaver".equals(id) || WeaverMods.find(id).isPresent();
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("missing \"" + key + "\"");
        }
        return value.getAsString();
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("missing array \"" + key + "\"");
        }
        return value.getAsJsonArray();
    }

    private static void report(String type, String reason) {
        if (REPORTED.putIfAbsent(type + reason, Boolean.TRUE) == null) {
            System.out.println("[weaver] DATA CONDITION type=" + type + " reason=" + reason);
        }
    }
}
