package net.weaver.minimap.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads and writes waypoints as UTF-8 JSON.
 *
 * <p>The file is a JSON object with a numeric {@code version} and a
 * {@code waypoints} array. Version {@code 1} entries contain {@code id},
 * {@code name}, {@code dimension}, {@code x}, {@code y}, {@code z},
 * {@code color}, and {@code visible}; they load with version 2 defaults. Version
 * {@code 2} adds {@code icon}, {@code group}, {@code temporary}, {@code death},
 * {@code createdAt}, and {@code updatedAt}. Any other entry field is retained in
 * the waypoint extension map and written back unchanged, so a future version
 * can round-trip its own fields through this store.</p>
 *
 * <p>A missing or blank file is treated as an empty list, and a missing
 * {@code waypoints} member is also treated as an empty list. Other malformed
 * content is rejected with an {@link IOException}.</p>
 *
 * <p>Writes create parent directories, write a sibling temporary file, and
 * replace the target with an atomic move when the file system supports one.
 * The supplied list order is preserved. Instances are stateless and safe to
 * share between threads.</p>
 */
public final class WaypointStore
{
    private static final int VERSION = 2;
    private static final int MIN_VERSION = 1;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path file;

    /**
     * Creates a store for one file.
     *
     * @param file the backing file, never {@code null}
     * @throws NullPointerException when {@code file} is {@code null}
     */
    public WaypointStore(Path file)
    {
        this.file = Objects.requireNonNull(file, "file");
    }

    /** The backing file, never {@code null}. */
    public Path file()
    {
        return file;
    }

    /**
     * Loads all waypoints in file order.
     *
     * @return an immutable list, empty when the file is absent or blank
     * @throws IOException when the file cannot be read or is malformed
     */
    public List<Waypoint> load() throws IOException
    {
        if (Files.notExists(file))
        {
            return List.of();
        }
        String json;
        try
        {
            json = Files.readString(file, StandardCharsets.UTF_8);
        }
        catch (IOException failure)
        {
            throw new IOException("cannot read waypoint file " + file, failure);
        }
        return fromJson(json);
    }

    /**
     * Parses waypoints from a JSON string using the same format as the file.
     *
     * @param json the JSON text, never {@code null}
     * @return an immutable list, empty when the text is blank
     * @throws NullPointerException when {@code json} is {@code null}
     * @throws IOException          when the text is malformed
     */
    public List<Waypoint> fromJson(String json) throws IOException
    {
        Objects.requireNonNull(json, "json");
        String text = stripBom(json);
        if (text.isBlank())
        {
            return List.of();
        }
        JsonElement parsed;
        try
        {
            parsed = JsonParser.parseString(text);
        }
        catch (RuntimeException failure)
        {
            throw corrupt("invalid JSON: " + reason(failure), failure);
        }
        if (!parsed.isJsonObject())
        {
            throw corrupt("root must be a JSON object", null);
        }
        JsonObject root = parsed.getAsJsonObject();
        int version = requireInt(root, "version", "\"version\"");
        if (version < MIN_VERSION || version > VERSION)
        {
            throw corrupt("unsupported version " + version, null);
        }
        JsonElement waypointsElement = root.get("waypoints");
        if (waypointsElement == null || waypointsElement.isJsonNull())
        {
            return List.of();
        }
        if (!waypointsElement.isJsonArray())
        {
            throw corrupt("\"waypoints\" must be an array", null);
        }
        List<Waypoint> waypoints = new ArrayList<>();
        int index = 0;
        for (JsonElement element : waypointsElement.getAsJsonArray())
        {
            if (!element.isJsonObject())
            {
                throw corrupt("waypoint " + index + " must be a JSON object", null);
            }
            waypoints.add(parseWaypoint(element.getAsJsonObject(), index, version));
            index++;
        }
        return List.copyOf(waypoints);
    }

    /**
     * Serializes waypoints to a version 2 JSON string.
     *
     * @param waypoints the waypoints to write, never {@code null}
     * @return the JSON text, never {@code null}
     * @throws NullPointerException when the list or an entry is {@code null}
     */
    public String toJson(List<Waypoint> waypoints)
    {
        Objects.requireNonNull(waypoints, "waypoints");
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonArray array = new JsonArray();
        for (Waypoint waypoint : waypoints)
        {
            Objects.requireNonNull(waypoint, "waypoint");
            array.add(toJsonObject(waypoint));
        }
        root.add("waypoints", array);
        return GSON.toJson(root);
    }

    /**
     * Saves all waypoints in the supplied order.
     *
     * @param waypoints the waypoints to write, never {@code null}
     * @throws NullPointerException when the list or an entry is {@code null}
     * @throws IOException          when the file cannot be written
     */
    public void save(List<Waypoint> waypoints) throws IOException
    {
        writeAtomically(toJson(waypoints) + "\n");
    }

    private JsonObject toJsonObject(Waypoint waypoint)
    {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", waypoint.id().toString());
        entry.addProperty("name", waypoint.name());
        entry.addProperty("dimension", waypoint.dimension());
        entry.addProperty("x", waypoint.x());
        entry.addProperty("y", waypoint.y());
        entry.addProperty("z", waypoint.z());
        entry.addProperty("color", waypoint.color());
        entry.addProperty("icon", waypoint.icon());
        entry.addProperty("group", waypoint.group());
        entry.addProperty("visible", waypoint.visible());
        entry.addProperty("temporary", waypoint.temporary());
        entry.addProperty("death", waypoint.death());
        entry.addProperty("createdAt", waypoint.createdAt());
        entry.addProperty("updatedAt", waypoint.updatedAt());
        for (Map.Entry<String, JsonElement> extension : waypoint.extensions().entrySet())
        {
            entry.add(extension.getKey(), extension.getValue());
        }
        return entry;
    }

    private Waypoint parseWaypoint(JsonObject object, int index, int version) throws IOException
    {
        String idText = requireString(object, "id", "waypoint " + index + " field \"id\"");
        UUID id;
        try
        {
            id = UUID.fromString(idText);
        }
        catch (IllegalArgumentException failure)
        {
            throw corrupt("waypoint " + index + " has an invalid UUID: " + idText, failure);
        }
        boolean visible = version == 1
                ? requireBoolean(object, "visible", "waypoint " + index + " field \"visible\"")
                : optionalBoolean(object, "visible", true,
                        "waypoint " + index + " field \"visible\"");
        try
        {
            Map<String, JsonElement> extensions = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet())
            {
                if (!Waypoint.knownFields().contains(entry.getKey()))
                {
                    extensions.put(entry.getKey(), entry.getValue());
                }
            }
            return new Waypoint(
                    id,
                    requireString(object, "name", "waypoint " + index + " field \"name\""),
                    requireString(object, "dimension",
                            "waypoint " + index + " field \"dimension\""),
                    requireInt(object, "x", "waypoint " + index + " field \"x\""),
                    requireInt(object, "y", "waypoint " + index + " field \"y\""),
                    requireInt(object, "z", "waypoint " + index + " field \"z\""),
                    requireInt(object, "color", "waypoint " + index + " field \"color\""),
                    optionalString(object, "icon", Waypoint.DEFAULT_ICON,
                            "waypoint " + index + " field \"icon\""),
                    optionalString(object, "group", Waypoint.NO_GROUP,
                            "waypoint " + index + " field \"group\""),
                    visible,
                    optionalBoolean(object, "temporary", false,
                            "waypoint " + index + " field \"temporary\""),
                    optionalBoolean(object, "death", false,
                            "waypoint " + index + " field \"death\""),
                    optionalLong(object, "createdAt", 0L,
                            "waypoint " + index + " field \"createdAt\""),
                    optionalLong(object, "updatedAt", 0L,
                            "waypoint " + index + " field \"updatedAt\""),
                    extensions);
        }
        catch (IllegalArgumentException failure)
        {
            throw corrupt("waypoint " + index + " is invalid: " + reason(failure), failure);
        }
    }

    private int requireInt(JsonObject object, String field, String label) throws IOException
    {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber())
        {
            throw corrupt(label + " must be a number", null);
        }
        try
        {
            return element.getAsBigDecimal().intValueExact();
        }
        catch (ArithmeticException | NumberFormatException failure)
        {
            throw corrupt(label + " must be a 32-bit integer", failure);
        }
    }

    private long optionalLong(JsonObject object, String field, long fallback, String label)
            throws IOException
    {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull())
        {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
        {
            throw corrupt(label + " must be a number", null);
        }
        try
        {
            return element.getAsBigDecimal().longValueExact();
        }
        catch (ArithmeticException | NumberFormatException failure)
        {
            throw corrupt(label + " must be a 64-bit integer", failure);
        }
    }

    private String requireString(JsonObject object, String field, String label)
            throws IOException
    {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString())
        {
            throw corrupt(label + " must be a string", null);
        }
        return element.getAsString();
    }

    private String optionalString(JsonObject object, String field, String fallback, String label)
            throws IOException
    {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull())
        {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
        {
            throw corrupt(label + " must be a string", null);
        }
        return element.getAsString();
    }

    private boolean requireBoolean(JsonObject object, String field, String label)
            throws IOException
    {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean())
        {
            throw corrupt(label + " must be a boolean", null);
        }
        return element.getAsBoolean();
    }

    private boolean optionalBoolean(JsonObject object, String field, boolean fallback,
                                    String label) throws IOException
    {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull())
        {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean())
        {
            throw corrupt(label + " must be a boolean", null);
        }
        return element.getAsBoolean();
    }

    private void writeAtomically(String json) throws IOException
    {
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null)
        {
            throw new IOException("waypoint file has no parent directory: " + file);
        }
        Path temporary = null;
        try
        {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, temporaryPrefix(), ".tmp");
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            moveReplacing(temporary, file);
            temporary = null;
        }
        catch (IOException failure)
        {
            throw new IOException("cannot write waypoint file " + file, failure);
        }
        finally
        {
            deleteQuietly(temporary);
        }
    }

    private String temporaryPrefix()
    {
        Path name = file.getFileName();
        String prefix = name == null ? "waypoints" : name.toString();
        return prefix.length() >= 3 ? prefix + "." : "waypoints.";
    }

    private IOException corrupt(String detail, Throwable cause)
    {
        String message = "corrupt waypoint file " + file + ": " + detail;
        return cause == null ? new IOException(message) : new IOException(message, cause);
    }

    private static String stripBom(String text)
    {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static String reason(Throwable failure)
    {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName() : message;
    }

    private static void moveReplacing(Path source, Path target) throws IOException
    {
        try
        {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException unsupported)
        {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path)
    {
        if (path == null)
        {
            return;
        }
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored)
        {
        }
    }
}
