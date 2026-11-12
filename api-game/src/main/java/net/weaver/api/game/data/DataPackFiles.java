package net.weaver.api.game.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import net.weaver.api.registry.DataPackSnapshot;
import net.weaver.api.registry.RegistryKey;

/**
 * Turns the Weaver files of one game data pack into a {@link DataPackSnapshot}.
 * It applies the same layout and JSON rules as the directory loader in core
 * ({@code DataPackRepository}), but takes files already listed from the
 * game's resource system, keyed by their path inside {@code data/}:
 *
 * <pre>
 * &lt;packNamespace&gt;/weaver/registry/&lt;registryNamespace&gt;/&lt;registryPath&gt;/&lt;entryPath&gt;.json
 * &lt;packNamespace&gt;/weaver/tags/&lt;registryNamespace&gt;/&lt;registryPath&gt;/&lt;tagPath&gt;.json
 * </pre>
 *
 * <p>An entry belongs to registry {@code registryNamespace:registryPath} with
 * id {@code registryNamespace:entryPath}; a tag is keyed by
 * {@code packNamespace:tagPath}. Problems are collected instead of thrown.</p>
 */
public final class DataPackFiles {
    /** Files per pack beyond which the rest are skipped, as in the directory loader. */
    public static final int MAX_FILES_PER_PACK = 4096;
    /** Bytes per file beyond which it is skipped. */
    public static final int MAX_FILE_BYTES = 1_048_576;

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_.-]+(/[a-z0-9_.-]+)*");

    private DataPackFiles() {
    }

    /** One pack's snapshot and the problems found while reading it. */
    public record Result(DataPackSnapshot snapshot, List<String> problems) {
        public Result {
            Objects.requireNonNull(snapshot, "snapshot");
            problems = List.copyOf(problems);
        }
    }

    /**
     * Reads one pack.
     *
     * @param packId   the pack id, used in problems and as the snapshot id
     * @param priority the pack priority; later packs override earlier ones
     * @param files    path inside {@code data/} to file contents, in the
     *                 order the files should be applied
     */
    public static Result read(String packId, int priority, Map<String, String> files) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(files, "files");
        DataPackSnapshot.Builder builder = DataPackSnapshot.builder(packId, priority);
        List<String> problems = new ArrayList<>();
        int count = 0;
        for (Map.Entry<String, String> file : files.entrySet()) {
            if (++count > MAX_FILES_PER_PACK) {
                problems.add(packId + ": more than " + MAX_FILES_PER_PACK + " Weaver files; the rest were skipped");
                break;
            }
            readFile(packId, priority, file.getKey(), file.getValue(), builder, problems);
        }
        return new Result(builder.build(), problems);
    }

    private static void readFile(String packId, int priority, String path, String content,
                                 DataPackSnapshot.Builder builder, List<String> problems) {
        String where = packId + "/data/" + path;
        if (!path.endsWith(".json")) {
            return;
        }
        if (content.length() > MAX_FILE_BYTES) {
            problems.add(where + ": file is larger than " + MAX_FILE_BYTES + " bytes");
            return;
        }
        String[] parts = path.substring(0, path.length() - ".json".length()).split("/", 6);
        if (parts.length < 6 || !"weaver".equals(parts[1])) {
            return;
        }
        String packNamespace = parts[0];
        String kind = parts[2];
        String registryNamespace = parts[3];
        String registryPath = parts[4];
        String idPath = parts[5];
        if (!NAMESPACE.matcher(packNamespace).matches() || !NAMESPACE.matcher(registryNamespace).matches()
                || !SEGMENT.matcher(registryPath).matches() || !PATH.matcher(idPath).matches()) {
            problems.add(where + ": invalid path");
            return;
        }
        String registryId = registryNamespace + ":" + registryPath;
        JsonObject root = parseObject(where, content, problems);
        if (root == null) {
            return;
        }
        if ("registry".equals(kind)) {
            builder.entry(registryId, idPath, toMap(root));
        } else if ("tags".equals(kind)) {
            DataPackSnapshot.TagDeclaration declaration = tagDeclaration(where, root, priority, problems);
            if (declaration != null) {
                builder.tag(registryId, packNamespace + ":" + idPath, declaration);
            }
        }
    }

    private static JsonObject parseObject(String where, String content, List<String> problems) {
        try {
            JsonElement element = JsonParser.parseString(content);
            if (element == null || !element.isJsonObject()) {
                problems.add(where + ": root must be a JSON object");
                return null;
            }
            return element.getAsJsonObject();
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            problems.add(where + ": " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            return null;
        }
    }

    private static DataPackSnapshot.TagDeclaration tagDeclaration(String where, JsonObject object, int packPriority,
                                                                  List<String> problems) {
        int priority = packPriority;
        JsonElement priorityElement = object.get("priority");
        if (priorityElement != null && !priorityElement.isJsonNull()) {
            Double number = number(priorityElement);
            if (number == null || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                problems.add(where + ": priority must be an integer");
            } else {
                priority = number.intValue();
            }
        }
        boolean replace = false;
        JsonElement replaceElement = object.get("replace");
        if (replaceElement != null && !replaceElement.isJsonNull()) {
            if (replaceElement.isJsonPrimitive() && replaceElement.getAsJsonPrimitive().isBoolean()) {
                replace = replaceElement.getAsBoolean();
            } else {
                problems.add(where + ": replace must be a boolean");
            }
        }
        List<DataPackSnapshot.TagValue> values = new ArrayList<>();
        JsonElement valuesElement = object.get("values");
        if (valuesElement != null && !valuesElement.isJsonNull()) {
            if (!valuesElement.isJsonArray()) {
                problems.add(where + ": values must be an array");
                return null;
            }
            for (JsonElement element : valuesElement.getAsJsonArray()) {
                DataPackSnapshot.TagValue value = tagValue(where, element, problems);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        return new DataPackSnapshot.TagDeclaration(values, priority, replace);
    }

    private static DataPackSnapshot.TagValue tagValue(String where, JsonElement element, List<String> problems) {
        String raw;
        boolean required = false;
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            raw = element.getAsString();
        } else if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement id = object.get("id");
            if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) {
                problems.add(where + ": tag value object needs a string id");
                return null;
            }
            raw = id.getAsString();
            JsonElement requiredElement = object.get("required");
            if (requiredElement != null && !requiredElement.isJsonNull()) {
                if (!requiredElement.isJsonPrimitive() || !requiredElement.getAsJsonPrimitive().isBoolean()) {
                    problems.add(where + ": tag value required must be a boolean");
                    return null;
                }
                required = requiredElement.getAsBoolean();
            }
        } else {
            problems.add(where + ": tag value must be a string or object");
            return null;
        }
        try {
            boolean reference = raw.startsWith("#");
            String canonical = RegistryKey.parse(reference ? raw.substring(1) : raw).id();
            return reference ? DataPackSnapshot.TagValue.reference(canonical, required)
                    : DataPackSnapshot.TagValue.value(canonical, required);
        } catch (IllegalArgumentException invalid) {
            problems.add(where + ": invalid tag value '" + raw + "': " + invalid.getMessage());
            return null;
        }
    }

    private static Double number(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsDouble();
        }
        return null;
    }

    /**
     * Converts a JSON object the way the directory loader does: nested objects
     * become maps, arrays lists, numbers {@code Double}, and JSON null Java
     * null. The result is read-only.
     */
    public static Map<String, Object> toMap(JsonObject object) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> member : object.entrySet()) {
            map.put(member.getKey(), toValue(member.getValue()));
        }
        return Collections.unmodifiableMap(map);
    }

    private static Object toValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            return toMap(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            List<Object> list = new ArrayList<>(array.size());
            for (JsonElement item : array) {
                list.add(toValue(item));
            }
            return Collections.unmodifiableList(list);
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsDouble();
        }
        return primitive.getAsString();
    }
}
