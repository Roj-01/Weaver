package net.weaver.api.game.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * Shared reading of typed data pack files ({@code {"type": "<id>", ...}}), used
 * by loot modifiers, biome modifiers, and data maps: JSON reading, the
 * {@code enabled} switch, type lookup, codec parsing, and one consistent
 * "skipped" message per bad file.
 */
public final class ModifierFiles {
    private ModifierFiles() {
    }

    /** One parsed file: its id, its JSON object, and the value its type's codec read. */
    public record Parsed<T>(Identifier id, JsonObject json, T value) {
    }

    /**
     * Parses {@code files} in id order with the codec of each file's
     * {@code type}, then {@code finish} (which may read more fields and may
     * throw). A file with {@code "enabled": false} is left out; a file that
     * fails is skipped with {@code [weaver] <label> SKIPPED id=... reason=...}.
     */
    public static <T, R> List<R> parse(String label, Map<Identifier, JsonElement> files,
            Map<Identifier, MapCodec<? extends T>> types, DynamicOps<JsonElement> ops,
            Function<Parsed<T>, R> finish, Consumer<String> warn) {
        List<R> parsed = new ArrayList<>();
        for (Map.Entry<Identifier, JsonElement> file : new TreeMap<>(files).entrySet()) {
            try {
                JsonElement json = file.getValue();
                if (!json.isJsonObject()) {
                    throw new IllegalArgumentException("not a JSON object");
                }
                JsonObject object = json.getAsJsonObject();
                if (object.has("enabled") && !object.get("enabled").getAsBoolean()) {
                    continue;
                }
                if (!object.has("type")) {
                    throw new IllegalArgumentException("missing type");
                }
                Identifier type = Identifier.parse(object.get("type").getAsString());
                MapCodec<? extends T> codec = types.get(type);
                if (codec == null) {
                    throw new IllegalArgumentException("unknown type " + type);
                }
                T value = orThrow(codec.codec().parse(ops, object));
                R result = finish.apply(new Parsed<>(file.getKey(), object, value));
                if (result != null) {
                    parsed.add(result);
                }
            } catch (RuntimeException | LinkageError | StackOverflowError failure) {
                warn.accept("[weaver] " + label + " SKIPPED id=" + file.getKey() + " reason=" + failure.getMessage());
            }
        }
        return List.copyOf(parsed);
    }

    /** The value of {@code result}, or an {@link IllegalArgumentException} with its error message. */
    public static <T> T orThrow(DataResult<T> result) {
        return result.getOrThrow(message -> new IllegalArgumentException(message));
    }

    /** Reads one resource as JSON. */
    public static JsonElement readJson(Resource resource) throws IOException {
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader);
        }
    }
}
