package net.weaver.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code weaver.mod.json} descriptor.
 *
 * <p>{@code description}, {@code authors}, {@code license}, and {@code homepage}
 * are optional display fields, shown in the in-game mod list. {@code authors} is
 * a string, an array of strings, or an array of objects with a {@code name}.</p>
 *
 * <p>{@code compatibleVersions} (optional) is a version range: the versions of this
 * mod a client may have to join a server that has it, and the reverse. Without it
 * the join check needs the same version on both sides.</p>
 */
public record ModMetadata(
        int schemaVersion,
        String id,
        String version,
        String name,
        String mainClass,
        List<String> mixins,
        List<String> accessTransformers,
        Map<String, String> dependencies,
        Map<String, String> optional,
        Map<String, String> breaks,
        String side,
        String minecraft,
        String description,
        List<String> authors,
        String license,
        String homepage,
        String compatibleVersions) {

    public static final String FILE = "weaver.mod.json";

    public static ModMetadata parse(String source, byte[] json) {
        try {
            JsonElement parsed = JsonParser.parseString(new String(json, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new MetadataException("descriptor-not-an-object");
            }
            JsonObject root = parsed.getAsJsonObject();
            int schema = integer(root, "schemaVersion", 1);
            if (schema != 1 && schema != 2) {
                throw new MetadataException("bad-schema:" + schema);
            }
            String id = string(root, "id");
            if (id == null || !id.matches("[a-z0-9_-]+")) {
                throw new MetadataException("bad-id:" + id);
            }
            String version = string(root, "version");
            if (version == null || version.isBlank()) {
                version = "0.0.0";
            }
            String name = string(root, "name");
            if (name == null || name.isBlank()) {
                name = id;
            }
            String mainClass = string(root, "mainClass");
            if (mainClass == null || mainClass.isBlank()) {
                mainClass = entrypoint(root);
            }
            if (mainClass == null || mainClass.isBlank()) {
                throw new MetadataException("missing-mainClass");
            }
            List<String> mixins = strings(root, "mixins");
            List<String> accessTransformers = strings(root, "accessTransformers");
            Map<String, String> dependencies = stringMap(root, "dependencies");
            Map<String, String> optional = stringMap(root, "optional");
            Map<String, String> breaks = breaks(root.get("breaks"));
            String side = string(root, "side");
            if (side == null || side.isBlank()) {
                side = "both";
            }
            if (!"client".equals(side) && !"server".equals(side) && !"both".equals(side)) {
                throw new MetadataException("bad-side:" + side);
            }
            String minecraft = string(root, "minecraft");
            if (minecraft == null || minecraft.isBlank()) {
                minecraft = "26.3";
            }
            String description = string(root, "description");
            String license = string(root, "license");
            String homepage = string(root, "homepage");
            if (homepage == null) {
                JsonElement contact = root.get("contact");
                if (contact != null && contact.isJsonObject()) {
                    homepage = string(contact.getAsJsonObject(), "homepage");
                }
            }
            String compatibleVersions = string(root, "compatibleVersions");
            if (compatibleVersions == null) {
                compatibleVersions = "";
            }
            if (!compatibleVersions.isBlank() && !VersionRange.isValid(compatibleVersions)) {
                throw new MetadataException("bad-compatibleVersions:" + VersionRange.problem(compatibleVersions));
            }
            return new ModMetadata(schema, id, version, name, mainClass,
                    List.copyOf(mixins), List.copyOf(accessTransformers),
                    Map.copyOf(dependencies), Map.copyOf(optional), Map.copyOf(breaks),
                    side, minecraft, description == null ? "" : description, List.copyOf(authors(root.get("authors"))),
                    license == null ? "" : license, homepage == null ? "" : homepage, compatibleVersions.trim());
        } catch (JsonParseException e) {
            throw new MetadataException("json:" + e.getMessage());
        } catch (IllegalStateException | UnsupportedOperationException e) {
            throw new MetadataException("descriptor:" + e.getMessage());
        }
    }

    private static String entrypoint(JsonObject root) {
        JsonElement entrypoints = root.get("entrypoints");
        if (entrypoints == null || !entrypoints.isJsonObject()) {
            return null;
        }
        JsonElement main = entrypoints.getAsJsonObject().get("main");
        if (main == null) {
            return null;
        }
        if (main.isJsonPrimitive()) {
            return main.getAsString();
        }
        if (main.isJsonArray()) {
            JsonArray array = main.getAsJsonArray();
            if (!array.isEmpty() && array.get(0).isJsonPrimitive()) {
                return array.get(0).getAsString();
            }
        }
        return null;
    }

    private static String string(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static int integer(JsonObject root, String key, int fallback) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    private static List<String> strings(JsonObject root, String key) {
        JsonElement value = root.get(key);
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        if (value.isJsonPrimitive()) {
            out.add(value.getAsString());
            return out;
        }
        if (value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    out.add(item.getAsString());
                }
            }
        }
        return out;
    }

    private static List<String> authors(JsonElement value) {
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        if (value.isJsonPrimitive()) {
            out.add(value.getAsString());
            return out;
        }
        if (value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    out.add(item.getAsString());
                } else if (item.isJsonObject()) {
                    String name = string(item.getAsJsonObject(), "name");
                    if (name != null && !name.isBlank()) {
                        out.add(name);
                    }
                }
            }
        }
        return out;
    }

    private static Map<String, String> breaks(JsonElement value) {
        Map<String, String> out = new LinkedHashMap<>();
        if (value == null) {
            return out;
        }
        if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                out.put(entry.getKey(), entry.getValue().isJsonPrimitive()
                        ? entry.getValue().getAsString() : "*");
            }
            return out;
        }
        if (value.isJsonArray()) {
            for (JsonElement item : value.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    out.put(item.getAsString(), "*");
                }
            }
        }
        return out;
    }

    private static Map<String, String> stringMap(JsonObject root, String key) {
        JsonElement value = root.get(key);
        Map<String, String> out = new LinkedHashMap<>();
        if (value == null || !value.isJsonObject()) {
            return out;
        }
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                out.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return out;
    }
}
