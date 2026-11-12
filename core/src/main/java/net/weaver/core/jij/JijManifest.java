package net.weaver.core.jij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for {@code META-INF/weaver/jij.json}.
 *
 * <p>The manifest schema is version {@value JijLayout#SCHEMA_VERSION}:
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 1,
 *   "firstWins": true,
 *   "jars": [
 *     {"file": "META-INF/weaver/jars/lib-1.0.0.jar",
 *      "id": "lib", "version": "1.0.0", "optional": false}
 *   ]
 * }
 * }</pre>
 *
 * <p>Entries are returned in manifest order. Duplicate ids are preserved; the
 * scanner reports them separately.
 */
public final class JijManifest {

    private JijManifest() {
    }

    /**
     * Parses a jar-in-jar manifest.
     *
     * @param json the manifest bytes, UTF-8 encoded
     * @return the declared nested jars in manifest order
     * @throws IllegalArgumentException if the JSON is malformed or an entry is
     *         unsafe, incomplete, or outside {@link JijLayout#ROOT}
     */
    public static List<NestedJar> parse(byte[] json) {
        if (json == null) {
            throw new IllegalArgumentException("jij manifest is missing");
        }
        String text = new String(json, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(text);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("jij manifest is not valid JSON: " + e.getMessage(), e);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("jij manifest must be a JSON object");
        }
        JsonObject root = parsed.getAsJsonObject();
        int schema = integer(root, "schemaVersion", JijLayout.SCHEMA_VERSION);
        if (schema != JijLayout.SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported jij schemaVersion: " + schema);
        }
        JsonElement firstWins = root.get("firstWins");
        if (firstWins != null && (!firstWins.isJsonPrimitive()
                || !firstWins.getAsJsonPrimitive().isBoolean())) {
            throw new IllegalArgumentException("jij firstWins must be a boolean");
        }
        JsonElement jars = root.get("jars");
        if (jars == null || !jars.isJsonArray()) {
            throw new IllegalArgumentException("jij manifest must contain a jars array");
        }
        JsonArray array = jars.getAsJsonArray();
        List<NestedJar> nested = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            JsonElement element = array.get(index);
            String where = "jij jar[" + index + "]";
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(where + " must be a JSON object");
            }
            JsonObject jar = element.getAsJsonObject();
            String file = string(jar, "file", where);
            String id = string(jar, "id", where);
            String version = string(jar, "version", where);
            if (!id.matches("[a-z0-9_-]+")) {
                throw new IllegalArgumentException(where + " has an invalid id: " + id);
            }
            if (version.isBlank()) {
                throw new IllegalArgumentException(where + " has a blank version");
            }
            boolean optional = bool(jar, "optional", false, where);
            JijScanner.requireSafeEntry(file);
            if (!file.startsWith(JijLayout.ROOT)) {
                throw new IllegalArgumentException(where + " is outside " + JijLayout.ROOT + ": " + file);
            }
            nested.add(new NestedJar(file, id, version, optional));
        }
        return List.copyOf(nested);
    }

    private static String string(JsonObject object, String key, String where) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(where + " must have a string " + key);
        }
        String text = value.getAsString();
        if (text.isBlank()) {
            throw new IllegalArgumentException(where + " has a blank " + key);
        }
        return text;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        if (value == null) {
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("jij " + key + " must be a number");
        }
        return value.getAsInt();
    }

    private static boolean bool(JsonObject object, String key, boolean fallback, String where) {
        JsonElement value = object.get(key);
        if (value == null) {
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(where + " must have a boolean " + key);
        }
        return value.getAsBoolean();
    }
}
